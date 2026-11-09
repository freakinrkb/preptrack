# PrepTrack on a private Windows laptop

This runbook prepares the 8 GB Windows laptop to host PrepTrack for a small, invited group. It keeps PostgreSQL off the host network, builds the frontend for production, and limits the only published container port to Windows loopback. Tailscale Serve then provides HTTPS to authorized tailnet devices. Do not use router port forwarding or Tailscale Funnel.

## What is in the deployment scaffold

- `compose.windows.yaml` starts PostgreSQL, the Spring API, and Caddy. The database and API have no host-published ports.
- `Dockerfile.web` builds the React app and serves it from Caddy. Caddy forwards `/api/*` to the API and serves the single-page app for other paths.
- `backend/Dockerfile` builds and runs the API as a non-root user with a modest Java heap. Container memory ceilings are 768 MB for PostgreSQL, 1.5 GB for the API, and 128 MB for Caddy.
- `deploy/windows/Initialize-PrepTrack.ps1` creates private random database and bootstrap secrets in `deploy/windows/.env.production`. That file is ignored by Git and Docker build contexts.
- `Start-PrepTrack.ps1`, `Stop-PrepTrack.ps1`, `Backup-PrepTrack.ps1`, and `Restore-PrepTrack-Drill.ps1` manage startup, shutdown, encrypted backups, and a restore drill into a disposable database.

## First setup on the Windows laptop

1. Install Docker Desktop with its WSL 2 backend and PowerShell 7. With 8 GB total RAM, allocate about 4 GB to Docker Desktop and leave the rest for Windows and other apps. Docker Desktop's WSL 2 resource controls are managed through its settings or `.wslconfig`; re-check memory use after starting the app.
2. Install Tailscale on the Windows laptop and each device that should use PrepTrack. Sign them into the same tailnet, or share the laptop only with specific trusted users. Do not enable Funnel.
3. Copy this project folder to the laptop. Open PowerShell 7 in the project and run `./deploy/windows/Initialize-PrepTrack.ps1`, then `./deploy/windows/Start-PrepTrack.ps1 -Rebuild`.
4. Configure private HTTPS on the Windows host with `tailscale serve --bg http://127.0.0.1:8081`. Tailscale Serve terminates HTTPS and is reachable within the tailnet; it forwards to the loopback-only Caddy port. Use the HTTPS URL shown by `tailscale serve status` in the browser. `PREPTRACK_COOKIE_SECURE=true` requires that HTTPS URL for sign-in.
5. The one-time owner setup secret is the value of `PREPTRACK_BOOTSTRAP_SECRET` in `deploy/windows/.env.production`. Enter it only on PrepTrack's owner setup page. After the owner account is created, remove that line's value from the environment file, then run `./deploy/windows/Start-PrepTrack.ps1` to recreate the API with owner bootstrap disabled. The database password stays in the file for container startup; keep the file private.
6. Open the app from an allowed tailnet device, create the owner account, and invite only the people who should use the tracker. Do not share the bootstrap secret with invitees.

The checked-in Compose defaults never expose PostgreSQL or the API on the Windows host. The host's Windows Firewall should not receive an inbound allow rule for ports 5432, 8080, or 8081; Tailscale Serve handles the private HTTPS entry point.

## Start automatically and monitor

The containers use Docker's `unless-stopped` restart policy. Enable Docker Desktop to start with Windows, then create a Task Scheduler task that runs `Start-PrepTrack.ps1` at the owner's sign-in. Test a full Windows restart before relying on it. Keep Windows, Docker Desktop, and Tailscale patched. Check free SSD space, Docker Desktop memory, container health, and the last successful backup monthly.

Run `./deploy/windows/Stop-PrepTrack.ps1` to stop the services; it keeps the named database volume. Do not use `docker compose down -v` on the live installation, because `-v` removes the database volume. After validating a daily backup manually, schedule `Backup-PrepTrack.ps1` in Task Scheduler with the age public recipient and an external backup destination. Check the task's last result after each run.

## Encrypted backups and restore drill

Install the `age` utility and keep its private identity key somewhere separate from the laptop. Pass only the matching public recipient string to the backup script. For example:

```powershell
./deploy/windows/Backup-PrepTrack.ps1 -AgeRecipient 'age1...' -BackupDirectory 'E:\PrepTrackBackups'
```

The script streams the database dump directly into encryption rather than writing a plaintext dump file. Store the encrypted output on an external disk or private cloud location, not only on the server SSD. Keep a daily/weekly retention policy at the backup destination.

Before calling the installation reliable, run `./deploy/windows/Restore-PrepTrack-Drill.ps1 -BackupPath '<backup>.dump.age' -AgeIdentityPath '<private-age-identity-file>'`. It decrypts and restores into a temporary database, reports account/catalogue/learning-record counts, then removes that temporary database. It never restores over the live `preptrack` database. Record the date and result. The encryption key must not live only beside the encrypted backups.

## Access and deployment boundaries

- Tailscale Serve shares the service only inside the tailnet; Tailscale Funnel is the public-internet sharing mode and is intentionally excluded. Tailscale documents Serve as an HTTPS reverse proxy to a local service and notes that `http://127.0.0.1` is a supported proxy target: [Serve CLI reference](https://tailscale.com/docs/reference/tailscale-cli/serve), [Funnel vs. sharing](https://tailscale.com/docs/reference/funnel-vs-sharing).
- Docker Desktop on Windows with WSL 2 has separate memory and swap controls; this scaffold's container ceilings total about 2.4 GB, leaving room for the OS and Docker overhead. Confirm actual usage on the laptop: [Docker Desktop resource settings](https://docs.docker.com/desktop/settings-and-maintenance/settings/).
- The current application does not yet provide password reset or request rate limiting. Keep access limited to the owner and trusted invited users on the private tailnet; do not make the service public.
- A deployment is not complete until the actual Windows laptop survives restart, tailnet access is verified from another authorized device, PostgreSQL remains unreachable from the host network, and a backup is restored successfully.
