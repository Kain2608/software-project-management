# Requires approved staging API + real IdP-issued JWTs in process environment only.
# Does not display tokens or write Authorization headers to evidence.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$required = @(
    'SASMS_E2E_BASE_URL', 'SASMS_E2E_STORE_A', 'SASMS_E2E_STORE_B',
    'SASMS_E2E_MANAGER_JWT', 'SASMS_E2E_CASHIER_JWT'
)
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($missing.Count -gt 0) {
    Write-Host ('NOT RUN: Thiếu cấu hình E2E thật: ' + ($missing -join ', '))
    exit 2
}
$base = $env:SASMS_E2E_BASE_URL.TrimEnd('/')
$storeA = $env:SASMS_E2E_STORE_A
$storeB = $env:SASMS_E2E_STORE_B
try {
    [void][guid]::Parse($storeA)
    [void][guid]::Parse($storeB)
} catch {
    throw 'Store UUID E2E không hợp lệ.'
}
if ($storeA -eq $storeB) { throw 'Cần 2 cửa hàng khác nhau cho ca cross-store.' }
$from = [uri]::EscapeDataString('2026-11-01T00:00:00Z')
$to = [uri]::EscapeDataString('2026-12-01T00:00:00Z')

function Invoke-Report([string]$store, [string]$token) {
    $uri = "$base/api/v1/reports/store-summary?store_id=$store&from=$from&to=$to"
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($token)) { $headers['Authorization'] = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Uri $uri -Headers $headers -TimeoutSec 20 -UseBasicParsing -ErrorAction Stop
        return @{ Status = [int]$r.StatusCode; Body = [string]$r.Content }
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw 'Không kết nối được Backend; kiểm tra port, TLS và firewall.' }
        return @{ Status = [int]$response.StatusCode; Body = $null }
    }
}

$cases = @(
    @{ Name = 'Missing JWT'; Store = $storeA; Token = ''; Expected = 401 },
    @{ Name = 'Cashier without reports.read'; Store = $storeA; Token = $env:SASMS_E2E_CASHIER_JWT; Expected = 403 },
    @{ Name = 'Store manager own store'; Store = $storeA; Token = $env:SASMS_E2E_MANAGER_JWT; Expected = 200 },
    @{ Name = 'Store manager different store'; Store = $storeB; Token = $env:SASMS_E2E_MANAGER_JWT; Expected = 403 }
)
$failed = 0
foreach ($c in $cases) {
    $result = Invoke-Report $c.Store $c.Token
    $ok = $result.Status -eq $c.Expected
    if ($ok -and $result.Status -eq 200) {
        try {
            $body = $result.Body | ConvertFrom-Json
            $ok = ([string]$body.store_id -eq [string]$c.Store) -and ($null -ne $body.top_products)
        } catch { $ok = $false }
    }
    if (-not $ok) { $failed++ }
    $label = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host "$label : $($c.Name) (actual $($result.Status), expected $($c.Expected))"
}
Write-Host ('Summary: ' + (4 - $failed) + '/4 PASS;  ' + $failed + ' FAIL')
if ($failed -gt 0) { exit 1 }
