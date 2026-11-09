# PrepTrack planning workspace

PrepTrack is a lightweight, multi-user technical interview preparation tracker—not an online judge. It combines company-wise DSA, system design, and low-level design (LLD), while keeping each learner's progress private and separate from upstream catalogues. The first implementation is taking shape here as a React frontend plus a planned Spring Boot modular monolith.

Start with [the product plan](docs/PRODUCT_PLAN.md), then [the phased roadmap](docs/IMPLEMENTATION_ROADMAP.md). The source facts behind the plan are in [verified observations](docs/VERIFIED_SOURCES.md); proposals are explicitly labelled in the plan.

## Run the frontend

Install Node.js 20+ and run `npm install`, then `npm run dev`. Open the printed `http://127.0.0.1:5173/` address; opening `index.html` as a file will show a blank page because the React modules need a local server. The app shows the sign-in/first-owner screen when the API is available and a clearly marked preview when it is not.

## Backend foundation

The Spring Boot service is in `backend/`; from the project root start PostgreSQL with `docker compose up -d db`, then from `backend/` run `./gradlew bootRun`. Set `PREPTRACK_INVITE_SECRET` before starting the API. The first account is created through the one-time setup screen and secret documented in `backend/README.md`; rotate the bootstrap secret after use. Run `./gradlew test` in `backend/` to exercise access boundaries against a dedicated `preptrack_test` PostgreSQL database (or set `PREPTRACK_TEST_DB_URL`, `PREPTRACK_TEST_DB_USER`, and `PREPTRACK_TEST_DB_PASSWORD`). Never point the test database setting at a database containing user data.

## Project-local agent skills

Skills live in `.agents/skills/` so they can travel with the repository. This follows the portable Agent Skills folder convention (`SKILL.md` plus optional on-demand references). If the eventual coding tool has a different project-skill discovery path, copy or link these folders there; do not duplicate their contents.

| Skill | Invoke when |
|---|---|
| `preptrack-product` | deciding scope, information architecture, or acceptance criteria |
| `catalog-sync` | importing, reconciling, or diagnosing GitHub source data |
| `lld-study` | designing the LLD learning experience or its content model |
| `preptrack-frontend` | implementing or reviewing React UI work |
| `preptrack-backend` | implementing or reviewing Spring Boot, PostgreSQL, or REST work |
| `windows-deploy` | preparing the Windows-hosted deployment, backup, or remote access |

There is intentionally no “do everything” skill. Each has a narrow trigger and loads further detail only when needed.

## Private Windows deployment

The Windows deployment scaffold and runbook are in [docs/WINDOWS_DEPLOYMENT.md](docs/WINDOWS_DEPLOYMENT.md). It uses Docker Desktop, a private PostgreSQL container, a production React build behind Caddy, and Tailscale Serve for tailnet-only HTTPS. No router port forwarding or public Funnel is part of the setup. The deployment has not been installed on the Windows laptop yet; finish the target-device and backup/restore steps in [the roadmap](docs/IMPLEMENTATION_ROADMAP.md) before calling it live.
