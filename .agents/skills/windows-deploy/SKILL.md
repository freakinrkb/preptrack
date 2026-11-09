---
name: windows-deploy
description: Prepare or review PrepTrack's small private deployment on the owner's Windows laptop. Use for reverse proxy, HTTPS/HTTP2, backups, service management, and remote-access decisions; not for ordinary development.
---

# PrepTrack Windows deployment

Optimize for a reliable private MVP on one 8 GB RAM Windows laptop, not public scale.

- Serve a production React build behind a reverse proxy that terminates TLS and HTTP/2; keep PostgreSQL private.
- Use a private mesh VPN for initial remote access; do not port-forward the application, database, or remote desktop.
- Set conservative Spring heap and PostgreSQL limits, avoid always-running development services, and monitor before adding components.
- Keep secrets outside the repository, patch the host, run the app as managed services, and prevent sleep while serving.
- Make encrypted backups to a separate location and prove restoration before calling deployment complete.

Read `references/operating-checklist.md` for a release or recovery task.
