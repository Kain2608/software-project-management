[CmdletBinding()]
param(
    [string]$ContainerName = 'tv4_s1_it_db',
    [string]$VolumeName = 'tv4_s1_it_pgdata',
    [int]$HostPort = 15432
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
        throw "docker $($Arguments -join ' ') failed: $output"
    }
    return ($output | Out-String).Trim()
}

if ($ContainerName -ne 'tv4_s1_it_db' -or $VolumeName -ne 'tv4_s1_it_pgdata' -or $HostPort -ne 15432) {
    throw 'Refusing to provision anything except the approved isolated TV4 Sprint 1 container/volume/port.'
}

$appPassword = $env:SASMS_IT_EPHEMERAL_APP_PASSWORD
$adminPassword = $env:SASMS_IT_EPHEMERAL_ADMIN_PASSWORD
if ($appPassword -notmatch '^[A-Fa-f0-9]{64}$' -or $adminPassword -notmatch '^[A-Fa-f0-9]{64}$') {
    throw 'Ephemeral integration passwords must be supplied through environment variables as 64 hexadecimal characters.'
}

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
    $portBinding[0].HostIp -ne '127.0.0.1' -or $portBinding[0].HostPort -ne '15432') {
    throw 'Container 5432/tcp must be bound only to 127.0.0.1:15432.'
}

$dataMount = @($container.Mounts | Where-Object { $_.Destination -eq '/var/lib/postgresql/data' })
if ($dataMount.Count -ne 1 -or $dataMount[0].Type -ne 'volume' -or $dataMount[0].Name -ne $VolumeName) {
    throw "Container must mount only the expected named volume '$VolumeName' at PostgreSQL data path."
}

$null = Invoke-DockerText -Arguments @('volume', 'inspect', $VolumeName)
if (-not $container.State.Running) {
    $null = Invoke-DockerText -Arguments @('start', $ContainerName)
}

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

$sql = @"
\set ON_ERROR_STOP on
BEGIN;
DO `$provision`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app') THEN
        RAISE EXCEPTION 'Required NOLOGIN role sasms_app is missing';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_roles
        WHERE rolname IN ('tv4_s1_app_login','tv4_s1_admin_login')
          AND (NOT rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'Existing TV4 integration login has unsafe attributes; manual review required';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_auth_members membership
        JOIN pg_roles member ON member.oid = membership.member
        JOIN pg_roles parent ON parent.oid = membership.roleid
        WHERE (member.rolname = 'tv4_s1_app_login' AND parent.rolname <> 'sasms_app')
           OR (member.rolname = 'tv4_s1_admin_login' AND parent.rolname <> 'sasms_owner')
    ) THEN
        RAISE EXCEPTION 'Existing TV4 integration login has unexpected membership; manual review required';
    END IF;
END
`$provision`$;
DROP ROLE IF EXISTS tv4_s1_app_login;
DROP ROLE IF EXISTS tv4_s1_admin_login;
CREATE ROLE tv4_s1_app_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$appPassword';
GRANT sasms_app TO tv4_s1_app_login;
CREATE ROLE tv4_s1_admin_login LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$adminPassword';
GRANT sasms_owner TO tv4_s1_admin_login;
COMMIT;
"@

$sql | & docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U sasms_owner -d sasms
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to create isolated ephemeral PostgreSQL logins.'
}

