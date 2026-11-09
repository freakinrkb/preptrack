-- Phase 3: source-linked study items plus private, structured design workspaces.
CREATE TABLE design_workspace (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    track VARCHAR(24) NOT NULL CHECK (track IN ('SYSTEM_DESIGN','LLD')),
    response_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(response_json) = 'object'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    UNIQUE(user_id, content_item_id)
);
CREATE INDEX design_workspace_user_updated_idx ON design_workspace(user_id, updated_at DESC);

INSERT INTO source(slug, canonical_url) VALUES
    ('system-design-primer', 'https://github.com/donnemartin/system-design-primer'),
    ('the-weekend-dev-lld', 'https://theweekenddev.com/lld/')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO source_revision(source_id, revision_ref, status, summary)
SELECT id, 'curated-v1-2026-11-08', 'APPLIED', '{"method":"link-first-curated-starter"}'::jsonb
FROM source WHERE slug IN ('system-design-primer','the-weekend-dev-lld')
ON CONFLICT (source_id, revision_ref) DO NOTHING;

CREATE TEMP TABLE phase3_seed (
    source_slug VARCHAR(80) NOT NULL,
    external_key VARCHAR(500) NOT NULL,
    track VARCHAR(24) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    title VARCHAR(300) NOT NULL,
    url TEXT NOT NULL,
    category VARCHAR(80) NOT NULL,
    summary TEXT NOT NULL,
    prompt TEXT NOT NULL,
    estimated_minutes INTEGER NOT NULL,
    topics TEXT[] NOT NULL
) ON COMMIT DROP;

INSERT INTO phase3_seed VALUES
('system-design-primer','primer:performance-vs-scalability','SYSTEM_DESIGN','CONCEPT','Performance vs scalability','https://github.com/donnemartin/system-design-primer/blob/master/README.md#performance-vs-scalability','Foundations','Compare how a system behaves under a fixed workload with how its capacity grows as demand increases.','Explain one design that improves latency and one that increases capacity. State the workload assumptions behind each choice.',15,ARRAY['Foundations']),
('system-design-primer','primer:latency-vs-throughput','SYSTEM_DESIGN','CONCEPT','Latency vs throughput','https://github.com/donnemartin/system-design-primer/blob/master/README.md#latency-vs-throughput','Foundations','Separate the time an individual request takes from the amount of work completed over time.','Describe a workload where higher throughput can coexist with worse tail latency. Name a metric you would watch.',15,ARRAY['Foundations']),
('system-design-primer','primer:availability-vs-consistency','SYSTEM_DESIGN','CONCEPT','Availability vs consistency','https://github.com/donnemartin/system-design-primer/blob/master/README.md#availability-vs-consistency','Reliability','Explore how distributed systems behave when communication between replicas is delayed or interrupted.','Pick a user action and explain what stale data or temporary unavailability would mean for it.',20,ARRAY['Reliability','Distributed systems']),
('system-design-primer','primer:cap-theorem','SYSTEM_DESIGN','CONCEPT','CAP theorem','https://github.com/donnemartin/system-design-primer/blob/master/README.md#cap-theorem','Reliability','Use partitions as the failure condition when reasoning about consistency and availability guarantees.','Choose a partition scenario and state which guarantee your service preserves for the operation you selected.',20,ARRAY['Reliability','Distributed systems']),
('system-design-primer','primer:content-delivery-network','SYSTEM_DESIGN','CONCEPT','Content delivery networks','https://github.com/donnemartin/system-design-primer/blob/master/README.md#content-delivery-network','Networking','Study how geographically distributed caches can reduce distance between users and static content.','Identify which responses are safe to cache, how freshness is controlled, and what happens on a cache miss.',20,ARRAY['Networking','Caching']),
('system-design-primer','primer:load-balancer','SYSTEM_DESIGN','CONCEPT','Load balancing','https://github.com/donnemartin/system-design-primer/blob/master/README.md#load-balancer','Traffic management','Compare ways to distribute incoming requests and detect unhealthy destinations.','Describe the health signal and routing behavior you would choose for a service with uneven request costs.',20,ARRAY['Networking','Reliability']),
('system-design-primer','primer:sql-or-nosql','SYSTEM_DESIGN','CONCEPT','SQL or NoSQL','https://github.com/donnemartin/system-design-primer/blob/master/README.md#sql-or-nosql','Data','Compare data models and operational properties against concrete access patterns and invariants.','Write two important queries and one consistency invariant, then justify a storage model for them.',20,ARRAY['Data']),
('system-design-primer','primer:cache','SYSTEM_DESIGN','CONCEPT','Caching strategies','https://github.com/donnemartin/system-design-primer/blob/master/README.md#cache','Data','Review cache placement, invalidation, expiry, and the consistency trade-offs each policy creates.','Choose a cache-aside or write-through flow and walk through what happens when the underlying record changes.',20,ARRAY['Caching','Data']),
('system-design-primer','primer:message-queues','SYSTEM_DESIGN','CONCEPT','Message queues and back pressure','https://github.com/donnemartin/system-design-primer/blob/master/README.md#message-queues','Async processing','Use queues to decouple work while making delivery, retries, ordering, and overload behavior explicit.','Describe a retry policy that avoids duplicate side effects and explain how consumers handle growing lag.',20,ARRAY['Async processing','Reliability']),
('system-design-primer','primer:interview-approach','SYSTEM_DESIGN','EXERCISE','System design interview walkthrough','https://github.com/donnemartin/system-design-primer/blob/master/README.md#how-to-approach-a-system-design-interview-question','Interview practice','Practice moving from user requirements to a high-level design, then examine scale and failure trade-offs.','Choose a familiar product. Time-box the exercise: clarify requirements, estimate demand, sketch components, and identify the first bottleneck.',35,ARRAY['Interview practice']),
('the-weekend-dev-lld','weekenddev:whatsapp','LLD','CASE_STUDY','Design a messaging application','https://theweekenddev.com/lld/designing-whatsapp-low-level-design/','Communication','Model direct and group conversations, message history, membership, and delivery state.','Design a messaging domain that supports direct and group conversations. Define who owns message state and how membership changes affect future messages.',45,ARRAY['Domain modeling','State and behavior']),
('the-weekend-dev-lld','weekenddev:api-gateway','LLD','CASE_STUDY','Design an API gateway','https://theweekenddev.com/lld/api-gateway-lld/','Infrastructure','Explore route selection and a composable request-processing pipeline with clear failure behavior.','Design a gateway with route registration and ordered request checks. Explain how a rejected request stops the remaining pipeline.',45,ARRAY['Pipelines','Extensibility']),
('the-weekend-dev-lld','weekenddev:circuit-breaker','LLD','CASE_STUDY','Design a circuit breaker','https://theweekenddev.com/lld/design-a-circuit-breaker/','Resilience','Model a component that changes how calls behave after repeated dependency failures.','Define the states and transitions for a dependency guard. Explain how concurrent calls affect state changes and recovery probes.',45,ARRAY['State and behavior','Concurrency']),
('the-weekend-dev-lld','weekenddev:payment-gateway','LLD','CASE_STUDY','Design a payment gateway','https://theweekenddev.com/lld/design-a-payment-gateway-aggregator/','Payments','Compare payment providers while keeping retries, idempotency, and outcome reporting explicit.','Design provider selection and payment lifecycle handling. Explain how retries avoid charging twice and how uncertain outcomes are reconciled.',50,ARRAY['Strategy','Reliability']),
('the-weekend-dev-lld','weekenddev:load-balancer','LLD','CASE_STUDY','Design a load balancer','https://theweekenddev.com/lld/design-a-load-balancer/','Infrastructure','Model backend membership, health, and a policy for selecting a destination for each request.','Choose a routing policy and model how unhealthy backends are removed and later reintroduced.',45,ARRAY['Strategy','Reliability']);

