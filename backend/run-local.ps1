# 读取 .env.local 后启动后端；凭据只存在本机，不进仓库
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env.local'
if (-not (Test-Path $envFile)) {
    Write-Host ' 缺少 .env.local，请先执行: copy .env.example .env.local 然后填入 MYSQL_USERNAME / MYSQL_PASSWORD' -ForegroundColor Yellow
    exit 1
}
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $i = $line.IndexOf('=')
    if ($i -lt 1) { return }
    $k = $line.Substring(0, $i).Trim()
    $v = $line.Substring($i + 1).Trim()
    [Environment]::SetEnvironmentVariable($k, $v, 'Process')
}
if (-not $env:MYSQL_USERNAME) { throw 'MYSQL_USERNAME 为空，检查 .env.local' }
Write-Host (" 启动后端  user={0}  host={1}" -f $env:MYSQL_USERNAME, ($env:MYSQL_URL -replace '\?.*$','')) -ForegroundColor Cyan
mvn spring-boot:run