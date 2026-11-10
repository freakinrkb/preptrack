-- Add a link-only practice catalogue for liquidslr/system-design-notes.
-- PrepTrack stores repository chapter links and original prompts, not the notes,
-- diagrams, or other material from the upstream repository.
INSERT INTO source(slug, canonical_url) VALUES
    ('liquidslr-system-design-notes', 'https://github.com/liquidslr/system-design-notes')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO source_revision(source_id, revision_ref, status, summary)
SELECT id, 'curated-link-only-2026-11-10', 'APPLIED',
       '{"method":"manual-link-catalogue","items":28,"contentCopied":false}'::jsonb
FROM source WHERE slug='liquidslr-system-design-notes'
ON CONFLICT (source_id, revision_ref) DO NOTHING;

CREATE TEMP TABLE liquidslr_seed (
    external_key VARCHAR(500) NOT NULL,
    title VARCHAR(300) NOT NULL,
    url TEXT NOT NULL,
    category VARCHAR(80) NOT NULL,
    summary TEXT NOT NULL,
    prompt TEXT NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    topics TEXT[] NOT NULL,
    kind VARCHAR(24) NOT NULL
) ON COMMIT DROP;

INSERT INTO liquidslr_seed VALUES
('chapter-01-scaling','Scale from zero to millions of users','https://github.com/liquidslr/system-design-notes/tree/main/01.%20Scaling','Foundations','Explore how a service can evolve as its audience and workload grow.','Sketch a service for its first 100 users, then describe the changes you would make at 10 million users. Identify the first bottleneck at each stage and the signal that would trigger the next change.',35,ARRAY['Scaling','Foundations'],'EXERCISE'),
('chapter-02-estimation','Back-of-the-envelope estimation','https://github.com/liquidslr/system-design-notes/tree/main/02.%20Back%20Of%20the%20Envelope%20Estimation','Foundations','Practice turning product assumptions into rough traffic, storage, and bandwidth estimates.','Choose a product and estimate average and peak requests per second, annual storage growth, and bandwidth. State every assumption and show which estimate most affects your design.',25,ARRAY['Capacity planning','Foundations'],'EXERCISE'),
('chapter-03-framework','System design interview framework','https://github.com/liquidslr/system-design-notes/tree/main/03.%20System%20Design%20Framework','Interview practice','Use a repeatable sequence to move from an open-ended prompt to a defensible design.','Run a timed design session: clarify users and constraints, estimate demand, define APIs and data, sketch components, then examine bottlenecks and failure modes. Record what you would ask before committing to a design.',35,ARRAY['Interview practice','Foundations'],'EXERCISE'),
('chapter-04-rate-limiter','Design a rate limiter','https://github.com/liquidslr/system-design-notes/tree/main/04.%20Rate%20Limiter','Traffic management','Design request limits that protect a service while accounting for identity, burst behavior, and distributed enforcement.','Define the limit key, algorithm, storage location, and response for an over-limit request. Explain how you handle bursts, clock or counter behavior across instances, and a backing-store outage.',40,ARRAY['Rate limiting','Reliability','Distributed systems'],'CASE_STUDY'),
('chapter-05-consistent-hashing','Design consistent hashing','https://github.com/liquidslr/system-design-notes/tree/main/05.%20Consistent%20Hashing','Distributed systems','Reason about assigning keys to a changing set of nodes while limiting data movement.','Model a ring with virtual nodes. Walk through adding and removing a node, then discuss skew, replication, and how clients learn the current ring.',35,ARRAY['Consistent hashing','Distributed systems'],'EXERCISE'),
('chapter-06-key-value-store','Design a key-value store','https://github.com/liquidslr/system-design-notes/tree/main/06.%20Key-Value%20Store','Storage','Explore partitioning, replication, and read/write behavior for a distributed key-value service.','Specify the consistency and availability expectations for reads and writes. Sketch partition placement, replica repair, and behavior when a node is unavailable.',45,ARRAY['Storage','Distributed systems','Reliability'],'CASE_STUDY'),
('chapter-07-unique-id-generator','Design a unique ID generator','https://github.com/liquidslr/system-design-notes/tree/main/07.%20Unique-Id%20Generator','Data','Compare ID generation approaches under scale, ordering, uniqueness, and operational constraints.','Choose an ID format for a globally distributed service. Explain how it avoids collisions, whether it preserves useful ordering, and how it behaves during clock skew or generator failure.',35,ARRAY['Identifiers','Distributed systems','Data'],'CASE_STUDY'),
('chapter-08-url-shortener','Design a URL shortener','https://github.com/liquidslr/system-design-notes/tree/main/08.%20URL%20Shortener','Web services','Design short-link creation and redirection with explicit storage, caching, and abuse considerations.','Define create and redirect APIs. Estimate read/write traffic, choose a code-generation strategy, and explain expiration, hot links, and abuse controls.',40,ARRAY['Web services','Caching','Data'],'CASE_STUDY'),
('chapter-09-web-crawler','Design a web crawler','https://github.com/liquidslr/system-design-notes/tree/main/09.%20Web%20Crawler','Async processing','Plan a crawler that schedules URLs, fetches pages responsibly, and processes results at scale.','Describe URL discovery, deduplication, frontier scheduling, fetch workers, and storage. Explain how you limit per-host load and recover work after worker failure.',45,ARRAY['Web crawling','Async processing','Reliability'],'CASE_STUDY'),
('chapter-10-notification-system','Design a notification system','https://github.com/liquidslr/system-design-notes/tree/main/10.%20Notification%20System','Communication','Model notification requests, user preferences, delivery channels, retries, and delivery status.','Design an API and asynchronous flow for email, push, and SMS notifications. Explain idempotency, preference changes, provider failures, retry limits, and delivery tracking.',45,ARRAY['Notifications','Async processing','Reliability'],'CASE_STUDY'),
('chapter-11-news-feed','Design a news feed','https://github.com/liquidslr/system-design-notes/tree/main/11.%20News%20Feed%20System','Social systems','Compare feed generation choices and their effects on freshness, fan-out, and read latency.','Design feed creation and retrieval for users with very different follower counts. Compare write-time fan-out and read-time assembly, and describe how ranking changes affect cached feeds.',45,ARRAY['Feeds','Caching','Data'],'CASE_STUDY'),
('chapter-12-chat-system','Design a chat system','https://github.com/liquidslr/system-design-notes/tree/main/12.%20Chat%20System','Communication','Consider message delivery, conversation history, presence, and reconnect behavior in a chat service.','Define the message lifecycle for online and offline recipients. Explain ordering, acknowledgements, reconnect catch-up, group delivery, and how message history is partitioned.',45,ARRAY['Messaging','Networking','Reliability'],'CASE_STUDY'),
('chapter-13-search-autocomplete','Design search autocomplete','https://github.com/liquidslr/system-design-notes/tree/main/13.%20Search%20Autocomplete','Search','Explore prefix lookup, ranking signals, index updates, and latency for autocomplete suggestions.','Design an autocomplete API and index for a large query corpus. Explain prefix lookup, ranking, freshness, hot prefixes, and how you keep responses fast.',40,ARRAY['Search','Caching','Data'],'CASE_STUDY'),
('chapter-14-youtube','Design a video streaming service','https://github.com/liquidslr/system-design-notes/tree/main/14.%20Youtube','Media','Trace video upload, processing, storage, and playback while accounting for varied network conditions.','Design an upload-to-playback path. Cover metadata, transcoding, storage, delivery, adaptive quality, and what happens when processing or a regional cache is unavailable.',50,ARRAY['Video','Storage','Networking'],'CASE_STUDY'),
('chapter-15-google-drive','Design a file synchronization service','https://github.com/liquidslr/system-design-notes/tree/main/15.%20Google%20Drive','Storage','Model file metadata and content synchronization across devices, including concurrent edits.','Define file and folder operations and a sync protocol. Explain chunking, version conflicts, offline edits, access control, and how clients resume a partial upload.',50,ARRAY['File storage','Synchronization','Reliability'],'CASE_STUDY'),
('chapter-16-proximity-service','Design a proximity service','https://github.com/liquidslr/system-design-notes/tree/main/16.%20Proximity%20Service','Geo services','Find nearby places while balancing spatial indexing, freshness, and location privacy.','Design a nearby-search API. Choose a spatial partitioning method, explain how location updates are indexed, and state how you limit exposure of precise user locations.',45,ARRAY['Geo','Search','Privacy'],'CASE_STUDY'),
('chapter-17-nearby-friends','Design a nearby-friends service','https://github.com/liquidslr/system-design-notes/tree/main/17.%20Nearby%20Friends','Geo services','Explore opt-in location sharing, proximity queries, and privacy-aware updates.','Define opt-in and visibility rules before designing the location flow. Explain update cadence, nearby matching, stale locations, and how a user can stop sharing immediately.',45,ARRAY['Geo','Privacy','Real-time systems'],'CASE_STUDY'),
('chapter-18-google-maps','Design a maps and navigation service','https://github.com/liquidslr/system-design-notes/tree/main/18.%20Google%20Maps','Geo services','Break down map delivery, route planning, traffic signals, and frequently changing geographic data.','Design a route-planning request and map-tile delivery path. Identify which data can be cached, how live traffic changes routes, and how to handle partial regional outages.',50,ARRAY['Geo','Routing','Caching'],'CASE_STUDY'),
('chapter-19-message-queue','Design a distributed message queue','https://github.com/liquidslr/system-design-notes/tree/main/19.%20Distributed%20Message%20Queue','Async processing','Define message persistence, consumer progress, delivery guarantees, ordering, and back pressure.','Specify publish and consume APIs. Explain partitioning, offsets, retention, ordering boundaries, retries, and how producers respond when consumers fall behind.',45,ARRAY['Queues','Async processing','Reliability'],'CASE_STUDY'),
('chapter-20-monitoring-alerting','Design a metrics monitoring and alerting system','https://github.com/liquidslr/system-design-notes/tree/main/20.%20Metrics%20Monitoring%20and%20Alerting%20System','Observability','Plan metric ingestion, aggregation, query, and alert evaluation under high-cardinality constraints.','Define ingestion and query workloads. Explain sampling or aggregation, cardinality controls, retention tiers, alert evaluation delay, and what happens during an ingestion spike.',45,ARRAY['Observability','Data','Reliability'],'CASE_STUDY'),
('chapter-21-ad-click-aggregation','Design ad-click event aggregation','https://github.com/liquidslr/system-design-notes/tree/main/21.%20Ad%20Click%20Event%20Aggregation','Data systems','Aggregate high-volume events with deduplication and clear treatment of late or repeated events.','Design the event path from click collection to reporting. Explain deduplication keys, event-time windows, late arrivals, aggregation accuracy, and replay after a processing failure.',45,ARRAY['Event processing','Data','Async processing'],'CASE_STUDY'),
('chapter-22-hotel-reservation','Design a hotel reservation system','https://github.com/liquidslr/system-design-notes/tree/main/22.%20Hotel%20Reservation%20System','Reservations','Manage room availability and booking confirmation while preventing conflicting reservations.','Model inventory, holds, booking confirmation, and cancellation. Explain how concurrent requests avoid double booking and how an expired hold returns a room to inventory.',45,ARRAY['Reservations','Transactions','Reliability'],'CASE_STUDY'),
('chapter-23-email-service','Design a distributed email service','https://github.com/liquidslr/system-design-notes/tree/main/23.%20Distributed%20Email%20Service','Communication','Explore message submission, delivery queues, mailbox storage, and search across distributed email data.','Design send and mailbox-read flows. Explain delivery retries, idempotency, mailbox partitioning, indexing, and how you protect the service from spam or abusive senders.',50,ARRAY['Email','Queues','Storage'],'CASE_STUDY'),
('chapter-24-object-storage','Design S3-like object storage','https://github.com/liquidslr/system-design-notes/tree/main/24.%20S3-like%20Object%20Storage','Storage','Consider object upload, retrieval, durability, metadata, and lifecycle management.','Define object APIs and storage guarantees. Explain multipart upload, replication or erasure coding, metadata lookup, integrity checks, and lifecycle deletion.',50,ARRAY['Object storage','Reliability','Data'],'CASE_STUDY'),
('chapter-25-gaming-leaderboard','Design a real-time gaming leaderboard','https://github.com/liquidslr/system-design-notes/tree/main/25.%20Real-time%20Gaming%20Leaderboard','Real-time systems','Maintain ranked scores and serve top lists and player-relative ranks under frequent updates.','Design score submission and leaderboard queries. Explain ranking ties, update throughput, time windows, sharding, and how a player can retrieve their rank without scanning all scores.',45,ARRAY['Leaderboards','Real-time systems','Data'],'CASE_STUDY'),
('chapter-26-payment-system','Design a payment system','https://github.com/liquidslr/system-design-notes/tree/main/26.%20Payment%20System','Payments','Track payment intent and outcome across retries and external provider uncertainty.','Model payment states and provider interactions. Explain idempotency, uncertain outcomes, reconciliation, auditability, and how you prevent duplicate charges.',50,ARRAY['Payments','Transactions','Reliability'],'CASE_STUDY'),
('chapter-27-digital-wallet','Design a digital wallet','https://github.com/liquidslr/system-design-notes/tree/main/27.%20Digital%20Wallet','Payments','Represent balances and transfers with strong accounting invariants and recoverable transaction flows.','Define wallet and ledger operations. State the balance invariant, explain concurrent transfers, idempotent retries, and how a ledger supports reconciliation.',50,ARRAY['Wallets','Transactions','Data'],'CASE_STUDY'),
('chapter-28-stock-exchange','Design a stock exchange','https://github.com/liquidslr/system-design-notes/tree/main/28.%20Stock%20Exchange','Trading systems','Reason about order submission, matching, market data, and audit requirements for a trading system.','Model order entry and matching at a high level. Explain ordering guarantees, price-time priority, cancellations, market-data fan-out, and the audit trail needed to reconstruct outcomes.',50,ARRAY['Trading','Ordering','Reliability'],'CASE_STUDY');

