# PrepTrack — project context for coding assistants

**Last updated:** 2026-11-10

**Purpose:** Give a new coding assistant the current product, architecture, access, deployment, and operating context in one place. Read this file before changing the repository. It describes the deployed system, not just the original plan.

## Product at a glance

PrepTrack is a private, multi-user interview preparation workspace. It combines company-linked data structures and algorithms (DSA), system design study, low-level design (LLD) case studies, an LLD video library, spaced review, bookmarks, timed attempts, and personal progress insights. It is a study tracker, not an online judge.

Every learner signs into their own account. Learning progress is scoped to that user. The workspace owner has administrator access for catalogue synchronization, catalogue corrections, video management, and learner invitations.

## Current deployment

- **Application:** <https://desktop-otbm7k5.taile19751.ts.net/>
- **LLD video library:** <https://desktop-otbm7k5.taile19751.ts.net/videos>
- **Access:** private to devices accepted into the owner's Tailscale network. Tailscale Serve provides HTTPS. Tailscale Funnel and router port forwarding are off. A link by itself does not grant access to an off-network visitor.
- **Host:** the owner's Windows 11 laptop. Docker Desktop runs the web server, Spring API, and PostgreSQL. Lecture files stay on the laptop SSD under `Videos\PrepTrack`; PostgreSQL holds metadata and learning data.
- **Video catalogue:** 53 published lessons in 11 chapters. There is no expected lesson 11.3; that numbering gap is intentional.
- **Source repository:** the `main` branch of the public `freakinrkb/preptrack` GitHub repository. Keep generated builds, production secrets, learner data, and media out of Git.
- **Vercel:** an optional entry page only. It does not host the database, application API, or media, and it cannot bypass Tailscale access controls.

Before claiming the service is live, confirm the Windows host is online and check the app page, `/api/public/videos`, and one ranged video request. The Windows laptop must remain awake, connected to Tailscale, and running Docker Desktop for the service to be available.

## Signup, roles, and sharing

There is no open public signup. Account creation is invite-only after the workspace owner account is bootstrapped.

1. The first account is an `ADMIN`. The one-time owner setup secret is `PREPTRACK_BOOTSTRAP_SECRET` in the Windows-only `deploy/windows/.env.production` file. It is not the invitation token. The first-owner endpoint only works while the account table is empty; the owner account already exists. The optional setup screen is only reachable with `?setup=owner`; ordinary sign-in never asks for this secret. Never share or re-enable the bootstrap secret for learners.
2. The administrator opens **Account → Invite a learner**, enters the learner's email, then privately shares the generated invitation link. The API stores a hash of its random, one-use token. It expires after seven days. The learner chooses their own password when accepting the invite.
3. The invitee must first be approved as a device/user on the owner's Tailscale network, then open the invite link from that network. The invite link is restricted to the invited email. It is not a public registration URL.
4. Learner accounts use the `USER` role. The admin-only catalogue correction, source sync, and video upload/publish controls are not shown to learners. Everyone has their own learning history.

The invite is not emailed automatically. PrepTrack generates the link for the owner to send privately. Do not paste invite links into public channels. Password reset, account deletion, invite revocation, and an administrator user directory are not implemented yet.

## Main user flows

- **Owner sign-in:** sign in with the existing owner email and password. The account menu shows the account role, profile details, learner invitation, and sign out.
- **Learner sign-up:** owner creates an email-specific invite; learner opens it while connected to Tailscale, sets a password of at least 12 characters, and signs in. Learners never need the one-time owner setup secret.
- **Practice:** browse company and topic-linked DSA questions, start an attempt timer from a question, stop and save the attempt, then revisit attempt history and review dates. Save or bookmark System Design and LLD work independently.
- **Videos:** an authorized admin manages video metadata/publication from the signed-in workspace. Tailnet viewers open `/videos`; playback is streamed from the Windows SSD with HTTP byte-range support.
- **Source updates:** an admin previews upstream catalogue changes, reviews the diff, then applies the synchronization. Learner progress is preserved when source catalogue rows change.

## Architecture

```text
Browser on an approved Tailscale device
       │ HTTPS via Tailscale Serve (tailnet only)
       ▼
Caddy web container ───────────────┐
  full app :8081                   │ internal Docker network
  video-only listener :8082       ▼
       │                    Spring Boot API ───── PostgreSQL 17
       │                         │                    │
       └──── /videos and         └── metadata         └── accounts,
             public video API         + media bytes       preferences,
             path only                                   progress, invites
                                                       Windows SSD volume
```

- **Frontend:** React with Vite, served by Caddy from a production build. `src/main.jsx` owns the dashboard and page switching. Feature-specific styles are in `src/*.css`; account menu and invite UI live in `src/AccountMenu.jsx` and `src/account.css`.
- **Backend:** Java 21 and Spring Boot, organized as a modular monolith. API source is in `backend/src/main/java/dev/preptrack/`. Database changes are versioned in `backend/src/main/resources/db/migration/` and applied by Flyway.
- **Database:** PostgreSQL 17. Compose service has a named persistent volume. Never remove it during routine deploys; do not run `docker compose down -v` against the live deployment.
- **Media:** video bytes are files in the Windows media bind mount. The database stores metadata and publication state. The public video controller supports byte ranges so browser seeking works.
- **Web boundaries:** the main listener routes app and authenticated APIs. The isolated video listener permits only the video page, its built static assets, and `/api/public/videos/**`; other paths should return 404. Both host listeners bind to Windows loopback. Only Tailscale Serve exposes the main listener to the tailnet.

