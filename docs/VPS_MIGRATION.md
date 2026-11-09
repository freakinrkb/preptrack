# Move a private PrepTrack instance to a VPS

This runbook moves an existing private PrepTrack installation from the Windows laptop to a Linux VPS. Keep the service private to the owner's trusted devices and tailnet. A VPS changes the host, not the application's access boundary: do not enable public registration, router-style public ports, or Tailscale Funnel.

## Before cutover

1. Choose a Linux VPS with enough memory and disk for the Compose limits in `compose.windows.yaml`, PostgreSQL data, backups, and growth. Keep PostgreSQL and the API on the private Compose network.
2. Install Docker Engine with the Compose plugin, Tailscale, `age`, and PowerShell 7 if using the checked-in backup and restore scripts.
3. Secure SSH with keys, disable password login after confirming key access, and limit SSH at the provider firewall to the administrator's current address or tailnet. Do not expose ports 5432, 8080, or 8081.
4. Copy the project to the VPS over SSH or a private file transfer. Do not copy `.env.production`, the `age` private key, or plaintext database dumps into the source tree.
5. Create a new VPS deployment environment file with fresh database and bootstrap secrets, set `PREPTRACK_LOCAL_PORT=8081`, and restrict its file permissions to the deployment account. Do not reuse the laptop's database password. Keep the same PrepTrack invite secret only if outstanding invite links must remain valid; otherwise revoke pending invites and generate a new secret.

## Create and verify a transfer backup

1. On the laptop, create an encrypted backup using a public `age` recipient whose private identity key is stored separately from both hosts:

   ```powershell
   ./deploy/windows/Backup-PrepTrack.ps1 -AgeRecipient 'age1...' -BackupDirectory 'E:\PrepTrackBackups'
   ```

2. Copy only the resulting `.dump.age` file to the VPS. Copy the matching identity key through a separate protected channel, outside the project directory, and restrict access to the deployment account.
3. On the VPS, start PostgreSQL and run the restore drill against a disposable database before touching the live destination:

   ```powershell
   ./deploy/windows/Start-PrepTrack.ps1 -Rebuild
   ./deploy/windows/Restore-PrepTrack-Drill.ps1 -BackupPath '/secure-transfer/preptrack.dump.age' -AgeIdentityPath '/secure-keys/age-identity.txt'
   ```

4. Confirm the drill reports the expected account, catalogue, and learning-record counts. Resolve any mismatch before planning the cutover. The drill removes its temporary database when it exits.

## Cut over

1. Schedule a short maintenance window. Stop new learning writes by stopping the laptop app, then take a final encrypted backup and transfer that backup to the VPS.
2. Run the restore drill for the final backup and verify the expected counts. Restore into the VPS's live `preptrack` database only after this check passes. Use PostgreSQL's `pg_restore --clean --if-exists --no-owner --no-privileges` against that database while the API is stopped. Keep the laptop database and its backup unchanged until the VPS is accepted.
3. Start the API and web containers. Confirm `/actuator/health/readiness` from inside the Compose network, then use the VPS loopback URL locally to sign in and check DSA questions, attempt history, design drafts, bookmarks, review queue, and export.
4. Sign the VPS into the owner's tailnet and publish the loopback Caddy service with Tailscale Serve. Confirm the HTTPS URL works from an authorized second device. Check that the provider firewall still blocks direct access to the app and database ports.
5. Keep the laptop powered down from writes during the acceptance period. After the owner confirms VPS data and access, update the documented backup destination and schedule encrypted backups on the VPS. Keep the `age` identity key off the VPS and store it in a separate recovery location.

## Rollback

If the VPS fails acceptance before anyone writes new data, stop its app containers and restart the laptop installation. If users have already written data on the VPS, freeze both copies and take encrypted backups of each before choosing a recovery direction; do not blindly restore the older laptop snapshot over newer VPS records.

## Completion record

Record the VPS host label, date of cutover, final backup filename and encryption recipient fingerprint, restore-drill counts, authorized-device check, firewall check, and first scheduled-backup result. Never record passwords, bootstrap secrets, or the `age` identity contents in this record.
