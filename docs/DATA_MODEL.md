# PrepTrack data model

Use PostgreSQL with UUID primary keys, UTC timestamps, `created_at`/`updated_at`, foreign keys, and migrations. This is a logical model, not schema code.

## Catalogue (shared, source-derived)

| Entity | Essential fields | Notes |
|---|---|---|
| `source` | id, slug, canonical_url, licence_note, enabled | One row per upstream repository/site integration. |
| `source_revision` | id, source_id, revision_ref, fetched_at, status, summary | Git SHA or content fingerprint; immutable audit history. |
| `content_item` | id, track, kind, canonical_title, canonical_url, lifecycle | `track`: DSA/SYSTEM_DESIGN/LLD. `kind`: question/concept/exercise/case_study. |
| `source_item` | source_id, source_revision_id, external_key, content_item_id, raw_metadata_json | Preserves provenance and enables reconciliation. Unique `(source_id, external_key)`. |
| `topic` | id, track_nullable, name, slug, provenance | Vocabulary is curated; no guessed taxonomy. |
| `content_topic` | content_item_id, topic_id | Many-to-many. |
| `company` | id, name, slug | DSA first; optional later for other tracks. |
| `content_company` | content_item_id, company_id, evidence_source_item_id | Requires evidence. |
| `content_reference` | content_item_id, label, url, reference_type, source_id | Source/article/problem links; no copied full content. |
| `catalogue_correction` | content_item_id, actor_user_id, field_name, previous_value, corrected_value, evidence_url, reason, status, created_at | Admin-only, append-only correction provenance. `catalogue_item` overlays the active corrected title while preserving `content_item.canonical_title`. |

`content_item.lifecycle` is active/archived. Never hard-delete a source item that has a personal record.

## Personal learning (private)

| Entity | Essential fields | Notes |
|---|---|---|
| `user_account` | id, email, password_hash, role, status | Roles: USER/ADMIN. Invite-only status. |
| `user_preference` | user_id, show_difficulty, timezone, theme | Difficulty is presentational only. |
| `learning_state` | user_id, content_item_id, status, latest_attempt_at, completed_at, next_review_at | Unique `(user_id, content_item_id)`. |
| `learning_attempt` | id, user_id, content_item_id, occurred_at, outcome, independence_rating, confidence, duration_seconds, note | Append-only history. Rating 1–5; duration is the active problem-solving seconds for this attempt (0–86,400), nullable for older attempts. |
| `user_note` | id, user_id, content_item_id, body, note_type | Free-form notes; `note_type` includes tradeoff/retrospective. |
| `bookmark` | user_id, content_item_id, created_at | Unique pair. |
| `design_workspace` | user_id, content_item_id, track, response_json, updated_at, completed_at | Private structured System Design / LLD draft; unique per learner and item. JSON keys are allow-listed by track in the API and bounded in size. |
| `review_schedule` | user_id, content_item_id, due_at, interval_days, last_action | Derived/updateable; preserve schedule decisions. |
| `lld_design_packet` | user_id, content_item_id, requirements, entities, relationships, patterns, edge_cases, tradeoffs, retrospective, status | One structured working record per user/item; diagrams later reference an attachment, never public content by default. |
| `design_exercise_record` | user_id, content_item_id, assumptions, high_level_design, components, bottlenecks, tradeoffs | Equivalent structured record for system-design exercises. |

## Operational entities

| Entity | Essential fields |
|---|---|
| `sync_run` | id, source_id, started_at, finished_at, trigger, candidate_revision, status, counts, error_summary |
| `sync_change` | sync_run_id, change_type, source_item_key, content_item_id_nullable, before_hash, after_hash, approval_status |
| `audit_event` | id, actor_user_id_nullable, type, resource, resource_id, occurred_at, safe_metadata_json |
| `invite` | id, email, role, token_hash, expires_at, accepted_at, created_by |

## Derived metrics (calculate; do not persist initially)

- Completion = completed active items / active items in the selected filter.
- Strength = average independence score mapping of rated, completed latest attempts. Display count.
- Topic/company strength = the same aggregation scoped to a topic/company.
- Streak = consecutive local days with a learning attempt, based on user timezone.

## Important constraints

1. Catalogue imports never write `learning_state`, attempts, notes, or packets.
2. A content item can have many upstream references and many companies/topics.
3. Source revision data remains replayable for debugging.
4. User-facing deletes are soft deletes where history or auditability matters.

System Design and LLD shared catalogue rows contain source links, attribution metadata, and short PrepTrack-authored prompts. The learner's requirements, estimates, model, pattern rationale, edge cases, diagram reference, and trade-offs stay in `design_workspace.response_json`. Completing a packet appends a `learning_attempt` and updates that learner's `learning_state` with a review date. Saving a draft does not erase an earlier completion or affect another account.
