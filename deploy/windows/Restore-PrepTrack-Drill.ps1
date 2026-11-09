#requires -Version 7.0
param(
    [Parameter(Mandatory = $true)][string]$BackupPath,
    [Parameter(Mandatory = $true)][string]$AgeIdentityPath
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$EnvFile = Join-Path $PSScriptRoot '.env.production'
foreach ($Path in @($BackupPath, $AgeIdentityPath, $EnvFile)) {
    if (-not (Test-Path $Path -PathType Leaf)) { throw "Required file was not found: $Path" }
}
if (-not (Get-Command age -ErrorAction SilentlyContinue)) { throw 'Install age before running a restore drill.' }
$RestoreDb = 'preptrack_restore_check_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
$Compose = @('compose','--env-file',$EnvFile,'-f',(Join-Path $Root 'compose.windows.yaml'),'exec','-T','db')
Push-Location $Root
try {
    $CreateArgs = $Compose + @('createdb','-U','preptrack','--template=template0',$RestoreDb)
    & docker @CreateArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary restore database.' }

    $Age = [System.Diagnostics.Process]::new()
    $Age.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $Age.StartInfo.FileName = 'age'
    $Age.StartInfo.UseShellExecute = $false
    $Age.StartInfo.RedirectStandardOutput = $true
    @('--decrypt','--identity',$AgeIdentityPath,$BackupPath) | ForEach-Object { [void]$Age.StartInfo.ArgumentList.Add($_) }

    $Restore = [System.Diagnostics.Process]::new()
    $Restore.StartInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $Restore.StartInfo.FileName = 'docker'
    $Restore.StartInfo.UseShellExecute = $false
    $Restore.StartInfo.RedirectStandardInput = $true
    @($Compose + @('pg_restore','--no-owner','--no-privileges','--exit-on-error','-U','preptrack','--dbname',$RestoreDb)) | ForEach-Object { [void]$Restore.StartInfo.ArgumentList.Add($_) }

    try {
        if (-not $Age.Start()) { throw 'Could not start backup decryption.' }
        if (-not $Restore.Start()) { throw 'Could not start PostgreSQL restore.' }
        $CopyTask = $Age.StandardOutput.BaseStream.CopyToAsync($Restore.StandardInput.BaseStream)
        $Age.WaitForExit()
        $CopyTask.GetAwaiter().GetResult()
        $Restore.StandardInput.Close()
        $Restore.WaitForExit()
        if ($Age.ExitCode -ne 0) { throw 'Backup decryption failed; verify the age identity and recipient.' }
        if ($Restore.ExitCode -ne 0) { throw "PostgreSQL restore failed with exit code $($Restore.ExitCode)." }
    } finally {
        if (-not $Age.HasExited) { $Age.Kill($true) }
        if (-not $Restore.HasExited) { $Restore.Kill($true) }
        $Age.Dispose()
        $Restore.Dispose()
    }

    $CountArgs = $Compose + @('psql','-U','preptrack','-d',$RestoreDb,'-Atc',"SELECT 'accounts='||(SELECT count(*) FROM user_account)||', items='||(SELECT count(*) FROM content_item)||', learning_records='||(SELECT count(*) FROM learning_state);")
    $Counts = & docker @CountArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the restored tables.' }
    Write-Host "Restore drill passed in temporary database $RestoreDb`: $Counts"
} finally {
    $DropArgs = $Compose + @('dropdb','--if-exists','-U','preptrack',$RestoreDb)
    & docker @DropArgs | Out-Null
    Pop-Location
}
