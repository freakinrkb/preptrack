# PrepTrack API foundation

This is the first modular Spring Boot service: identity and preferences are separated from future catalogue and learning modules. Java 21 is the app toolchain; Spring Boot 4.1.2 is the current documented release used for this scaffold.

## Local start

1. Start PostgreSQL from the project root: `docker compose up -d db`.
2. Set `PREPTRACK_INVITE_SECRET` to a random secret of at least 24 characters.
3. From this directory run `./gradlew bootRun` (generate the wrapper with installed Gradle once, or use `gradle bootRun`).
4. Fetch `GET /api/auth/csrf`, then send `POST /api/auth/bootstrap` with that CSRF header, the `X-PrepTrack-Bootstrap` secret, and JSON `{ "email": "you@example.com", "password": "a-long-unique-password" }`.
5. The bootstrap endpoint succeeds only while the user table is empty. Remove/rotate the bootstrap secret after creating the owner.

Login uses `GET /api/auth/csrf`, then `POST /api/auth/login` with the CSRF header and email/password. `GET /api/auth/me` returns the current user's profile. `POST /api/auth/logout` requires CSRF. An authenticated owner can create an invite with `POST /api/auth/admin/invites` and `{ "email": "friend@example.com" }`; the one-time token is returned once and must be shared privately by the owner. The invitee accepts through the sign-in screen using a URL with `?invite=TOKEN&email=ADDRESS`. Authenticated users save preferences through `POST /api/auth/preferences`.

For local HTTP, cookies are not marked Secure. Set `PREPTRACK_COOKIE_SECURE=true` behind HTTPS. Do not expose this service or PostgreSQL publicly until rate limiting, invite acceptance, password reset, and production deployment configuration are complete. Normal multi-user invites are the next identity slice; the bootstrap endpoint is strictly for the first owner.
