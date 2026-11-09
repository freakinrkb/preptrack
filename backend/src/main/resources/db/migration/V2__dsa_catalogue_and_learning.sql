CREATE TABLE source (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(80) NOT NULL UNIQUE,
    canonical_url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO source(slug, canonical_url) VALUES
('leetcode-company-wise-problems', 'https://github.com/liquidslr/leetcode-company-wise-problems');

CREATE TABLE source_revision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES source(id),
    revision_ref VARCHAR(128) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PREVIEWED','APPLIED','FAILED','UNCHANGED')),
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE(source_id, revision_ref)
);

CREATE TABLE content_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    track VARCHAR(24) NOT NULL DEFAULT 'DSA' CHECK (track IN ('DSA','SYSTEM_DESIGN','LLD')),
    kind VARCHAR(24) NOT NULL DEFAULT 'QUESTION',
    canonical_title VARCHAR(300) NOT NULL,
    canonical_url TEXT NOT NULL UNIQUE,
    lifecycle VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle IN ('ACTIVE','ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE source_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES source(id),
    external_key VARCHAR(500) NOT NULL,
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    raw_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT true,
    last_seen_revision_id UUID REFERENCES source_revision(id),
    UNIQUE(source_id, external_key)
);

CREATE TABLE company (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL UNIQUE,
    slug VARCHAR(180) NOT NULL UNIQUE
);

CREATE TABLE topic (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    provenance VARCHAR(24) NOT NULL DEFAULT 'SOURCE'
);

CREATE TABLE content_topic (
    content_item_id UUID NOT NULL REFERENCES content_item(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES topic(id),
    PRIMARY KEY(content_item_id, topic_id)
);
CREATE TABLE content_company (
    content_item_id UUID NOT NULL REFERENCES content_item(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES company(id),
    evidence_source_item_id UUID NOT NULL REFERENCES source_item(id),
    PRIMARY KEY(content_item_id, company_id)
);

CREATE TABLE learning_state (
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED' CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    completed_at TIMESTAMPTZ,
    latest_attempt_at TIMESTAMPTZ,
    next_review_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id, content_item_id)
);

CREATE TABLE learning_attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('COMPLETED','IN_PROGRESS')),
    independence_rating SMALLINT CHECK (independence_rating BETWEEN 1 AND 5),
    note VARCHAR(2000),
    CONSTRAINT attempt_rating_on_completion CHECK (outcome <> 'COMPLETED' OR independence_rating IS NOT NULL)
);
CREATE INDEX learning_attempt_user_recent_idx ON learning_attempt(user_id, occurred_at DESC);

CREATE TABLE bookmark (
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id, content_item_id)
);
