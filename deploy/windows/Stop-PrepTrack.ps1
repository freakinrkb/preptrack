$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$EnvFile = Join-Path $PSScriptRoot '.env.production'
Push-Location $Root
try {
    & docker compose --env-file $EnvFile -f compose.windows.yaml down
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not stop PrepTrack cleanly.' }
    Write-Host 'PrepTrack stopped. Its named database volume was kept.'
} finally { Pop-Location }
