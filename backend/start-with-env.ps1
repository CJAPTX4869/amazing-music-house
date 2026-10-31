# Backend launcher: load .env.local (UTF-8) into process env, then mvn spring-boot:run.
# NOTE: keep this file ASCII-only. PowerShell 5.1 reads BOM-less scripts as ANSI(GBK);
# multi-byte comments can swallow the following newline and corrupt the code lines.
$envFile = Join-Path $PSScriptRoot '.env.local'
$loaded = 0
[System.IO.File]::ReadAllLines($envFile, [System.Text.Encoding]::UTF8) | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $kv = $line -split '=', 2
        if ($kv.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), 'Process')
            $loaded++
        }
    }
}
if ($loaded -lt 4) {
    [System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'env-load-status.txt'), "FAILED loaded=$loaded")
    exit 1
}
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'env-load-status.txt'), "OK loaded=$loaded")
Set-Location $PSScriptRoot
cmd /c "mvn spring-boot:run > run.log 2>&1"
