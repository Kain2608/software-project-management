[CmdletBinding()]
param(
    [string]$ContainerName = 'tv4_s2_order_it_db',
    [string]$VolumeName = 'tv4_s2_order_it_pgdata',
    [int]$HostPort = 15433
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "Checking if Docker is running..."
& docker info > $null 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not running. Please launch Docker Desktop from Start Menu or Desktop and ensure it is green (Running)."
}

# 1. Create Volume if absent
$volumes = & docker volume ls --format "{{.Name}}"
if ($volumes -notcontains $VolumeName) {
    Write-Host "Creating volume $VolumeName..."
    & docker volume create $VolumeName
}

# 2. Check if container exists
$containers = & docker ps -a --format "{{.Names}}"
if ($containers -notcontains $ContainerName) {
    Write-Host "Creating and starting isolated container $ContainerName on 127.0.0.1:$HostPort..."
    $ownerPassword = [Guid]::NewGuid().ToString('N')
    & docker run -d `
        --name $ContainerName `
        -e POSTGRES_DB=sasms `
        -e POSTGRES_USER=sasms_owner `
        -e POSTGRES_PASSWORD=$ownerPassword `
        -p "127.0.0.1:${HostPort}:5432" `
        -v "${VolumeName}:/var/lib/postgresql/data" `
        postgres:16

    Write-Host "Waiting for container to become healthy..."
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        $testOutput = & docker exec -i $ContainerName pg_isready -U sasms_owner -d sasms 2>&1
        if ($LASTEXITCODE -eq 0) {
            break
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            throw "PostgreSQL container did not become ready within 60s: $testOutput"
        }
    } while ($true)

    Write-Host "Loading baseline schema..."
    Get-Content (Join-Path $repoRoot 'database\schema.sql') -Raw | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms

    Write-Host "Loading runtime roles..."
    Get-Content (Join-Path $repoRoot 'database\runtime-roles.sql') -Raw | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms

    Write-Host "Extracting and applying migrations V003-V005 from origin/DB..."
    $v003 = & git -C $repoRoot show origin/DB:database/migrations/V003__scoped_device_identifiers.sql
    $v003 | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms

    $v004 = & git -C $repoRoot show origin/DB:database/migrations/V004__pos_lease_lifecycle.sql
    $v004 | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms

    $v005 = & git -C $repoRoot show origin/DB:database/migrations/V005__frs_business_roles.sql
    $v005 | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms

    Write-Host "Isolated container $ContainerName is ready on 127.0.0.1:$HostPort."
} else {
    Write-Host "Container $ContainerName already exists. Ensuring it is running..."
    & docker start $ContainerName
}
