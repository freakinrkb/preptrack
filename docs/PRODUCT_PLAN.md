# PrepTrack — lean product plan

## Product promise

One private preparation workspace for three related tracks: company-wise DSA, system design, and LLD. A learner can find the next item, open the original resource, record what happened, and return at the right time. PrepTrack does not run code, judge submissions, host copied learning content, or attempt to replace LeetCode.

## Proposed product decisions

- **Audience:** the owner first, with a secure path to a small invited group (roughly 5–10 accounts); no public sign-up in the first release.
- **Learning units:** catalogue entries are immutable-ish source data; personal status, attempts, notes, confidence, and revision schedule belong to the user.
- **Difficulty control:** a per-user preference. When off, difficulty labels are hidden—not removed or used to change progress. Only show verified source difficulty.
- **Tracking:** retain attempt history; the current state is a summary of the latest attempt. Strength is not an assessment of an unattempted item.
- **LLD:** use a structured “design packet,” not a simple done checkbox.
- **Company filtering:** primary for DSA. System design/LLD begin topic and format based; company tags may be added only where a source credibly provides them.
- **Architecture:** modular monolith: React client, one Spring Boot API, one PostgreSQL instance. HTTP/2 terminates at the reverse proxy.

## Information architecture

```text
PrepTrack
├── Home dashboard
├── Learn
│   ├── DSA: company → topic → questions
│   ├── System design: foundations → components → exercises
│   └── LLD: patterns → case studies → design packets
├── Review queue
├── Bookmarks
├── Insights
└── Settings (profile, display, account)
Admin (invited owner only): source health, sync preview, catalogue corrections
```

Search is global, but results retain their track and source. A compact left navigation on desktop becomes a bottom or drawer navigation on mobile.

## Page-by-page UI/UX

| Page | Primary content | Key actions |
|---|---|---|
| Sign in / invite acceptance | minimal email/password form; rate-limit feedback | sign in, reset password, accept invite |
| Onboarding | choose tracks, target companies, target date optional, display-difficulty preference | save preferences; enter dashboard |
| Dashboard | three separate progress cards, review-due count, “continue learning,” recent attempts | resume, open review queue, change track |
| DSA explorer | company picker, search, topic groups, totals; difficulty chips only if enabled | filter, open original problem, bookmark |
| Question drawer/page | title, canonical external link, source attribution, topics, companies, user history | log attempt, mark completed, add note |
| System-design library | concept/exercise tabs, topic tree, source links | study, record understanding, schedule review |
| System-design exercise | guided record: requirements, scale assumptions, diagram link/file reference later, components, bottlenecks, trade-offs | save draft, log attempt, complete |
| LLD library | pattern and case-study filters; case cards show estimated scope and source link | start design packet, bookmark |
| LLD design packet | brief; requirements; entities/value objects; relationships/class diagram reference; patterns with rationale; edge cases/concurrency; trade-offs; retrospective | save draft, complete, schedule review |
| Review queue | due items ordered by urgency and last confidence | revisit, log reattempt, snooze once |
| Insights | completion and strength separated by track/company/topic; recent consistency | inspect weak/untouched areas |
| Admin sync | source status, last commit, dry-run diff, errors | preview, approve sync, retry safely |

### Small interaction rules

1. External problem links open in a new tab and never imply a solved state.
2. Completing an item opens a short attempt form: outcome, independence (1–5), optional note, next review suggestion.
3. Progress card denominators use active catalogue entries; source removals are archived, never erase a user record.
4. “Strength” is an average of completed/latest rated attempts; display its sample size beside it.
5. Every imported item displays its source link and last source sync date.

## Feature scope

### MVP — include

- Invite-only local accounts, sessions, password reset by owner-assisted flow initially.
- DSA company/topic browsing; external source links; bookmarks.
- Per-user attempts, completion, five-point independence rating, notes, and simple review dates.
- System-design concepts and exercises from linked source metadata.
- LLD case studies with the structured design packet and source links.
- Source catalogue import, manual dry-run, scheduled fallback sync, audit log.
- Dashboard, review queue, basic insights, and backups.

### Explicitly defer

- compiler/editor/online judge; public profiles/feed; OAuth; email delivery; push notifications; AI-generated classifications; social collaboration; Telegram content ingestion; public webhooks; mobile app; microservices; full diagram editor.

## Core user flows

### First use

Owner creates an invite → invitee sets a password → chooses tracks/companies and difficulty display → sees an empty, honest dashboard with a recommended first item.

### DSA learning

Select company → narrow by topic → open canonical problem → return → log attempt with independence and optional notes → dashboard and review date update.

### LLD learning

Open case study → read the brief and source link → fill a design packet in stages → self-rate the explanation and design confidence → mark completed only when ready → review queue links back to the packet.

### Safe source update

Scheduler sees new commit (or admin requests it) → adapter fetches data to a staging run → validation and deduplication produce a diff → admin approves changes for early releases → catalogue version advances; user records remain untouched → outcome is logged.

## Data model summary

See [DATA_MODEL.md](DATA_MODEL.md). The two important boundaries are `catalogue` versus `personal learning`, and `source observation` versus `admin-curated metadata`.

## Security and privacy

- Store password hashes with Argon2id (or Spring Security’s current strong adaptive encoder); never store plaintext passwords or tokens.
- Use secure, HttpOnly, SameSite session cookies; protect write endpoints from CSRF. Prefer server sessions for this small application over exposing long-lived browser JWTs.
- Enforce authorization in every API query by user/account; administration is a role, not a hidden URL.
- Validate and size-limit notes; escape rendered text; restrict any later upload feature to allow-listed file types.
- Keep secrets in Windows environment variables or a local secret store, outside version control. Rotate on exposure.
- TLS at the reverse proxy; HSTS only once the hostname and HTTPS are stable. Never expose PostgreSQL or Windows remote desktop publicly.
- Log authentication/sync/security events without passwords, access tokens, full notes, or Telegram data. Back up encrypted database dumps and test restore.

## Windows laptop constraints and deployment shape

An 8 GB RAM / 400 GB SSD Windows laptop is adequate for the MVP and a small, private group if it is treated as a single-host service:

```text
Private devices ── HTTPS / HTTP/2 ── reverse proxy ── React static files + Spring Boot API ── PostgreSQL
                                                        (same Windows laptop; DB not internet-exposed)
```

- Prefer an Ethernet connection, AC power, disabled sleep while serving, and a BIOS/Windows power-on recovery plan.
- Run the React production build as static files; do not leave a development server running.
- Budget conservatively: Windows ~3–4 GB, PostgreSQL ~0.5–1 GB, Spring Boot max heap initially 512 MB–1 GB; monitor before adding services. Containers are optional—not a requirement.
- For initial remote access, use a private mesh VPN such as Tailscale; do not port-forward the app. Public access later needs a stable domain, HTTPS, reverse-proxy hardening, patch discipline, and rate limiting.
- Daily database backup to a separate encrypted location; retain at least 7 daily and 4 weekly copies. Test a restore monthly.
- Expect laptop power/network outages. A future VPS migration should be planned around container/backup portability, but not implemented first.

## Success measures

Within one minute, a user can choose a track and start an item. Within two minutes after studying, they can capture an attempt and know when to revisit. An upstream source change never destroys personal progress. The owner can restore a backup and explain source provenance for every catalogue item.
