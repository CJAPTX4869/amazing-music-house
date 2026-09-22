# Twemoji emoji downloader (V3.1)
# Downloads 72x72 PNG set for the built-in emoji pack into storage/emoji/
# Source: https://github.com/jdecked/twemoji (CC-BY 4.0), pinned to v15.1.0
# Names are mapped to codepoints in db/emoji-init.sql (kept separate so this
# script stays pure ASCII and immune to console encoding issues).

$ErrorActionPreference = 'Continue'
$dest = 'D:\Desktop\MusicProject\music-platform\music-server\storage\emoji'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$codes = @(
  '1f602','1f600','1f601','1f606','1f923',
  '1f609','1f60d','1f618','1f60c','1f60e',
  '1f612','1f624','1f622','1f62d','1f631',
  '1f620','1f621','1f614','1f62a','1f637',
  '1f914','1f92b','1f922','1f921','1f4a9',
  '1f47b','1f480','1f47d','1f916','1f383',
  '1f436','1f431','1f42d','1f430','1f98a',
  '1f43b','1f43c','1f437','1f438','1f435',
  '1f414','1f427','1f41f','1f98b','1f422',
  '1f40d','1f419','1f433','1f42c','1f44d',
  '1f44e','1f44c','1f44f','1f44b','1f64f',
  '1f4aa','1f449','270c','270a','2764',
  '1f494','2728','1f525','2b50','1f308',
  '1f319','2600','2601','2614','1f382',
  '2615','1f37a','1f349'
)

$ok = 0; $fail = 0
foreach ($code in $codes) {
  $out = Join-Path $dest ("$code.png")
  if (Test-Path $out) { $ok++; continue }
  $url = "https://cdn.jsdelivr.net/gh/jdecked/twemoji@15.1.0/assets/72x72/$code.png"
  try {
    Invoke-WebRequest -Uri $url -OutFile $out -TimeoutSec 20 -UseBasicParsing
    $ok++
  } catch {
    $fail++
    Write-Output "FAIL $code : $($_.Exception.Message)"
  }
}
Write-Output "done ok=$ok fail=$fail total=$($codes.Count)"
