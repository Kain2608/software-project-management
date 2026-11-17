[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$containerName = 'tv4_s2_order_it_db'
$appLogin = 'tv4_s2_app_login'
$adminLogin = 'tv4_s2_admin_login'

function New-HexSecret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Remove-EphemeralLogins {
    $cleanupSql = @"
\set ON_ERROR_STOP on
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
 WHERE usename IN ('$appLogin','$adminLogin') AND pid <> pg_backend_pid();
DROP ROLE IF EXISTS $appLogin;
DROP ROLE IF EXISTS $adminLogin;
"@
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $cleanupOutput = $cleanupSql | & docker exec -i $containerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms 2>&1
        $cleanupExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($cleanupExitCode -ne 0) {
        throw "Failed to remove exact ephemeral TV4 Sprint 2 integration logins: `n$($cleanupOutput | Out-String)"
    }
}

# 1. Track original environment variables to guarantee clean restoration in finally
$trackedVars = @(
    'SASMS_IT_EPHEMERAL_APP_PASSWORD',
    'SASMS_IT_EPHEMERAL_ADMIN_PASSWORD',
    'SASMS_S2_PG_IT_ENABLED',
    'SASMS_DB_URL',
    'SASMS_DB_USERNAME',
    'SASMS_DB_PASSWORD',
    'SASMS_IT_ADMIN_USERNAME',
    'SASMS_IT_ADMIN_PASSWORD'
)
$originalEnv = @{}
foreach ($var in $trackedVars) {
    $originalEnv[$var] = [System.Environment]::GetEnvironmentVariable($var, [System.EnvironmentVariableTarget]::Process)
}

$appPassword = New-HexSecret
$adminPassword = New-HexSecret

$env:SASMS_IT_EPHEMERAL_APP_PASSWORD = $appPassword
$env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD = $adminPassword
$env:SASMS_S2_PG_IT_ENABLED = 'true'
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:15433/sasms'
$env:SASMS_DB_USERNAME = $appLogin
$env:SASMS_DB_PASSWORD = $appPassword
$env:SASMS_IT_ADMIN_USERNAME = $adminLogin
$env:SASMS_IT_ADMIN_PASSWORD = $adminPassword

$surefireXmlPath = Join-Path $repoRoot 'backend\target\surefire-reports\TEST-com.smartelectronicsupermarket.Sprint2OrderPostgreSqlIntegrationTest.xml'
if (Test-Path $surefireXmlPath) {
    Remove-Item $surefireXmlPath -Force
}

$testExitCode = 1
$provisioned = $false
try {
    & (Join-Path $PSScriptRoot 'provision_s2_order_db.ps1')
    $provisioned = $true

    Push-Location (Join-Path $repoRoot 'backend')
    try {
        & .\mvnw.cmd '-Duser.timezone=UTC' '-Dtest=Sprint2OrderPostgreSqlIntegrationTest' test
        $testExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    try {
        if ($provisioned) {
            Remove-EphemeralLogins
        }
    } finally {
        foreach ($var in $trackedVars) {
            $oldVal = $originalEnv[$var]
            [System.Environment]::SetEnvironmentVariable($var, $oldVal, [System.EnvironmentVariableTarget]::Process)
        }
    }
}

# 2. Assert Maven exit code
if ($testExitCode -ne 0) {
    throw "Sprint 2 Order PostgreSQL integration test failed with Maven exit code $testExitCode."
}

# 3. Assert Surefire XML results: must be exactly 7 tests run, 0 failures, 0 errors, 0 skipped
if (-not (Test-Path $surefireXmlPath)) {
    throw "Surefire XML report was not generated at expected location: $surefireXmlPath"
}

[xml]$surefireXml = Get-Content $surefireXmlPath
$tests = [int]$surefireXml.testsuite.tests
$failures = [int]$surefireXml.testsuite.failures
$errors = [int]$surefireXml.testsuite.errors
$skipped = [int]$surefireXml.testsuite.skipped

if ($tests -ne 7 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "Surefire verification failed: expected exactly 7 tests run, 0 failures, 0 errors, 0 skipped; but got tests=$tests, failures=$failures, errors=$errors, skipped=$skipped."
}

Write-Host "Sprint 2 Order PostgreSQL integration test completed successfully: $tests tests passed (0 failures, 0 errors, 0 skipped); ephemeral logins were removed and all environment variables were restored."
