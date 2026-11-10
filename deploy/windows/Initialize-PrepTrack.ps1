$ErrorActionPreference = 'Stop'

$EnvFile = Join-Path $PSScriptRoot '.env.production'
$MediaRoot = Join-Path $env:USERPROFILE 'Videos\PrepTrack'
if (Test-Path $EnvFile) {
    throw "Secret file already exists at $EnvFile. It was left unchanged."
}
New-Item -ItemType Directory -Force -Path $MediaRoot, (Join-Path $MediaRoot '.staging') | Out-Null

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
    'PREPTRACK_PUBLIC_LOCAL_PORT=8082'
    "PREPTRACK_MEDIA_ROOT=$($MediaRoot.Replace('\','/'))"
    'PREPTRACK_MEDIA_MAX_FILE_SIZE=8GB'
    'PREPTRACK_MEDIA_MAX_REQUEST_SIZE=9GB'
    'PREPTRACK_MEDIA_MAX_FILE_BYTES=8589934592'
    'PREPTRACK_MEDIA_RESERVE_BYTES=10737418240'
) -join "`r`n"
[System.IO.File]::WriteAllText($EnvFile, $Contents + "`r`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Created private deployment settings at $EnvFile and Windows SSD video storage at $MediaRoot. Keep this file out of source control and share the bootstrap secret only with the owner during setup."
