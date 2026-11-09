---
name: preptrack-product
description: Make or review PrepTrack product, scope, information-architecture, and acceptance decisions. Use for feature planning before implementation; do not use as a general coding skill.
---

# PrepTrack product guardrails

Keep PrepTrack a private interview-preparation tracker for DSA, system design, and LLD—not an online judge or content mirror.

- Preserve the boundary between source catalogue data and private user learning records.
- Prefer a small, testable increment. Keep explicitly deferred features deferred unless the request changes scope.
- Treat difficulty as per-user display preference and display only verified metadata.
- Keep DSA company-led; start System Design and LLD topic/format-led unless company evidence exists.
- For new or changed requirements, read `docs/PRODUCT_PLAN.md`; read `docs/DATA_MODEL.md` if data ownership or metrics are affected.

Before proposing a feature, name its user outcome, smallest acceptance criteria, source/provenance implication, and whether it is MVP or later.
