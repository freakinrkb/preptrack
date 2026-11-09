CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_account_email_unique UNIQUE (email)
);

CREATE TABLE user_preference (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    show_difficulty BOOLEAN NOT NULL DEFAULT false,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    theme VARCHAR(16) NOT NULL DEFAULT 'dark' CHECK (theme IN ('dark', 'light', 'system')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX invite_email_pending_idx ON invite (lower(email), expires_at) WHERE accepted_at IS NULL;

CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES user_account(id) ON DELETE SET NULL,
    event_type VARCHAR(48) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX audit_event_occurred_idx ON audit_event (occurred_at DESC);
