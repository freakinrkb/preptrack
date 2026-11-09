CREATE TABLE catalogue_correction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_item_id UUID NOT NULL REFERENCES content_item(id),
    actor_user_id UUID NOT NULL REFERENCES user_account(id),
    field_name VARCHAR(24) NOT NULL CHECK (field_name IN ('TITLE')),
    previous_value VARCHAR(300) NOT NULL,
    corrected_value VARCHAR(300) NOT NULL,
    evidence_url TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUPERSEDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX catalogue_correction_active_idx ON catalogue_correction(content_item_id,field_name,created_at DESC) WHERE status='ACTIVE';
CREATE INDEX catalogue_correction_history_idx ON catalogue_correction(created_at DESC);

CREATE VIEW catalogue_item AS
SELECT ci.id,ci.track,ci.kind,
       coalesce(c.corrected_value,ci.canonical_title) AS display_title,
       ci.canonical_title,ci.canonical_url,ci.lifecycle,ci.created_at,ci.updated_at
FROM content_item ci
LEFT JOIN LATERAL (
    SELECT corrected_value FROM catalogue_correction cc
    WHERE cc.content_item_id=ci.id AND cc.field_name='TITLE' AND cc.status='ACTIVE'
    ORDER BY cc.created_at DESC LIMIT 1
) c ON true;
