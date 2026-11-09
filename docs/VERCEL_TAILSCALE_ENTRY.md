# Vercel entry and private Windows application

Vercel serves a small public entry page. The PrepTrack application, Spring API, and PostgreSQL database remain together on the Windows laptop. Tailscale Serve provides the private HTTPS address. The entry page redirects to that address only after it is configured; it does not proxy API calls or store learning data. This preserves same-origin app/API cookies and avoids putting the private API on the public internet.

## Vercel project settings

Import the private GitHub repository `freakinrkb/preptrack` as a Vite project:

- Framework preset: Vite
- Build command: `npm run build`
- Output directory: `dist`
- Root directory: repository root
- Set `VITE_PREPTRACK_ENTRY_ONLY=true` for Production and Preview.
- After Windows/Tailscale is ready, set `VITE_PREPTRACK_PRIVATE_URL=https://<windows-device>.<tailnet>.ts.net` and redeploy.

The entry validates that the target uses HTTPS and a Tailscale `ts.net` hostname before redirecting. Do not use Tailscale Funnel or router port forwarding. People outside the tailnet can see the entry page but cannot access the private application.

## Windows steps still required

The Windows laptop must be online with Docker Desktop and Tailscale running. Follow [WINDOWS_DEPLOYMENT.md](WINDOWS_DEPLOYMENT.md) to initialize the secret file, start the stack, configure `tailscale serve --bg http://127.0.0.1:8081`, create the owner, and remove the bootstrap secret. Then use `tailscale serve status` to obtain the HTTPS URL and add it to Vercel as `VITE_PREPTRACK_PRIVATE_URL`.

The current workspace is on macOS and cannot execute or verify those Windows-device steps. Do not point the production entry at the local development server.

The SSH project named `PrepTrack Mac` is a remote editing connection to the Mac source folder. For the Windows-hosted server, clone the repository onto Windows and run the PowerShell deployment scripts from that local Windows copy.
