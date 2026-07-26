-- 公司對零件扮演的角色（design D6）：同一顆晶片可能聯發科設計、台積電製造、日月光封測，
-- 只有一條「做這個零件」的關係會讓三家長得一樣。
-- 唯一鍵含角色，因此同一公司對同一零件可有多個角色（台積電既製造又封測）。
CREATE TABLE company_item_role (
    id            BIGSERIAL     PRIMARY KEY,
    company_id    BIGINT        NOT NULL,
    item_id       BIGINT        NOT NULL,
    company_role  VARCHAR(32)   NOT NULL,

    source_type   VARCHAR(32)   NOT NULL,
    source_detail VARCHAR(512),
    confidence    NUMERIC(4, 3),
    review_status VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by   VARCHAR(128),
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_company_item_role UNIQUE (company_id, item_id, company_role),
    CONSTRAINT fk_company_item_role_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_company_item_role_item FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT ck_company_item_role_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_company_item_role_item ON company_item_role (item_id);