INSERT INTO topic(name, slug, provenance)
SELECT DISTINCT topic_name,
       lower(regexp_replace(topic_name, '[^a-zA-Z0-9]+', '-', 'g')),
       'SOURCE'
FROM liquidslr_seed s CROSS JOIN LATERAL unnest(s.topics) topic_name
ON CONFLICT (name) DO NOTHING;

INSERT INTO content_item(track, kind, canonical_title, canonical_url)
SELECT 'SYSTEM_DESIGN', kind, title, url FROM liquidslr_seed
ON CONFLICT (canonical_url) DO NOTHING;

INSERT INTO source_item(source_id, external_key, content_item_id, raw_metadata_json, active, last_seen_revision_id)
SELECT src.id, s.external_key, ci.id,
       jsonb_build_object(
           'category',s.category,
           'summary',s.summary,
           'prompt',s.prompt,
           'estimatedMinutes',s.estimated_minutes,
           'format',s.kind,
           'attribution','liquidslr/system-design-notes',
           'integrationMode','link-only',
           'contentCopied',false
       ),
       true, rev.id
FROM liquidslr_seed s
JOIN source src ON src.slug='liquidslr-system-design-notes'
JOIN source_revision rev ON rev.source_id=src.id AND rev.revision_ref='curated-link-only-2026-11-10'
JOIN content_item ci ON ci.canonical_url=s.url
ON CONFLICT (source_id, external_key) DO UPDATE
SET raw_metadata_json=EXCLUDED.raw_metadata_json,
    active=true,
    last_seen_revision_id=EXCLUDED.last_seen_revision_id;

INSERT INTO content_topic(content_item_id, topic_id)
SELECT DISTINCT ci.id, t.id
FROM liquidslr_seed s
JOIN content_item ci ON ci.canonical_url=s.url
CROSS JOIN LATERAL unnest(s.topics) topic_name
JOIN topic t ON t.name=topic_name
ON CONFLICT DO NOTHING;
