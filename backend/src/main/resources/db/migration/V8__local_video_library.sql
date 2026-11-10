CREATE TABLE video_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    module VARCHAR(120) NOT NULL,
    description VARCHAR(1200) NOT NULL DEFAULT '',
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    storage_key VARCHAR(64) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('video/mp4','video/webm')),
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    duration_seconds BIGINT CHECK (duration_seconds > 0),
    sha256 CHAR(64) NOT NULL UNIQUE CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','ARCHIVED')),
    source_attribution VARCHAR(500) NOT NULL DEFAULT '',
    uploaded_by UUID NOT NULL REFERENCES user_account(id),
    rights_confirmed_by UUID NOT NULL REFERENCES user_account(id),
    rights_confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (storage_key ~ '^[0-9a-f-]{36}\.(mp4|webm)$')
);

CREATE INDEX video_asset_public_order_idx
    ON video_asset(module, sort_order, title) WHERE status='PUBLISHED';
CREATE INDEX video_asset_admin_recent_idx
    ON video_asset(created_at DESC);
