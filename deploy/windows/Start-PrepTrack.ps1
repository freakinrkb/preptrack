param([switch]$Rebuild)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$EnvFile = Join-Path $PSScriptRoot '.env.production'
if (-not (Test-Path $EnvFile)) { throw 'Run Initialize-PrepTrack.ps1 first to create local secrets.' }
Push-Location $Root
try {
    $DockerArgs = @('compose', '--env-file', $EnvFile, '-f', 'compose.windows.yaml', 'up', '-d')
    if ($Rebuild) { $DockerArgs += '--build' }
    & docker @DockerArgs
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose could not start PrepTrack.' }
    Write-Host 'PrepTrack is available on this PC at http://127.0.0.1:8081.'
    Write-Host 'For private remote HTTPS, follow the Tailscale Serve step in docs/WINDOWS_DEPLOYMENT.md.'
} finally { Pop-Location }