INSERT INTO topic(name, slug, provenance)
SELECT DISTINCT t, lower(regexp_replace(t, '[^a-zA-Z0-9]+', '-', 'g')), 'SOURCE'
FROM phase3_seed s CROSS JOIN LATERAL unnest(s.topics) t
ON CONFLICT (name) DO NOTHING;

INSERT INTO content_item(track, kind, canonical_title, canonical_url)
SELECT DISTINCT track, kind, title, url FROM phase3_seed
ON CONFLICT (canonical_url) DO NOTHING;

INSERT INTO source_item(source_id, external_key, content_item_id, raw_metadata_json, active, last_seen_revision_id)
SELECT src.id, s.external_key, ci.id,
       jsonb_build_object('category',s.category,'summary',s.summary,'prompt',s.prompt,
                          'estimatedMinutes',s.estimated_minutes,'format',s.kind),
       true, rev.id
FROM phase3_seed s
JOIN source src ON src.slug=s.source_slug
JOIN source_revision rev ON rev.source_id=src.id AND rev.revision_ref='curated-v1-2026-11-08'
JOIN content_item ci ON ci.canonical_url=s.url
ON CONFLICT (source_id, external_key) DO UPDATE
SET raw_metadata_json=EXCLUDED.raw_metadata_json, active=true, last_seen_revision_id=EXCLUDED.last_seen_revision_id;

INSERT INTO content_topic(content_item_id, topic_id)
SELECT DISTINCT ci.id, t.id
FROM phase3_seed s
JOIN content_item ci ON ci.canonical_url=s.url
CROSS JOIN LATERAL unnest(s.topics) topic_name
JOIN topic t ON t.name=topic_name
ON CONFLICT DO NOTHING;
