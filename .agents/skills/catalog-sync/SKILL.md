---
name: catalog-sync
description: Design, implement, or review PrepTrack imports from approved learning sources. Use for source adapters, catalogue reconciliation, sync runs, and source-data failures; not for personal progress features.
---

# PrepTrack catalogue synchronization

Import source facts into the shared catalogue without changing user progress, attempts, notes, bookmarks, or design packets.

- Build one adapter per source; do not force disparate source formats into one parser.
- Identify entries using stable source keys before canonical URL, and never merge based on fuzzy titles alone.
- Stage, validate, diff, and audit each run. A failed run must retain the last known-good catalogue.
- Use link-first attribution; do not copy prose, diagrams, or code until licence/terms have been checked at the pinned revision.
- Do not access the user’s Telegram channel unless explicit permission and resource boundaries are supplied.

Read `references/sync-rules.md` before changing an adapter or sync workflow.
