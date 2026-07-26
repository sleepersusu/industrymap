-- 公司代號（design D5）：類型不只一種、數量不只一個（台積電有 TWSE 2330 與 NYSE TSM），
-- 且可能一個都沒有，因此獨立成表而非塞在 company 的欄位上。
-- 欄位名加 identifier_ 前綴，避免 type / value 這類在 SQL 方言中易生歧義的字。
CREATE TABLE company_identifier (
    id               BIGSERIAL     PRIMARY KEY,
    company_id       BIGINT        NOT NULL,
    identifier_type  VARCHAR(32)   NOT NULL,
    identifier_value VARCHAR(64)   NOT NULL,
    is_primary       BOOLEAN       NOT NULL DEFAULT FALSE,

    source_type      VARCHAR(32)   NOT NULL,
    source_detail    VARCHAR(512),
    confidence       NUMERIC(4, 3),
    review_status    VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by      VARCHAR(128),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_company_identifier_type_value UNIQUE (identifier_type, identifier_value),
    CONSTRAINT fk_company_identifier_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT ck_company_identifier_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

-- 每家公司至多一筆主要識別碼：接股價時直接取這筆，明確知道要抓哪個市場。
-- partial unique index 才能表達「只對 is_primary = true 的列唯一」。
CREATE UNIQUE INDEX uk_company_identifier_primary ON company_identifier (company_id) WHERE is_primary;

CREATE INDEX idx_company_identifier_company ON company_identifier (company_id);
