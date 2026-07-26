-- 公司主檔與同義詞。未上市公司（例：SRAM）必須能正常建立，
-- 因此代號一律不放在本表，改由 company_identifier 承載（design D5）。
CREATE TABLE company (
    id              BIGSERIAL     PRIMARY KEY,
    normalized_name VARCHAR(255)  NOT NULL,
    display_name    VARCHAR(255)  NOT NULL,
    country         VARCHAR(64),
    is_public       BOOLEAN       NOT NULL DEFAULT FALSE,

    source_type     VARCHAR(32)   NOT NULL,
    source_detail   VARCHAR(512),
    confidence      NUMERIC(4, 3),
    review_status   VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by     VARCHAR(128),
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_company_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_company_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_company_review_status ON company (review_status);

-- 公司同義詞：台積電／TSMC／台灣積體電路製造股份有限公司。
-- 與 item_alias 相同，跨表衝突（別名撞到其他公司名稱）由 service 層把關。
CREATE TABLE company_alias (
    id               BIGSERIAL     PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    normalized_alias VARCHAR(255)  NOT NULL,
    display_alias    VARCHAR(255)  NOT NULL,

    source_type      VARCHAR(32)   NOT NULL,
    source_detail    VARCHAR(512),
    confidence       NUMERIC(4, 3),
    review_status    VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by      VARCHAR(128),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_company_alias_normalized UNIQUE (normalized_alias),
    CONSTRAINT fk_company_alias_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT ck_company_alias_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_company_alias_company ON company_alias (company_id);
