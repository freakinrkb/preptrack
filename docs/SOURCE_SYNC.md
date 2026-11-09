# Source synchronization strategy

## Principle

Adapt each source independently, version every import, and preserve personal data. Source links and compact metadata are the default; importing copyrightable prose, diagrams, or code needs an explicit licence review.

## Adapters

| Source | Adapter output | Initial cadence | Notes |
|---|---|---|---|
| `liquidslr/leetcode-company-wise-problems` | companies, question titles/URLs, difficulty, topics, and frequency metadata | admin preview + manual apply (Phase 2) | Current tree has company folders, each with numbered time-window CSVs. PrepTrack imports each `5. All.csv` once per company and fingerprints the downloaded archive. Topics/difficulty are source fields. |
| `donnemartin/system-design-primer` | topic/exercise entries and canonical repository links | weekly check + admin manual | Preserve the source’s conceptual/exercise separation. Review licence before copying anything. |
| The Weekend Dev LLD | later, link-only curated references | manual admin curation | No scraping/publishing mirror. Add only title, canonical URL, tags visible to the user, and source attribution after terms review. |
| Telegram channel | future optional link metadata | none | Never access or ingest until the owner explicitly enables it and defines permissions. |

## Pipeline

1. Fetch the public GitHub source archive and compute a SHA-256 fingerprint for the full archive.
2. If unchanged, record a lightweight successful check and stop.
3. Fetch to a staging representation, validate required identifiers/URLs, normalize titles, and calculate fingerprints.
4. Match source records by stable external key; use conservative URL/title matching only to propose manual review.
5. Produce additions, metadata changes, and archives. Do not automatically purge unmatched records.
6. For MVP, require admin approval of non-empty diffs. Apply the approved transactionally.
7. Store `sync_run`, revision, field-level diff, counts, and safe error summary.
8. On failure, keep the last known good catalogue and alert only the admin.

## Dedupe and change rules

- Identity order: source external ID/path → normalized canonical URL → manual merge candidate. Never merge solely on a fuzzy title.
- Multiple company records may point to one DSA `content_item`; the source association retains time window/evidence.
- A URL/title rename is a metadata change, not a new personal-learning record.
- A removed upstream row marks its source association absent and eventually archives the content item only when no active associations remain.
- Admin topic corrections are stored as curated metadata with provenance; an import may not overwrite them.

## GitHub access and rate limits

Use unauthenticated checks only for a private proof of concept; move to a fine-grained, read-only GitHub token stored outside the repository when limits demand it. Respect conditional requests, exponential backoff, timeouts, and rate-limit headers. A webhook is not a first-release requirement because a private laptop behind a mesh VPN cannot receive GitHub webhooks without a secure public endpoint.