## Important capabilities already in the repository

- DSA catalogue, company/topic filters, difficulty preference, bookmarks, timed attempts with saved elapsed duration, attempt history, review queue, progress insights, and user-specific export.
- System Design and LLD learning libraries with status, confidence/review tracking, notes, and structured design packets.
- Source synchronization and admin catalogue corrections with audit history. Source material and the original links are documented in `docs/VERIFIED_SOURCES.md` and `docs/SOURCE_SYNC.md`.
- LLD video management and tailnet-only playback. The imported catalogue has 53 videos over 11 chapters; no video `11.3` is expected.
- Authentication uses PostgreSQL-backed Spring Session JDBC, CSRF protection, Argon2 password hashes, role checks, and throttled login/bootstrap/invite attempts. Flyway migration V9 owns the session tables. Logout invalidates the session through `POST /api/auth/logout`.

## Repository map

| Path | Responsibility |
|---|---|
| `src/main.jsx` | Dashboard layout, navigation, DSA and design learning pages, video library, sign-in and invite acceptance |
| `src/AccountMenu.jsx` | Profile actions, visible sign-out, admin learner invitation and invite-link display |
| `src/api.js` | Same-origin API fetch, CSRF token, session cookies, upload requests |
| `src/*.css` | Feature and responsive styles; `account.css` styles profile/invites |
| `backend/src/main/java/dev/preptrack/identity/` | Login, owner bootstrap, account preferences, role-based invites |
| `backend/src/main/java/dev/preptrack/learning/` | DSA, practice attempts, progress, review, bookmarks, insights |
| `backend/src/main/java/dev/preptrack/catalog/` | Upstream DSA source adapter, safe synchronization, corrections |
| `backend/src/main/java/dev/preptrack/media/` | Video upload, metadata, publication, range streaming |
| `backend/src/main/java/dev/preptrack/learning/` and `design/` | System Design and LLD case library and user-specific workspaces |
| `compose.windows.yaml`, `Dockerfile.web`, `backend/Dockerfile` | Windows production stack and images |
| `deploy/windows/` | Windows initialization, start/stop, backup, restore-drill scripts and ignored production env file |
| `docs/` | Detailed product decisions, sources, data model, deployment and backup runbooks |

The upstream/reference material identified so far includes `donnemartin/system-design-primer`, `liquidslr/system-design-notes`, and The Weekend Dev LLD structure. They are reference/source inputs, not services that PrepTrack depends on at runtime. The Telegram videos were user-provided media copied into the Windows media folder; preserve the source attribution/rights declaration entered by the owner.

## Local development

From the repository root, install Node.js 20+ and run:

```sh
npm install
npm run dev
```

The frontend expects the backend at the same origin unless `VITE_API_BASE` is set. For the backend, use Java 21, configure a local PostgreSQL database, set a random `PREPTRACK_INVITE_SECRET` only for the first-owner setup, then run `./gradlew bootRun` from `backend/`. Never use production credentials or a live user database for local experiments.

## Windows production update procedure

The Windows checkout is `C:\Users\LENOVO\Documents\preptrack`; production env is `deploy\windows\.env.production` in that checkout. Inspect `git status` before pulling: preserve any local user changes, including the previously observed `backend\gradlew` modification. Do not overwrite or commit local secrets.

From the Windows repository, after the reviewed code has been pushed to `origin/main`:

```powershell
git pull --ff-only origin main
docker compose --env-file deploy\windows\.env.production -f compose.windows.yaml build api web
docker compose --env-file deploy\windows\.env.production -f compose.windows.yaml up -d api web
docker compose --env-file deploy\windows\.env.production -f compose.windows.yaml ps
```

Keep the database container and named volume. Confirm API readiness, the app page, the video list and a byte-range stream after deployment. Tailscale Serve should continue to point to `http://127.0.0.1:8081`. Confirm its status remains tailnet-only; do not enable Funnel, router forwarding, or any public listener without a new explicit decision from the owner.

## Current product boundaries and next opportunities

- Learners can join only by an admin-created invite; there is no open signup or automatic email delivery.
- Access to all app pages and videos is restricted to the owner's Tailscale network. A forwarded website link cannot authorize an outside visitor.
- The current account menu displays basic profile information and role; profile editing, password change/reset, account listing, invite revoke, and account removal are not implemented.
- Backups and restore scripts exist. Confirm scheduled encrypted backups and a restore drill on the actual Windows host before relying on the laptop as the only copy of user data and video files.
- The Windows laptop must be online for the application and video storage to be available. Vercel is not a substitute backend or media host.

## Operating rules for the next coding assistant

1. Read this file first, then inspect the actual tree, `git status`, current GitHub branch, and live deployment. The filesystem and running host are the evidence; earlier chat plans can be stale.
2. Preserve the existing visual system, keep the React UI accessible and responsive, and make changes in the smallest relevant modules.
3. Preserve account progress during catalogue synchronization. Never delete or recreate the production database volume during an app update.
4. Keep passwords, bootstrap/database secrets, raw invite links, media exports, and user data out of source control and logs.
5. Treat `ADMIN` versus `USER` checks in the API as the security boundary. Hiding a control in React is not sufficient authorization.
6. Keep Tailscale Funnel and router port forwarding disabled unless the owner explicitly changes the access policy. Public account/video access would require a separately reviewed hosting and media plan.
