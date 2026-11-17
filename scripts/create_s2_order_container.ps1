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

# Verify schema completeness: baseline + V001..V005 + runtime roles + Sprint 2 extensions
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
      WHERE n.nspname = 'sasms' AND proname IN ('valid_imei','assert_live_order_lease','reserve_order','complete_sale','find_and_lock_device_for_pos','release_device_lock')),
    (SELECT count(*) FROM sasms_meta.schema_migrations WHERE version IN (1, 2, 3, 4, 5));
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
    if ($parts.Count -lt 6) {
        throw "Unexpected schema probe output format: '$raw'"
    }

    $tableCount = [int]$parts[0]
    $holdColCount = [int]$parts[1]
    $legacyColCount = [int]$parts[2]
    $roleCount = [int]$parts[3]
    $funcCount = [int]$parts[4]
    $migCount = [int]$parts[5]

    if ($tableCount -lt 9 -or $holdColCount -lt 1 -or $legacyColCount -lt 1 -or $roleCount -lt 2 -or $funcCount -lt 6 -or $migCount -lt 5) {
        throw "Schema completeness verification failed ($ContextDescription): expected tables>=9 (got $tableCount), hold_expires_at>=1 (got $holdColCount), is_legacy>=1 (got $legacyColCount), roles>=2 (got $roleCount), functions>=6 (got $funcCount), migrations>=5 (got $migCount)."
    }
}

# 2. Check Docker daemon status
Write-Host "Checking if Docker is running..."
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $null = & docker info 2>&1
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

    # Verify complete schema (Fail-closed if incomplete; refuse to automatically delete)
    Write-Host "Verifying schema completeness in existing container '$ContainerName'..."
    try {
        Assert-CompleteSchema -ContextDescription "Existing container '$ContainerName'"
    } catch {
        throw "Existing container '$ContainerName' has an incomplete or corrupted schema (e.g. failed partial migration): $($_.Exception.Message)`nAutomatic deletion is refused to protect your data. To cleanly confirm and recreate the container, run:`npowershell -ExecutionPolicy Bypass -File scripts/recreate_s2_order_container.ps1"
    }

    Write-Host "Container '$ContainerName' already exists, is healthy, and its schema (baseline + V001-V005 + extensions) is fully verified."
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

# 5. Load baseline schema (Sprint 0)
Write-Host "Loading baseline schema..."
$schemaFile = Join-Path $repoRoot 'database\schema.sql'
if (-not (Test-Path $schemaFile)) {
    throw "Baseline schema file not found at: $schemaFile"
}
$schemaSql = Get-Content $schemaFile -Raw
Invoke-PsqlStdin -Sql $schemaSql -Description "Baseline schema (database/schema.sql)"

# 6. Load baseline runtime roles
Write-Host "Loading baseline runtime roles..."
$rolesFile = Join-Path $repoRoot 'database\runtime-roles.sql'
if (-not (Test-Path $rolesFile)) {
    throw "Runtime roles file not found at: $rolesFile"
}
$rolesSql = Get-Content $rolesFile -Raw
Invoke-PsqlStdin -Sql $rolesSql -Description "Baseline runtime roles (database/runtime-roles.sql)"

