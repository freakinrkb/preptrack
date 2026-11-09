$ErrorActionPreference = 'Stop'

$EnvFile = Join-Path $PSScriptRoot '.env.production'
if (Test-Path $EnvFile) {
    throw "Secret file already exists at $EnvFile. It was left unchanged."
}

function New-Secret([int]$Length = 48) {
    $Bytes = New-Object byte[] $Length
    $Generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $Generator.GetBytes($Bytes) } finally { $Generator.Dispose() }
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$Contents = @(
    "PREPTRACK_DB_PASSWORD=$(New-Secret 48)"
    "PREPTRACK_BOOTSTRAP_SECRET=$(New-Secret 48)"
    'PREPTRACK_LOCAL_PORT=8081'
) -join "`r`n"
[System.IO.File]::WriteAllText($EnvFile, $Contents + "`r`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Created private deployment secrets at $EnvFile. Keep this file out of source control and share the bootstrap secret only with the owner during setup."
