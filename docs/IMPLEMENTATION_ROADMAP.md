# Phased implementation roadmap

Implementation is underway in this workspace; completed items are marked below.

## Phase 0 — foundation and decisions

- Adopt these specifications and skills.
- Pin exact upstream revisions, inspect licences/terms, and create parser fixtures from permitted metadata.
- Choose the initial access boundary: owner-only LAN or named users over private mesh VPN. Default recommendation: named users over mesh VPN.
- Define invite-only account policy and backup destination.

**Done when:** scope is frozen for MVP; source contracts and privacy boundaries are documented.

## Phase 1 — skeleton and secure identity — complete

- [x] Create the modular monolith, database migration, and local developer environment.
- [x] Implement owner bootstrap, invite-only account acceptance, secure sessions, account preferences, and audit events.
- [x] Add React shell, responsive navigation, first-run/sign-in flow, saved difficulty-display preference, and visible preview state.
- [x] Add automated anonymous-access, account-isolation, and role-boundary checks plus CI build workflow.

**Done:** the secure identity foundation and responsive interface are in place. The CI workflow compiles and runs the authorization checks against PostgreSQL and builds the frontend.

## Phase 2 — DSA catalogue and tracking — complete

- [x] Implement the DSA CSV adapter, source fingerprint preview, and admin apply endpoint.
- [x] Build company/topic/question browsing, canonical links, bookmarks, attempts, and independence ratings.
- [x] Add per-user completion/review summary calculations and saved status.
- [x] Add page-level source-change detail, attempt-history view, and live DSA catalogue/learner metrics on the overview.
- [x] Add an automated import replay check proving source updates retain user completion, attempts, and bookmarks.
- [x] Run the first real source preview and owner-approved import; verify the full upstream archive against the live importer.

**Done:** the live preview and import completed against the dedicated local `preptrack_app` database on 2026-11-08. The imported snapshot contains 16,898 company-question associations across 422 companies, 3,396 unique active DSA questions, and 174 topics. Its source ZIP SHA-256 fingerprint is `31222525af9a91c7390609eb86600c84509cebfa4d7dc256b4dd79a0ec9dafad`. The importer also handles upstream CSVs that omit optional difficulty and topic columns; those fields remain unknown when the source does not provide them. Replay/isolation coverage verifies that catalogue reconciliation does not overwrite a learner's progress, attempts, or bookmarks.

## Phase 3 — system design and LLD — complete

- [x] Add a curated, link-first System Design Primer concepts/exercise library with PrepTrack-authored prompts.
- [x] Add 28 attributed, link-only chapter entries for `liquidslr/system-design-notes`, with source filtering and original PrepTrack practice prompts.
- [x] Add attributed LLD case studies with original briefs and pattern/topic navigation.
- [x] Implement private design workspaces, draft saving, confidence-rated completion, and a three-day review date.
- [x] Add System Design and LLD navigation and live dashboard counts.

**Done when:** a signed-in learner can save a draft or complete one LLD case with requirements, entities, optional pattern rationale, trade-offs, and a review date; the learning record stays isolated by account.

**Verified:** the earlier authenticated smoke checks saved and read a private design draft. The signed-in dashboard now loads the authenticated seven-day activity endpoint and displays real attempt counts in the learner's saved timezone; an empty account correctly shows zero activity. Frontend production build and backend automated tests pass.

## Phase 4 — review and insight — complete

- [x] Add due review queue, track filters, one/three/seven-day rescheduling, and links back to the matching study item.
- [x] Add per-user bookmarks view and streak calculation using the learner’s timezone.
- [x] Add track/topic insights that distinguish completion from rated strength and show rating sample sizes.
- [x] Add download of the current user’s learning state, attempts, bookmarks, and design workspaces as JSON.
- [x] Add admin-only catalogue title corrections with evidence, reason, superseded history, and audit event; keep the upstream title intact.

**Verified:** authenticated smoke checks passed for filtered and unfiltered DSA browsing, System Design and LLD libraries, track/topic insights, reviews, bookmarks, export, admin correction search, bookmark toggling, attempt recording, review scheduling, and private design draft save/read. The existing automated suite also passed: 8 tests passed; the opt-in live-source import was skipped. Temporary verification records and the disposable test database were removed. The reported DSA 500 came from a missing `GROUP BY` field after introducing the catalogue title overlay; insights also bound a UUID as text. Both are corrected. The design filter dropdown now uses the dark theme.

## Phase 5 — private Windows deployment — live; reliability work remains

- [x] Add production container builds and a Windows-focused Compose stack with PostgreSQL private to the container network.
- [x] Serve the built frontend and proxy the API through Caddy; publish only a loopback host port for Tailscale Serve.
- [x] Add private Tailscale Serve guidance, secret initialization, start/stop scripts, and encrypted backup script.
- [x] Document the private VPS migration and restore runbook in [VPS_MIGRATION.md](VPS_MIGRATION.md).
- [x] Run the stack on the owner's Windows laptop; owner-confirmed restart and authorized Mac access over Tailscale HTTPS.
- [x] Configure the owner's tailnet; verify the private app and DSA library from an authorized remote device. Funnel and router port forwarding remain disabled.
- [x] Import and verify the first DSA catalogue: 422 companies and 3,396 unique active questions.
- [ ] Measure resource use on the Windows laptop under normal study use.
- [ ] Perform an encrypted backup to a separate location and complete a restore drill.

**Done when:** a restart survives, remote authorized access works, the database is not publicly reachable, and a backup restoration has succeeded on the target Windows laptop.

## Next — Windows-hosted LLD video library (rights and audience decision needed)

- [ ] Confirm that each video may be redistributed by the user; otherwise keep it private for personal viewing or link to an authorized source.
- [ ] Keep the current private Tailscale-only access boundary unless public hosting is deliberately chosen and separately designed.
- [ ] Add a Windows SSD media root outside the repository and Docker image; store metadata and checksums in PostgreSQL, never the video bytes.
- [ ] Add owner-only import, atomic staging, duplicate detection, disk quota/free-space checks, and authenticated byte-range playback.
- [ ] Exclude media from Git, the container build context, and the existing database-only backup; define a separate encrypted media backup and restore plan.
- [ ] Measure laptop uptime, upload bandwidth, storage growth, and concurrent streaming before inviting more viewers.

See [VIDEO_LIBRARY.md](VIDEO_LIBRARY.md) for the proposed storage and delivery design.

## Later only

Evaluate Telegram as an opt-in resource connector, richer diagrams, public deployment, notifications, OAuth, and any AI assistance only after the core learning loop is stable.
