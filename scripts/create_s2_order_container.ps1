[CmdletBinding()]
param(
    [string]$ContainerName = 'tv4_s2_order_it_db',
    [string]$VolumeName = 'tv4_s2_order_it_pgdata',
    [int]$HostPort = 15433
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 1. Strict parameter whitelist: only isolated TV4 Sprint 2 Order resources allowed
if ($ContainerName -ne 'tv4_s2_order_it_db' -or $VolumeName -ne 'tv4_s2_order_it_pgdata' -or $HostPort -ne 15433) {
    throw 'Refusing to create or configure anything except the approved isolated TV4 Sprint 2 Order container tv4_s2_order_it_db, volume tv4_s2_order_it_pgdata, port 127.0.0.1:15433.'
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

# Fail-fast execution helper for psql scripts via stdin
function Invoke-PsqlStdin {
    param(
        [Parameter(Mandatory)][string]$Sql,
        [Parameter(Mandatory)][string]$Description
    )
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = $Sql | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
    if ($code -ne 0) {
        throw "PSQL execution for '$Description' failed with exit code $($code): `n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

# Wait until PostgreSQL server is ready to accept connections
function Wait-ForPostgreSqlReady {
    param(
        [Parameter(Mandatory)][string]$Container
    )
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        $prevErr = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $testOutput = & docker exec -i $Container pg_isready -U sasms_owner -d sasms 2>&1
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $prevErr
        }
        if ($code -eq 0) {
            return
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            throw "PostgreSQL in container '$Container' did not become ready within 60s: `n$($testOutput | Out-String)"
        }
    } while ($true)
}

# Verify schema completeness: baseline schema + runtime roles + V003 + V004 + V005
function Assert-CompleteSchema {
    param(
        [Parameter(Mandatory)][string]$ContextDescription
    )
    $query = @"
SELECT
    (SELECT count(*) FROM information_schema.tables
      WHERE table_schema = 'sasms'
        AND table_name IN ('sales_orders','sales_order_items','devices','order_devices','products','stores','warehouses','device_identifiers','product_identifier_profiles')
    ),
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'sasms' AND table_name = 'sales_orders' AND column_name = 'hold_expires_at'
    ),
    (SELECT count(*) FROM information_schema.columns
      WHERE table_schema = 'sasms' AND table_name = 'roles' AND column_name = 'is_legacy'
    ),
    (SELECT count(*) FROM pg_roles WHERE rolname IN ('sasms_app','sasms_owner')),
    (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
      WHERE n.nspname = 'sasms' AND proname IN ('valid_imei','assert_live_order_lease','reserve_order','complete_sale'));
"@
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $verifyOutput = & docker exec -i $ContainerName psql -U sasms_owner -d sasms -t -A -F ',' -c $query 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
    if ($code -ne 0) {
        throw "Schema verification query failed with exit code $($code): `n$($verifyOutput | Out-String)"
    }

    $raw = ($verifyOutput | Out-String).Trim()
    $parts = @($raw -split ',')
    if ($parts.Count -lt 5) {
        throw "Unexpected schema probe output format: '$raw'"
    }

    $tableCount = [int]$parts[0]
    $holdColCount = [int]$parts[1]
    $legacyColCount = [int]$parts[2]
    $roleCount = [int]$parts[3]
    $funcCount = [int]$parts[4]

    if ($tableCount -lt 9 -or $holdColCount -lt 1 -or $legacyColCount -lt 1 -or $roleCount -lt 2 -or $funcCount -lt 4) {
        throw "Schema completeness verification failed ($ContextDescription): expected tables>=9 (got $tableCount), hold_expires_at>=1 (got $holdColCount), is_legacy>=1 (got $legacyColCount), roles>=2 (got $roleCount), functions>=4 (got $funcCount)."
    }
}

# 2. Check Docker daemon status
Write-Host "Checking if Docker is running..."
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $dockerInfoOutput = & docker info 2>&1
    $dockerInfoCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $prevErr
}
if ($dockerInfoCode -ne 0) {
    throw "Docker daemon is not running. Please launch Docker Desktop from Start Menu or Desktop and ensure it is green (Running)."
}

