# PrepTrack

PrepTrack is a private, multi-user interview preparation workspace for DSA, System Design, LLD, practice tracking, review, and video lessons. The production app and PostgreSQL database run on the owner's Windows laptop; approved Tailscale devices connect through private HTTPS. Video files stay on that laptop's SSD.

## Start here

Read [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) before changing code. It is the current, self-contained handoff for another coding assistant: product status, feature map, identity and invitation flow, architecture, security boundaries, deployment procedure, known limitations, and repository paths. Detailed supporting specifications are in `docs/`.

## Live application

- Main workspace: <https://desktop-otbm7k5.taile19751.ts.net/>
- LLD video library: <https://desktop-otbm7k5.taile19751.ts.net/videos>

These addresses require an approved device on the owner's Tailscale network. The app does not allow public internet signup; the owner invites learners by email. Tailscale Funnel and router port forwarding are off.

## Local frontend

Install Node.js 20+ and run:

```sh
npm install
npm run dev
```

For backend and Windows deployment instructions, use [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) and [`docs/WINDOWS_DEPLOYMENT.md`](docs/WINDOWS_DEPLOYMENT.md). Never commit production secrets, media, database dumps, or user data.
