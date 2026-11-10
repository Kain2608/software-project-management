[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$containerName = 'tv4_s1_it_db'
$appLogin = 'tv4_s1_app_login'
$adminLogin = 'tv4_s1_admin_login'

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
    $cleanupSql | & docker exec -i $containerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to remove exact ephemeral TV4 integration logins.'
    }
}

$env:SASMS_IT_EPHEMERAL_APP_PASSWORD = New-HexSecret
$env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD = New-HexSecret
$env:SASMS_S1_PG_IT_ENABLED = 'true'
$env:SASMS_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/sasms'
$env:SASMS_DB_USERNAME = $appLogin
$env:SASMS_DB_PASSWORD = $env:SASMS_IT_EPHEMERAL_APP_PASSWORD
$env:SASMS_IT_ADMIN_USERNAME = $adminLogin
$env:SASMS_IT_ADMIN_PASSWORD = $env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD

$testExitCode = 1
$provisioned = $false
try {
    & (Join-Path $PSScriptRoot 'provision_s1_db.ps1')
    $provisioned = $true

    Push-Location (Join-Path $repoRoot 'backend')
    try {
        & .\mvnw.cmd '-Dtest=Sprint1PostgreSqlIntegrationTest' test
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
        Remove-Item Env:SASMS_IT_EPHEMERAL_APP_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_S1_PG_IT_ENABLED -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_DB_URL -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_DB_USERNAME -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_DB_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_IT_ADMIN_USERNAME -ErrorAction SilentlyContinue
        Remove-Item Env:SASMS_IT_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    }
}

if ($testExitCode -ne 0) {
    throw "Sprint 1 PostgreSQL integration test failed with exit code $testExitCode."
}

Write-Host 'Sprint 1 PostgreSQL integration test completed successfully; ephemeral logins were removed.'

