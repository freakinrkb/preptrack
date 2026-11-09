---
name: preptrack-backend
description: Implement or review PrepTrack Spring Boot, PostgreSQL, authorization, REST, and migration work. Use for server-side features; not for deployment operations or source-adapter changes.
---

# PrepTrack backend

Keep one modular Spring Boot application and PostgreSQL database. Organize modules around identity, catalogue, learning records, review, and administration; controllers stay thin and behavior belongs in services.

- Enforce user authorization at query/service boundaries for every personal record.
- Use migrations, DTO validation, UTC storage, foreign keys, and stable source identifiers.
- Prefer secure HttpOnly session cookies and CSRF protection for this small private web app; never expose long-lived browser tokens by convenience.
- Keep catalogue data separate from learning state. Attempts are append-only and current state is derived/updated deliberately.
- Add behavior-focused tests for authorization, migration invariants, and catalogue-import preservation.

Read `references/security-baseline.md` when identity, sessions, an endpoint that writes data, or personal notes are involved.
