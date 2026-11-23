[CmdletBinding()]
param(
    [string]$ContainerName = 'sasms_staging_e2e_db',
    [string]$VolumeName = 'sasms_staging_e2e_pgdata',
    [int]$HostPort = 15435,
    [string]$DatabaseName = 'sasms',
    [string]$AppUser = 'tv4_staging_app_user',
    [string]$AppPassword = 'SasmsApp_Staging_2026!'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot

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

function Invoke-PsqlStdin {
    param(
        [Parameter(Mandatory)][string]$Sql,
        [Parameter(Mandatory)][string]$Description,
        [string]$User = 'sasms_owner'
    )
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = $Sql | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U $User -d $DatabaseName 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
    if ($code -ne 0) {
        throw "PSQL execution for '$Description' failed with exit code $($code): `n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

function Invoke-PsqlFile {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string]$Description,
        [string]$User = 'sasms_owner'
    )
    if (-not (Test-Path $FilePath)) {
        throw "File not found: $FilePath"
    }
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = Get-Content -Raw $FilePath | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U $User -d $DatabaseName 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prevErr
    }
    if ($code -ne 0) {
        throw "PSQL file execution for '$Description' failed with exit code $($code): `n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

function Wait-ForPostgreSqlReady {
    param([Parameter(Mandatory)][string]$Container)
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        Start-Sleep -Seconds 2
        $prevErr = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $testOutput = & docker exec -i $Container pg_isready -U sasms_owner -d $DatabaseName 2>&1
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

Write-Host "=== 1. KIỂM TRA DOCKER DAEMON ==="
$prevErr = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $null = & docker info 2>&1
    $dockerInfoCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $prevErr
}
if ($dockerInfoCode -ne 0) {
    throw "Docker daemon is not running. Vui lòng mở Docker Desktop."
}

Write-Host "=== 2. KHỞI TẠO CONTAINER POSTGRESQL 16 CHO E2E STAGING (CỔNG $HostPort) ==="
$existingContainers = Invoke-Native -Command 'docker' -Arguments @('ps', '-a', '--format', '{{.Names}}') -Description 'Listing containers'
$containerList = @($existingContainers -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

if ($containerList -contains $ContainerName) {
    Write-Host "Container '$ContainerName' đã tồn tại. Đang kiểm tra trạng thái..."
    $running = Invoke-Native -Command 'docker' -Arguments @('inspect', '-f', '{{.State.Running}}', $ContainerName) -Description 'Check running'
    if ($running -ne 'true') {
        Write-Host "Đang khởi động container '$ContainerName'..."
        $null = Invoke-Native -Command 'docker' -Arguments @('start', $ContainerName) -Description 'Start container'
    }
} else {
    Write-Host "Đang tạo mới container '$ContainerName' trên cổng 127.0.0.1:$HostPort..."
    $null = Invoke-Native -Command 'docker' -Arguments @(
        'run', '-d',
        '--name', $ContainerName,
        '-p', "127.0.0.1:${HostPort}:5432",
        '-v', "${VolumeName}:/var/lib/postgresql/data",
        '-e', "POSTGRES_DB=$DatabaseName",
        '-e', 'POSTGRES_USER=sasms_owner',
        '-e', 'POSTGRES_PASSWORD=sasms_staging_owner_pass_2026',
        'postgres:16'
    ) -Description "Create container $ContainerName"
}

Wait-ForPostgreSqlReady -Container $ContainerName
Write-Host "PostgreSQL 16 sẵn sàng trên cổng $HostPort."

Write-Host "=== 3. NẠP SCHEMA BASELINE, SEED VÀ RUNTIME ROLES ==="
$null = Invoke-PsqlFile -FilePath (Join-Path $repoRoot 'database\schema.sql') -Description 'Baseline Schema'
$null = Invoke-PsqlFile -FilePath (Join-Path $repoRoot 'database\seed.sql') -Description 'Seed Data'
$null = Invoke-PsqlFile -FilePath (Join-Path $repoRoot 'database\runtime-roles.sql') -Description 'Runtime Roles'

Write-Host "=== 4. ÁP DỤNG CÁC BẢN MIGRATION TỪ V001 ĐẾN V008 ==="
$migrationFiles = Get-ChildItem -Path (Join-Path $repoRoot 'database\migrations') -Filter "V*.sql" | Sort-Object Name
foreach ($mig in $migrationFiles) {
    Write-Host "Applying $($mig.Name)..."
    $null = Invoke-PsqlFile -FilePath $mig.FullName -Description "Migration $($mig.Name)"
}

# Áp dụng extension Sprint 2 & 3 nếu có
$s2Ext = Join-Path $repoRoot 'database\sprint2\realtime_stock_and_locking.sql'
if (Test-Path $s2Ext) {
    Write-Host "Applying Sprint 2 realtime stock & locking extension..."
    $null = Invoke-PsqlFile -FilePath $s2Ext -Description 'Sprint 2 Realtime Stock'
}

Write-Host "=== 5. CẤP RUNTIME LOGIN USER ($AppUser) THUỘC NHÓM sasms_app ==="
$provisionSql = Join-Path $repoRoot 'scripts\provision_staging_e2e.sql'
$null = Invoke-PsqlFile -FilePath $provisionSql -Description 'Provision Staging E2E Login'

Write-Host "=== 6. XÁC NHẬN SCHEMA, MIGRATIONS VÀ QUYỀN HẠN RUNTIME ==="
$verifyQuery = @"
SELECT 
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'sasms') AS table_count,
    (SELECT count(*) FROM sasms_meta.schema_migrations) AS migration_count,
    (SELECT rolcanlogin FROM pg_roles WHERE rolname = '$AppUser') AS can_login,
    (SELECT rolsuper FROM pg_roles WHERE rolname = '$AppUser') AS is_superuser,
    (SELECT pg_has_role('$AppUser', 'sasms_app', 'member')) AS is_app_member;
"@

$verifyResult = Invoke-PsqlStdin -Sql $verifyQuery -Description 'Verify Schema and Roles'
Write-Host "Kết quả kiểm tra:"
Write-Host $verifyResult

Write-Host "`n================================================================="
Write-Host "🎉 MÔI TRƯỜNG E2E STAGING ĐÃ SẴN SÀNG CHO SPRING BOOT KẾT NỐI!"
Write-Host "================================================================="
Write-Host "Host:      127.0.0.1 (localhost)"
Write-Host "Port:      $HostPort"
Write-Host "Database:  $DatabaseName"
Write-Host "Username:  $AppUser  (Quyền: sasms_app, NON-SUPERUSER)"
Write-Host "Password:  $AppPassword"
Write-Host "JDBC URL:  jdbc:postgresql://127.0.0.1:$HostPort/$DatabaseName"
Write-Host "-----------------------------------------------------------------"
Write-Host "Biến môi trường để chạy Backend Spring Boot:"
Write-Host "`$env:SASMS_DB_URL='jdbc:postgresql://127.0.0.1:$HostPort/$DatabaseName'"
Write-Host "`$env:SASMS_DB_USERNAME='$AppUser'"
Write-Host "`$env:SASMS_DB_PASSWORD='$AppPassword'"
Write-Host "Lệnh chạy Backend:"
Write-Host "./mvnw spring-boot:run -Dspring-boot.run.profiles=postgresql"
Write-Host "================================================================="