# 7. Extract and apply migrations V001..V005 from origin/DB via official runner logic
Write-Host "Extracting and applying migrations V001-V005 from origin/DB via official runner logic..."
$tempMigDir = Join-Path $env:TEMP ("sasms_s2_mig_" + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $tempMigDir -Force
try {
    $migrationFiles = @(
        'V001__catalog_and_iam.sql',
        'V002__inventory_batches_and_identifiers.sql',
        'V003__scoped_device_identifiers.sql',
        'V004__pos_lease_lifecycle.sql',
        'V005__frs_business_roles.sql'
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    foreach ($mFile in $migrationFiles) {
        $pinfoGit = New-Object System.Diagnostics.ProcessStartInfo
        $pinfoGit.FileName = 'git'
        $pinfoGit.Arguments = "-C `"$repoRoot`" show origin/DB:database/migrations/$mFile"
        $pinfoGit.RedirectStandardOutput = $true
        $pinfoGit.RedirectStandardError = $true
        $pinfoGit.UseShellExecute = $false
        $pinfoGit.StandardOutputEncoding = [System.Text.Encoding]::UTF8

        $pGit = [System.Diagnostics.Process]::Start($pinfoGit)
        $gitOut = $pGit.StandardOutput.ReadToEnd()
        $gitErr = $pGit.StandardError.ReadToEnd()
        $pGit.WaitForExit()

        if ($pGit.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($gitOut)) {
            throw "Failed to extract $mFile from origin/DB (exit code $($pGit.ExitCode)): $gitErr"
        }

        $destPath = Join-Path $tempMigDir $mFile
        [System.IO.File]::WriteAllText($destPath, $gitOut, $utf8NoBom)
    }

    # Prepare python generator script file (avoids stdin pipe / shell argument quoting fragility)
    $tempPyRunner = Join-Path $tempMigDir 'build_migration_sql.py'
    $pyScriptCode = @'
import sys
from pathlib import Path

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

repo_scripts = Path(sys.argv[1]).resolve()
mig_dir = Path(sys.argv[2]).resolve()
sys.path.insert(0, str(repo_scripts))

from migrate_db import read_migrations, build_sql

migrations = read_migrations(mig_dir)
if len(migrations) != 5:
    raise ValueError(f"Expected exactly 5 migrations, found {len(migrations)}")

sql = build_sql(migrations, lock_timeout=10, statement_timeout=120)
sys.stdout.write(sql)
'@
    [System.IO.File]::WriteAllText($tempPyRunner, $pyScriptCode, $utf8NoBom)

    $scriptsDir = (Join-Path $repoRoot 'scripts').TrimEnd('\')
    $cleanMigDir = $tempMigDir.TrimEnd('\')

    # Execute python runner via ProcessStartInfo to cleanly separate stdout and stderr,
    # preventing PowerShell 5.1 from aborting on native stderr or truncating tracebacks.
    $pinfo = New-Object System.Diagnostics.ProcessStartInfo
    $pinfo.FileName = 'python'
    $pinfo.Arguments = "`"$tempPyRunner`" `"$scriptsDir`" `"$cleanMigDir`""
    $pinfo.RedirectStandardOutput = $true
    $pinfo.RedirectStandardError = $true
    $pinfo.UseShellExecute = $false
    $pinfo.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $pinfo.StandardErrorEncoding = [System.Text.Encoding]::UTF8

    $process = [System.Diagnostics.Process]::Start($pinfo)
    $migrationSql = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    $pyExitCode = $process.ExitCode
    if ($pyExitCode -ne 0) {
        throw "Python migration builder (scripts/migrate_db.py) failed with exit code ${pyExitCode}:`n${stderr}"
    }

    if ([string]::IsNullOrWhiteSpace($migrationSql)) {
        throw "Generated migration SQL is empty."
    }

    # Verify atomic SQL transaction structure before sending to PostgreSQL
    if (-not ($migrationSql.Contains("BEGIN;") -and $migrationSql.Contains("COMMIT;") -and $migrationSql.Contains("sasms_meta.schema_migrations"))) {
        throw "Generated migration SQL failed integrity validation (missing BEGIN, COMMIT, or schema_migrations ledger)."
    }

    Invoke-PsqlStdin -Sql $migrationSql -Description "Transactional migrations V001-V005 (migrate_db.py runner)"
} finally {
    Remove-Item -Path $tempMigDir -Recurse -Force -ErrorAction SilentlyContinue
}

# 8. Load Sprint 1 runtime roles grants
Write-Host "Loading Sprint 1 runtime roles grants..."
$sprint1RolesFile = Join-Path $repoRoot 'database\sprint1\runtime-roles.sql'
if (-not (Test-Path $sprint1RolesFile)) {
    throw "Sprint 1 runtime roles file not found at: $sprint1RolesFile"
}
# Strip \ir ../runtime-roles.sql because baseline runtime-roles.sql was already applied
$sprint1RolesSql = (Get-Content $sprint1RolesFile -Raw) -replace '(?m)^\s*\\ir\s+.*$', ''
Invoke-PsqlStdin -Sql $sprint1RolesSql -Description "Sprint 1 runtime roles (database/sprint1/runtime-roles.sql)"

# 9. Load Sprint 2 real-time inventory and locking extensions
Write-Host "Loading Sprint 2 real-time inventory and locking extensions..."
$sprint2File = Join-Path $repoRoot 'database\sprint2\realtime_stock_and_locking.sql'
if (-not (Test-Path $sprint2File)) {
    throw "Sprint 2 realtime_stock_and_locking.sql file not found at: $sprint2File"
}
$sprint2Sql = Get-Content $sprint2File -Raw
Invoke-PsqlStdin -Sql $sprint2Sql -Description "Sprint 2 realtime stock and locking (database/sprint2/realtime_stock_and_locking.sql)"

# 10. Post-provisioning schema completeness assertion
Write-Host "Verifying schema completeness after provisioning..."
Assert-CompleteSchema -ContextDescription "Newly created container '$ContainerName'"

Write-Host "Isolated container '$ContainerName' successfully initialized and verified on 127.0.0.1:$HostPort."