# 3. Check if container already exists
$existingContainers = Invoke-Native -Command 'docker' -Arguments @('ps', '-a', '--format', '{{.Names}}') -Description 'Listing docker containers'
$containerList = @($existingContainers -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

if ($containerList -contains $ContainerName) {
    Write-Host "Container '$ContainerName' already exists. Validating image, mount, port and schema..."
    $inspectJson = Invoke-Native -Command 'docker' -Arguments @('inspect', $ContainerName) -Description "Inspecting container '$ContainerName'"
    $container = @($inspectJson | ConvertFrom-Json)[0]

    # Verify Image: PostgreSQL 16 required
    if ($container.Config.Image -notmatch '(^|/)postgres:16([.-]|$)') {
        throw "Existing container '$ContainerName' uses image '$($container.Config.Image)'; PostgreSQL 16 is required. Refusing to delete or overwrite."
    }

    # Verify Environment: database sasms with sasms_owner
    if ($container.Config.Env -notcontains 'POSTGRES_DB=sasms' -or $container.Config.Env -notcontains 'POSTGRES_USER=sasms_owner') {
        throw "Existing container '$ContainerName' does not target database sasms with owner sasms_owner. Refusing to delete or overwrite."
    }

    # Verify Port Binding: exactly 127.0.0.1:HostPort -> 5432/tcp
    $portBinding = $container.HostConfig.PortBindings.'5432/tcp'
    if ($null -eq $portBinding -or $portBinding.Count -ne 1 -or
        $portBinding[0].HostIp -ne '127.0.0.1' -or $portBinding[0].HostPort -ne "$HostPort") {
        throw "Existing container '$ContainerName' port binding is invalid: expected 127.0.0.1:$HostPort only. Refusing to delete or overwrite."
    }

    # Verify Volume Mount: VolumeName at /var/lib/postgresql/data
    $dataMount = @($container.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
    if ($dataMount.Count -ne 1 -or $dataMount[0].Type -ne 'volume' -or $dataMount[0].Name -ne $VolumeName) {
        throw "Existing container '$ContainerName' does not mount volume '$VolumeName' at /var/lib/postgresql/data. Refusing to delete or overwrite."
    }

    # If stopped, start it
    if (-not $container.State.Running) {
        Write-Host "Starting existing container '$ContainerName'..."
        $null = Invoke-Native -Command 'docker' -Arguments @('start', $ContainerName) -Description "Starting container '$ContainerName'"
    }

    # Wait for ready
    Wait-ForPostgreSqlReady -Container $ContainerName

    # Verify complete schema
    Write-Host "Verifying schema completeness in existing container '$ContainerName'..."
    Assert-CompleteSchema -ContextDescription "Existing container '$ContainerName'"

    Write-Host "Container '$ContainerName' already exists, is healthy, and its schema (baseline + V003-V005) is fully verified."
    return
}

# 4. If container does not exist: create volume and container
Write-Host "Checking volume '$VolumeName'..."
$existingVolumes = Invoke-Native -Command 'docker' -Arguments @('volume', 'ls', '--format', '{{.Name}}') -Description 'Listing docker volumes'
$volumeList = @($existingVolumes -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
if ($volumeList -notcontains $VolumeName) {
    Write-Host "Creating volume '$VolumeName'..."
    $null = Invoke-Native -Command 'docker' -Arguments @('volume', 'create', $VolumeName) -Description "Creating volume '$VolumeName'"
} else {
    Write-Host "Volume '$VolumeName' exists. Validating..."
    $null = Invoke-Native -Command 'docker' -Arguments @('volume', 'inspect', $VolumeName) -Description "Inspecting volume '$VolumeName'"
}

Write-Host "Creating and starting isolated container '$ContainerName' on 127.0.0.1:$HostPort..."
$ownerPassword = [Guid]::NewGuid().ToString('N')
$null = Invoke-Native -Command 'docker' -Arguments @(
    'run', '-d',
    '--name', $ContainerName,
    '-e', 'POSTGRES_DB=sasms',
    '-e', 'POSTGRES_USER=sasms_owner',
    '-e', "POSTGRES_PASSWORD=$ownerPassword",
    '-p', "127.0.0.1:${HostPort}:5432",
    '-v', "${VolumeName}:/var/lib/postgresql/data",
    'postgres:16'
) -Description "docker run for container '$ContainerName'"

Write-Host "Waiting for container to become healthy and ready..."
Wait-ForPostgreSqlReady -Container $ContainerName

# 5. Load baseline schema
Write-Host "Loading baseline schema..."
$schemaFile = Join-Path $repoRoot 'database\schema.sql'
if (-not (Test-Path $schemaFile)) {
    throw "Baseline schema file not found at: $schemaFile"
}
$schemaSql = Get-Content $schemaFile -Raw
Invoke-PsqlStdin -Sql $schemaSql -Description "Baseline schema (database/schema.sql)"

# 6. Load runtime roles
Write-Host "Loading runtime roles..."
$rolesFile = Join-Path $repoRoot 'database\runtime-roles.sql'
if (-not (Test-Path $rolesFile)) {
    throw "Runtime roles file not found at: $rolesFile"
}
$rolesSql = Get-Content $rolesFile -Raw
Invoke-PsqlStdin -Sql $rolesSql -Description "Runtime roles (database/runtime-roles.sql)"

# 7. Extract and apply V003-V005 from origin/DB (fail-fast on each migration)
Write-Host "Extracting and applying migration V003 from origin/DB..."
$v003 = Invoke-Native -Command 'git' -Arguments @('-C', $repoRoot, 'show', 'origin/DB:database/migrations/V003__scoped_device_identifiers.sql') -Description "git show V003"
if ([string]::IsNullOrWhiteSpace($v003)) {
    throw "Extracted migration V003 is empty or unavailable from origin/DB."
}
Invoke-PsqlStdin -Sql $v003 -Description "V003__scoped_device_identifiers.sql"

Write-Host "Extracting and applying migration V004 from origin/DB..."
$v004 = Invoke-Native -Command 'git' -Arguments @('-C', $repoRoot, 'show', 'origin/DB:database/migrations/V004__pos_lease_lifecycle.sql') -Description "git show V004"
if ([string]::IsNullOrWhiteSpace($v004)) {
    throw "Extracted migration V004 is empty or unavailable from origin/DB."
}
Invoke-PsqlStdin -Sql $v004 -Description "V004__pos_lease_lifecycle.sql"

Write-Host "Extracting and applying migration V005 from origin/DB..."
$v005 = Invoke-Native -Command 'git' -Arguments @('-C', $repoRoot, 'show', 'origin/DB:database/migrations/V005__frs_business_roles.sql') -Description "git show V005"
if ([string]::IsNullOrWhiteSpace($v005)) {
    throw "Extracted migration V005 is empty or unavailable from origin/DB."
}
Invoke-PsqlStdin -Sql $v005 -Description "V005__frs_business_roles.sql"

# 8. Post-provisioning schema completeness assertion
Write-Host "Verifying schema completeness after provisioning..."
Assert-CompleteSchema -ContextDescription "Newly created container '$ContainerName'"

Write-Host "Isolated container '$ContainerName' successfully initialized and verified on 127.0.0.1:$HostPort."
