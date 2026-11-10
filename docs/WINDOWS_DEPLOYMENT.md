# PrepTrack and public LLD videos on a Windows laptop

This runbook keeps the PrepTrack account and learning app private to invited Tailscale users while allowing selected LLD lectures to be viewed publicly. Video files stay on the Windows SSD. PostgreSQL, the API, and both Caddy listeners remain on the host's loopback/container network. Tailscale Serve provides private app access; Tailscale Funnel publishes a separate, restricted, read-only video listener. Do not use router port forwarding.

## What is in the deployment scaffold

- `compose.windows.yaml` starts PostgreSQL, the Spring API, and Caddy. The database and API have no host-published ports. Caddy binds port 8081 for the private app and port 8082 for the restricted public video page, both to Windows loopback only.
- The Caddy listener on port 8082 serves only `/videos`, the static files needed by that page, and `/api/public/videos/**`. All other paths return 404. Account, progress, and upload APIs are not reachable through that listener.
- `Dockerfile.web` builds the React app for production. The private listener serves the full app; the public listener serves the video page and its static assets.
- The API mounts the Windows user's `Videos\\PrepTrack` folder. Video bytes stay on that SSD folder; PostgreSQL stores lecture metadata only.
- `backend/Dockerfile` builds and runs the API as a non-root user with a modest Java heap. Container memory ceilings are 768 MB for PostgreSQL, 1.5 GB for the API, and 128 MB for Caddy.
- `deploy/windows/Initialize-PrepTrack.ps1` creates private random database and bootstrap secrets in `deploy/windows/.env.production`. That file is ignored by Git and Docker build contexts.
- `Start-PrepTrack.ps1`, `Stop-PrepTrack.ps1`, `Backup-PrepTrack.ps1`, and `Restore-PrepTrack-Drill.ps1` manage startup, shutdown, encrypted backups, and a restore drill into a disposable database.

## First setup on the Windows laptop

1. Install Docker Desktop with its WSL 2 backend and PowerShell 7. With 8 GB total RAM, allocate about 4 GB to Docker Desktop and leave the rest for Windows and other apps. Docker Desktop's WSL 2 resource controls are managed through its settings or `.wslconfig`; re-check memory use after starting the app.
2. Install Tailscale on the Windows laptop and sign in to the tailnet. The private app remains restricted to invited tailnet users. A separate Funnel listener will serve only published videos to the public internet.
3. Put a **local copy** of this repository on the Windows laptop. The SSH project named `PrepTrack Mac` edits files on the Mac; it does not put those files on Windows. Since this GitHub repository is private, clone it while signed in to the `freakinrkb` GitHub account (Git Credential Manager can open a browser sign-in): `git clone https://github.com/freakinrkb/preptrack.git`. Open PowerShell 7 in that local `preptrack` folder.
4. Initialize private secrets and the SSD video folder, then start the application from that folder:

   ```powershell
   ./deploy/windows/Initialize-PrepTrack.ps1
   ./deploy/windows/Start-PrepTrack.ps1 -Rebuild
   ```

   Keep `deploy/windows/.env.production` private. It is excluded from Git. The initializer creates `Videos\\PrepTrack` under the Windows user profile and records that folder in the local Compose settings.
5. Configure private HTTPS on the Windows host with `tailscale serve --bg http://127.0.0.1:8081`. Tailscale Serve terminates HTTPS and is reachable within the tailnet; it forwards to the loopback-only Caddy port. Use the HTTPS URL shown by `tailscale serve status` in the browser. `PREPTRACK_COOKIE_SECURE=true` requires that HTTPS URL for sign-in.
6. The one-time owner setup secret is the value of `PREPTRACK_BOOTSTRAP_SECRET` in `deploy/windows/.env.production`. Enter it only on PrepTrack's owner setup page. After the owner account is created, remove that line's value from the environment file, then run `./deploy/windows/Start-PrepTrack.ps1` to recreate the API with owner bootstrap disabled. The database password stays in the file for container startup; keep the file private.
7. Open the app from an allowed tailnet device, create the owner account, and invite only the people who should use the tracker. Do not share the bootstrap secret with invitees.
8. Set the Vercel production variable `VITE_PREPTRACK_PRIVATE_URL` to the HTTPS `ts.net` URL shown by `tailscale serve status`. The [Vercel entry](VERCEL_TAILSCALE_ENTRY.md) will then redirect to the private Windows app.
9. From an Administrator PowerShell, publish only the restricted video listener through Funnel:

   ```powershell
   tailscale funnel --bg --https=8443 http://127.0.0.1:8082
   tailscale funnel status
   ```

   Share the resulting `https://<Windows-tailnet-name>.ts.net:8443/videos` page. The private Serve listener stays on port 443. Anyone with the public video link can watch published lectures; the public listener does not serve the account app or upload APIs. Funnel is a beta service with bandwidth limits, so start with a small audience.

The checked-in Compose defaults never expose PostgreSQL or the API on the Windows host. Caddy binds only to `127.0.0.1` on ports 8081 and 8082. Do not add inbound Windows Firewall rules or router port forwarding; Tailscale Serve and Funnel connect to the loopback listeners.

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

- Tailscale Serve keeps the private app inside the tailnet. Funnel publishes only the separate video listener. Funnel is a beta feature with non-configurable bandwidth limits, so this setup is intended for a modest audience. See [Funnel documentation](https://tailscale.com/docs/features/tailscale-funnel) and [Funnel vs. sharing](https://tailscale.com/docs/reference/funnel-vs-sharing).
- Docker Desktop on Windows with WSL 2 has separate memory and swap controls; this scaffold's container ceilings total about 2.4 GB, leaving room for the OS and Docker overhead. Confirm actual usage on the laptop: [Docker Desktop resource settings](https://docs.docker.com/desktop/settings-and-maintenance/settings/).
- Password recovery is not implemented. Owner bootstrap, sign-in, and invite acceptance have an account-based 15-minute attempt throttle; this is not a general per-IP request limiter. Keep the private app limited to invited tailnet users. The public listener must expose only the public video catalogue and streaming routes.
- A deployment is not complete until the actual Windows laptop survives restart, tailnet access is verified from another authorized device, PostgreSQL remains unreachable from the host network, and a backup is restored successfully.
