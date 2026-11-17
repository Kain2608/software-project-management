[CmdletBinding()]
param(
    [string]$ContainerName = 'tv4_s2_order_it_db',
    [string]$VolumeName = 'tv4_s2_order_it_pgdata',
    [int]$HostPort = 15433
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-DockerText {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker @Arguments 2>&1
        $dockerExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($dockerExitCode -ne 0) {
        throw "docker $($Arguments -join ' ') failed: `n$($output | Out-String)"
    }
    return ($output | Out-String).Trim()
}

# 1. Strict parameter whitelist: only isolated TV4 Sprint 2 Order resources allowed
if ($ContainerName -ne 'tv4_s2_order_it_db' -or $VolumeName -ne 'tv4_s2_order_it_pgdata' -or $HostPort -ne 15433) {
    throw 'Refusing to provision anything except the approved isolated TV4 Sprint 2 Order container tv4_s2_order_it_db, volume tv4_s2_order_it_pgdata, port 127.0.0.1:15433.'
}

# 2. Ephemeral password validation
$appPassword = $env:SASMS_IT_EPHEMERAL_APP_PASSWORD
$adminPassword = $env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD
if ($appPassword -notmatch '^[A-Fa-f0-9]{64}$' -or $adminPassword -notmatch '^[A-Fa-f0-9]{64}$') {
    throw 'Ephemeral integration passwords must be supplied through environment variables as 64 hexadecimal characters.'
}

# 3. Inspect container configuration
$containerJson = Invoke-DockerText -Arguments @('inspect', $ContainerName)
$container = @($containerJson | ConvertFrom-Json)[0]
if ($container.Config.Image -notmatch '(^|/)postgres:16([.-]|$)') {
    throw "Unexpected image '$($container.Config.Image)'; PostgreSQL 16 is required."
}
if ($container.Config.Env -notcontains 'POSTGRES_DB=sasms' -or
    $container.Config.Env -notcontains 'POSTGRES_USER=sasms_owner') {
    throw 'Container must target database sasms with the isolated bootstrap owner sasms_owner.'
}

$portBinding = $container.HostConfig.PortBindings.'5432/tcp'
if ($null -eq $portBinding -or $portBinding.Count -ne 1 -or
    $portBinding[0].HostIp -ne '127.0.0.1' -or $portBinding[0].HostPort -ne "$HostPort") {
    throw "Container 5432/tcp must be bound only to 127.0.0.1:$HostPort."
}

$dataMount = @($container.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
if ($dataMount.Count -ne 1 -or $dataMount[0].Type -ne 'volume' -or $dataMount[0].Name -ne $VolumeName) {
    throw "Container must mount only the expected named volume '$VolumeName' at PostgreSQL data path."
}

$null = Invoke-DockerText -Arguments @('volume', 'inspect', $VolumeName)
if (-not $container.State.Running) {
    $null = Invoke-DockerText -Arguments @('start', $ContainerName)
}

# 4. Wait for healthy/ready status
$deadline = [DateTime]::UtcNow.AddSeconds(90)
do {
    $stateJson = Invoke-DockerText -Arguments @('inspect', $ContainerName)
    $state = @($stateJson | ConvertFrom-Json)[0].State
    $healthStatus = if ($state.PSObject.Properties.Name -contains 'Health') { $state.Health.Status } else { $null }
    if ($state.Running -and ($null -eq $healthStatus -or $healthStatus -eq 'healthy')) {
        break
    }
    if ([DateTime]::UtcNow -ge $deadline) {
        throw "Container '$ContainerName' did not become healthy within 90 seconds."
    }
    Start-Sleep -Seconds 2
} while ($true)

# 5. Provision isolated ephemeral logins with strict attribute & membership guards
$sql = @"
\set ON_ERROR_STOP on
BEGIN;
DO `$provision`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        RAISE EXCEPTION 'Required NOLOGIN role sasms_app is missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_owner') THEN
        RAISE EXCEPTION 'Required role sasms_owner is missing';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN ('tv4_s2_app_login','tv4_s2_admin_login')
          AND (NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'Existing TV4 integration login has unsafe attributes; manual review required';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_auth_members membership
        JOIN pg_roles member ON member.oid = membership.member
        JOIN pg_roles parent ON parent.oid = membership.roleid
        WHERE (member.rolname = 'tv4_s2_app_login' AND parent.rolname <> 'sasms_app')
           OR (member.rolname = 'tv4_s2_admin_login' AND parent.rolname <> 'sasms_owner')
    ) THEN
        RAISE EXCEPTION 'Existing TV4 integration login has unexpected membership; manual review required';
    END IF;
END
`$provision`$;
DROP ROLE IF EXISTS tv4_s2_app_login;
DROP ROLE IF EXISTS tv4_s2_admin_login;
CREATE ROLE tv4_s2_app_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$appPassword';
GRANT sasms_app TO tv4_s2_app_login;
CREATE ROLE tv4_s2_admin_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$adminPassword';
GRANT sasms_owner TO tv4_s2_admin_login;
COMMIT;
"@

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $psqlOutput = $sql | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms 2>&1
    $psqlExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorPreference
}
if ($psqlExitCode -ne 0) {
    throw "Failed to create isolated ephemeral PostgreSQL logins: `n$($psqlOutput | Out-String)"
}

# 6. Verify newly created logins
$verifySql = @"
\set ON_ERROR_STOP on
DO `$verify`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tv4_s2_app_login' AND rolcanlogin AND NOT rolsuper) THEN
        RAISE EXCEPTION 'tv4_s2_app_login was not created properly';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tv4_s2_admin_login' AND rolcanlogin AND NOT rolsuper) THEN
        RAISE EXCEPTION 'tv4_s2_admin_login was not created properly';
    END IF;
END
`$verify`$;
"@

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $verifyOutput = $verifySql | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms 2>&1
    $verifyExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorPreference
}
if ($verifyExitCode -ne 0) {
    throw "Failed to verify ephemeral logins: `n$($verifyOutput | Out-String)"
}

Write-Host "Ephemeral TV4 Sprint 2 logins successfully created and verified in $ContainerName."
