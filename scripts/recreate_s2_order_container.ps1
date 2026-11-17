[CmdletBinding()]
param(
    [string]$ContainerName = 'tv4_s2_order_it_db',
    [string]$VolumeName = 'tv4_s2_order_it_pgdata',
    [int]$HostPort = 15433,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Guard 1: Strict parameter whitelist
if ($ContainerName -ne 'tv4_s2_order_it_db' -or $VolumeName -ne 'tv4_s2_order_it_pgdata' -or $HostPort -ne 15433) {
    throw 'Refusing to recreate anything except the approved isolated TV4 Sprint 2 Order container tv4_s2_order_it_db, volume tv4_s2_order_it_pgdata, port 127.0.0.1:15433.'
}

$repoRoot = Split-Path -Parent $PSScriptRoot

# Fail-fast execution helper for native commands
function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(Mandatory)][string[]]$Arguments,
        [string]$Description = ''
    )
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Command @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
    if ($code -ne 0) {
        $msg = if ($Description) { $Description } else { "$Command $($Arguments -join ' ')" }
        throw "Command '$msg' failed with exit code $($code): `n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

# 1. Check Docker daemon status
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $null = & docker info 2>&1
    $dockerInfoCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $prevErr
}
if ($dockerInfoCode -ne 0) {
    throw "Docker daemon is not running. Please launch Docker Desktop and ensure it is green (Running)."
}

# 2. Check and validate existing container (if present)
$existingContainers = Invoke-Native -Command 'docker' -Arguments @('ps', '-a', '--format', '{{.Names}}') -Description 'Listing containers'
$containerList = @($existingContainers -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

if ($containerList -contains $ContainerName) {
    $inspectJson = Invoke-Native -Command 'docker' -Arguments @('inspect', $ContainerName) -Description "Inspecting container '$ContainerName'"
    $container = @($inspectJson | ConvertFrom-Json)[0]

    # Verify that existing container matches our exact isolated specification before touching
    if ($container.Config.Image -notmatch '(^|/)postgres:16([.-]|$)') {
        throw "Container '$ContainerName' exists but uses unexpected image '$($container.Config.Image)'. Refusing to delete."
    }
    $portBinding = $container.HostConfig.PortBindings.'5432/tcp'
    if ($null -ne $portBinding -and $portBinding.Count -gt 0 -and $portBinding[0].HostPort -ne "$HostPort") {
        throw "Container '$ContainerName' exists on unexpected port $($portBinding[0].HostPort). Refusing to delete."
    }
}

# 3. Interactive confirmation prompt (protect user data)
Write-Host "=======================================================================" -ForegroundColor Yellow
Write-Host " WARNING: RECREATE ISOLATED TV4 SPRINT 2 ORDER POSTGRESQL CONTAINER    " -ForegroundColor Yellow
Write-Host "=======================================================================" -ForegroundColor Yellow
Write-Host "Target Container : $ContainerName"
Write-Host "Target Volume    : $VolumeName"
Write-Host "Target Host Port : 127.0.0.1:$HostPort"
Write-Host ""
Write-Host "This will stop and permanently remove ONLY container '$ContainerName' and volume '$VolumeName'."
Write-Host "No shared containers, no other volumes, and no migrations will be affected."
Write-Host ""

if (-not $Force) {
    $confirmation = Read-Host "Type 'YES' to confirm and proceed with clean recreation"
    if ($confirmation -cne 'YES') {
        Write-Host "Recreation aborted by user. No resources were modified." -ForegroundColor Green
        return
    }
} else {
    Write-Host "[-Force specified] Proceeding without interactive prompt."
}

# 4. Safely stop and remove container (if present)
if ($containerList -contains $ContainerName) {
    Write-Host "Stopping and removing container '$ContainerName'..."
    $null = Invoke-Native -Command 'docker' -Arguments @('rm', '-f', $ContainerName) -Description "Removing container '$ContainerName'"
}

# 5. Safely remove volume (if present)
$existingVolumes = Invoke-Native -Command 'docker' -Arguments @('volume', 'ls', '--format', '{{.Name}}') -Description 'Listing volumes'
$volumeList = @($existingVolumes -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
if ($volumeList -contains $VolumeName) {
    Write-Host "Removing volume '$VolumeName'..."
    $null = Invoke-Native -Command 'docker' -Arguments @('volume', 'rm', $VolumeName) -Description "Removing volume '$VolumeName'"
}

# 6. Run create_s2_order_container.ps1 to build fresh
Write-Host "Invoking create_s2_order_container.ps1 to build fresh isolated database..."
& (Join-Path $PSScriptRoot 'create_s2_order_container.ps1') -ContainerName $ContainerName -VolumeName $VolumeName -HostPort $HostPort
if ($LASTEXITCODE -ne 0) {
    throw "create_s2_order_container.ps1 failed during recreation with exit code $LASTEXITCODE."
}

Write-Host "Clean recreation of '$ContainerName' completed successfully." -ForegroundColor Green
