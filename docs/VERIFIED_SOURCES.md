# Verified source observations

Reviewed 8 November 2026. These are observations, not product commitments.

## The Weekend Dev — LLD

The [LLD index](https://theweekenddev.com/lld/) describes Java-focused LLD deep dives with architecture, design patterns, OOP principles, class diagrams, and implementations. It is a catalogue of case studies, not a user-progress product. Its list spans familiar interview systems (Parking Lot, Elevator, Movie Booking, Splitwise) and more advanced infrastructure cases (connection pool, circuit breaker, API gateway, CQRS).

Its [frequently asked LLD questions](https://theweekenddev.com/lld/lld-interview-questions/) presents a curated problem list and links to solutions. Individual case studies such as [WhatsApp](https://theweekenddev.com/lld/designing-whatsapp-low-level-design/) are positioned as design walkthroughs with diagram, patterns, and Java code. This supports a learning structure of: brief → domain model → relationships/diagram → patterns and trade-offs → personal explanation, rather than concept-only checklist items.

The site is a third-party publication. PrepTrack should store canonical links and small factual metadata, not copy its articles, diagrams, or code without separate permission.

Phase 3 rechecked the current [LLD index](https://theweekenddev.com/lld/) on 8 November 2026. It currently describes its posts as Java-oriented case studies covering design patterns and OOP. The starter links include [WhatsApp](https://theweekenddev.com/lld/designing-whatsapp-low-level-design/), [API Gateway](https://theweekenddev.com/lld/api-gateway-lld/), [Circuit Breaker](https://theweekenddev.com/lld/design-a-circuit-breaker/), [Payment Gateway](https://theweekenddev.com/lld/design-a-payment-gateway-aggregator/), and [Load Balancer](https://theweekenddev.com/lld/design-a-load-balancer/). PrepTrack stores these links and attribution only; its short briefs and reflection prompts are original learning scaffolds.

## `liquidslr/leetcode-company-wise-problems`

The [repository](https://github.com/liquidslr/leetcode-company-wise-problems) README says company directories contain CSV problem lists, with 30-day, 3-month, 6-month, >6-month, and all-time windows where available. Inspecting the current `main` tree at commit `2b71796266751b4736a7ef180efb6847879166ee` confirmed the CSV header: `Difficulty, Title, Frequency, Acceptance Rate, Link, Topics`. Example company rows provide LeetCode problem URLs and topic tags. The dataset landing page states it was updated 20 October 2025, while the current Git commit is dated 20 October 2026; those are different freshness signals and the import records a content fingerprint rather than assuming the README date is current.

The repository does not provide per-user tracking. PrepTrack imports only the `5. All.csv` file per company, retaining verified topic/difficulty metadata and source paths; overlapping company questions share one content item keyed by canonical problem URL.

Treat source columns, file paths, and licensing as sync-time facts: inspect the pinned commit and parser fixtures before implementing the importer. Do not infer a problem's topic, difficulty, or company association when the source does not supply it.

## `donnemartin/system-design-primer`

The [System Design Primer](https://github.com/donnemartin/system-design-primer) states that it is an organised collection for learning large-scale systems and system-design interview preparation. Its README groups concepts such as scalability, availability/consistency, DNS/CDN/load balancing, databases, caching, queues, communication, and security; it also includes system-design and object-oriented-design exercises, solutions, diagrams, and Anki decks.

Its study guide recommends breadth first, then deeper practice scaled to the learner's interview timeline. Its interview approach moves through requirements/constraints, high-level design, core components, then scaling and trade-offs. That is a strong source-backed template for PrepTrack system-design practice records.

The repository reports a CC BY 4.0 licence in its repository materials. Verify the current licence at the pinned revision before importing any text or media; favour links and original user notes.

The Phase 3 starter links to the Primer's README sections for performance/scalability, latency/throughput, availability/consistency, CAP, CDNs, load balancing, SQL/NoSQL, caching, queues, and interview approach. This is a manually curated link set rather than an automated content mirror; no article text or diagrams are copied into PrepTrack.

## Agent Skills format

The [Agent Skills overview](https://agentskills.io/home) defines a skill as a folder with a required `SKILL.md` containing at least `name` and `description`; optional `references/`, `scripts/`, and `assets/` support it. Discovery exposes only name/description, activation reads `SKILL.md`, and execution loads optional material as needed. The included skills use exactly that progressive-disclosure pattern.

## Deferred private resource

The user's Telegram channel of Striver LLD videos is deliberately not inspected or ingested. It remains a future, opt-in resource connector that must accept only user-authorized link metadata and preserve the channel's privacy/access rules.
