# Public cloud deployment: Vercel + Render

PrepTrack's frontend is a Vite static site, while identity, learning progress, and PostgreSQL run in the Spring Boot API. Vercel serves the frontend and proxies same-origin `/api/*` and `/actuator/*` requests to the API. Render runs the API container and managed PostgreSQL in Singapore. PostgreSQL is accessed by the API over Render's private network. The browser never connects directly to the database or API host.

## Cost and account boundary

The checked-in Render Blueprint requests a 512 MB always-on API service and a 256 MB paid PostgreSQL instance. Render's published rates currently indicate about **US$13/month** total before storage or bandwidth growth: US$7/month for the API plan and US$6/month for the database. Vercel static hosting is separate and may remain within its current plan limits. Confirm the price in the provider checkout before creating resources. The cloud resources have not been created by this repository change.

## Deployment files

- `render.yaml` defines the API and managed database. Render creates a random bootstrap secret and private database credentials.
- `vercel.json` hosts the SPA, adds security headers, and proxies API requests through the Vercel origin so session and CSRF cookies remain same-origin in the browser.
- `backend/src/main/resources/application.yml` composes the JDBC URL from Render's private database properties while preserving the local `PREPTRACK_DB_URL` override.
- The API rate-limits owner bootstrap, account sign-in, and invite acceptance by normalized account email, with bounded in-memory state and a 15-minute window. This is intended for one API instance; do not scale out without replacing it with shared rate-limit storage.

## First deployment and owner setup

1. In Render, create a Blueprint instance from the private `freakinrkb/preptrack` GitHub repository and inspect the plan, region, and final monthly price before applying. The API and database must use the same Singapore region.
2. In Vercel, import the same repository as a Vite project, using the repository root, `npm run build`, and `dist` as the output directory. Deploy only after the Render API hostname matches the proxy destinations in `vercel.json`.
3. Wait for `/actuator/health/readiness` to report healthy. Confirm PostgreSQL has no public inbound access and the API is reachable only through the Vercel proxy for normal browser use.
4. Retrieve `PREPTRACK_INVITE_SECRET` from the Render API environment settings and enter it once in PrepTrack's owner setup screen. Create the owner account, then delete that secret from the API environment and redeploy. Do not copy the secret into chat or GitHub.
5. Import the curated DSA catalogue through the authenticated source-sync screen. Do not copy local user records unless the owner explicitly requests a data migration.
6. Verify owner login, attempt timing and persistence, private LLD draft save/read, invite acceptance, logout, and account isolation on the deployed URL. Confirm a PostgreSQL backup/restore process before inviting additional users.

Password recovery is not implemented. Keep the initial deployment owner-only and do not invite additional users until a recovery path is available and documented. The local Windows/Tailscale deployment also remains unconfigured.

## Operational notes

- Keep the GitHub repository private. CI runs the API authorization checks and frontend build on each push.
- Keep Render API and database secrets out of GitHub and Vercel build variables.
- The current plan uses one API instance; the in-memory authentication limiter is reset by process restarts and is not shared across multiple instances.
- The local Windows/Tailscale deployment remains a separate option; do not run two writable databases for the same PrepTrack account.