$verificationSql = @"
WITH required_objects(object_name, object_type) AS (
    VALUES
        ('ai_recommendations', 'BASE TABLE'),
        ('audit_logs', 'BASE TABLE'),
        ('brands', 'BASE TABLE'),
        ('categories', 'BASE TABLE'),
        ('customers', 'BASE TABLE'),
        ('deliveries', 'BASE TABLE'),
        ('delivery_items', 'BASE TABLE'),
        ('devices', 'BASE TABLE'),
        ('goods_receipt_items', 'BASE TABLE'),
        ('goods_receipts', 'BASE TABLE'),
        ('identifier_rules', 'BASE TABLE'),
        ('inventory_import_batches', 'BASE TABLE'),
        ('inventory_movements', 'BASE TABLE'),
        ('invoices', 'BASE TABLE'),
        ('order_devices', 'BASE TABLE'),
        ('outbox_events', 'BASE TABLE'),
        ('payments', 'BASE TABLE'),
        ('permissions', 'BASE TABLE'),
        ('product_images', 'BASE TABLE'),
        ('products', 'BASE TABLE'),
        ('purchase_order_items', 'BASE TABLE'),
        ('purchase_orders', 'BASE TABLE'),
        ('return_items', 'BASE TABLE'),
        ('returns', 'BASE TABLE'),
        ('role_permissions', 'BASE TABLE'),
        ('roles', 'BASE TABLE'),
        ('sales_order_items', 'BASE TABLE'),
        ('sales_orders', 'BASE TABLE'),
        ('stock_availability', 'VIEW'),
        ('stocktake_items', 'BASE TABLE'),
        ('stocktakes', 'BASE TABLE'),
        ('stores', 'BASE TABLE'),
        ('suppliers', 'BASE TABLE'),
        ('technician_assignments', 'BASE TABLE'),
        ('user_roles', 'BASE TABLE'),
        ('users', 'BASE TABLE'),
        ('warehouses', 'BASE TABLE'),
        ('warranties', 'BASE TABLE'),
        ('warranty_claims', 'BASE TABLE'),
        ('warranty_policies', 'BASE TABLE')
),
required_migrations(version, filename) AS (
    VALUES
        (1, 'V001__catalog_and_iam.sql'),
        (2, 'V002__inventory_batches_and_identifiers.sql')
),
required_functions(signature) AS (
    VALUES
        ('sasms.has_permission(uuid,text,uuid)'),
        ('sasms.create_product(uuid,text,uuid,uuid,text,jsonb,numeric,integer)'),
        ('sasms.update_product(uuid,uuid,integer,text,jsonb,numeric,integer,boolean)'),
        ('sasms.receive_device_batch(uuid,uuid,jsonb,uuid)')
),
missing_objects AS (
    SELECT object_name || ' [' || object_type || ']' AS artifact
    FROM required_objects required
    WHERE NOT EXISTS (
        SELECT 1
        FROM information_schema.tables actual
        WHERE actual.table_schema = 'sasms'
          AND actual.table_name = required.object_name
          AND actual.table_type = required.object_type
    )
),
missing_migrations AS (
    SELECT filename AS artifact
    FROM required_migrations required
    WHERE NOT EXISTS (
        SELECT 1
        FROM sasms_meta.schema_migrations actual
        WHERE actual.version = required.version
          AND actual.filename = required.filename
    )
),
missing_functions AS (
    SELECT signature AS artifact
    FROM required_functions
    WHERE to_regprocedure(signature) IS NULL
),
verification AS (
    SELECT CASE
        WHEN EXISTS (SELECT 1 FROM missing_objects)
            THEN 'MISSING_OBJECTS=' || (SELECT string_agg(artifact, ',') FROM missing_objects)
        WHEN EXISTS (SELECT 1 FROM missing_migrations)
            THEN 'MISSING_MIGRATIONS=' || (SELECT string_agg(artifact, ',') FROM missing_migrations)
        WHEN EXISTS (SELECT 1 FROM missing_functions)
            THEN 'MISSING_FUNCTIONS=' || (SELECT string_agg(artifact, ',') FROM missing_functions)
        WHEN NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sasms_app' AND NOT rolcanlogin)
            THEN 'MISSING_OR_UNSAFE_ROLE=sasms_app'
        ELSE 'READY'
    END AS status
)
SELECT current_database() || '|' || current_setting('server_version') || '|' || status
FROM verification;
"@

$verification = Invoke-DockerText -Arguments @(
    'exec', $ContainerName, 'psql', '-At', '-v', 'ON_ERROR_STOP=1',
    '-U', 'sasms_owner', '-d', 'sasms', '-c',
    $verificationSql
)
if ($verification -notmatch '^sasms\|16\.[0-9]+(?:\.[0-9]+)?\|READY$') {
    throw "Unexpected isolated database verification result: $verification"
}

Write-Host "Verified isolated PostgreSQL 16 container '$ContainerName', volume '$VolumeName', loopback port $HostPort, database sasms, required Sprint 1 schema artifacts and migrations V001/V002."

