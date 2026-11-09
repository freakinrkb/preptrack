#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][string]$AgeRecipient,
    [Parameter(Mandatory = $true)][string]$BackupDirectory
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$EnvFile = Join-Path $PSScriptRoot '.env.production'
if (-not (Test-Path $EnvFile)) { throw 'PrepTrack secrets were not found; initialize the Windows deployment first.' }
if (-not (Get-Command age -ErrorAction SilentlyContinue)) { throw 'Install the age encryption utility before creating backups.' }
New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
$Stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssZ')
$BackupPath = Join-Path $BackupDirectory "preptrack-$Stamp.dump.age"

$Docker = [System.Diagnostics.Process]::new()
$Docker.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
$Docker.StartInfo.FileName = 'docker'
$Docker.StartInfo.UseShellExecute = $false
$Docker.StartInfo.RedirectStandardOutput = $true
@('compose','--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.windows.yaml'),'exec','-T','db','pg_dump','-U','preptrack','-Fc','preptrack') | ForEach-Object { [void]$Docker.StartInfo.ArgumentList.Add($_) }

$Age = [System.Diagnostics.Process]::new()
$Age.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
$Age.StartInfo.FileName = 'age'
$Age.StartInfo.UseShellExecute = $false
$Age.StartInfo.RedirectStandardInput = $true
@('--encrypt','--recipient',$AgeRecipient,'--output',$BackupPath) | ForEach-Object { [void]$Age.StartInfo.ArgumentList.Add($_) }

try {
    if (-not $Docker.Start()) { throw 'Could not start the database dump.' }
    if (-not $Age.Start()) { throw 'Could not start age encryption.' }
    $CopyTask = $Docker.StandardOutput.BaseStream.CopyToAsync($Age.StandardInput.BaseStream)
    $Docker.WaitForExit()
    $CopyTask.GetAwaiter().GetResult()
    $Age.StandardInput.Close()
    $Age.WaitForExit()
    if ($Docker.ExitCode -ne 0) { throw "Database dump failed with exit code $($Docker.ExitCode)." }
    if ($Age.ExitCode -ne 0 -or -not (Test-Path $BackupPath)) { throw 'Backup encryption failed.' }
    Write-Host "Encrypted backup saved to $BackupPath"
    Write-Host 'Copy it to a separate device or private cloud location; this laptop alone is not a backup destination.'
} catch {
    Remove-Item $BackupPath -Force -ErrorAction SilentlyContinue
    throw
} finally {
    if (-not $Docker.HasExited) { $Docker.Kill($true) }
    if (-not $Age.HasExited) { $Age.Kill($true) }
    $Docker.Dispose()
    $Age.Dispose()
}
