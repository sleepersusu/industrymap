-- 市佔率（design D7）：隨期間、地區、口徑而完全不同，塞進關係表當單一欄位明年就是錯的。
-- 刻意不對 company_item_role 加外鍵——市佔率資料常先於角色關係到達（報告先說 Shimano 七成），
-- 強制關聯會卡住匯入。
CREATE TABLE market_share (
    id            BIGSERIAL     PRIMARY KEY,
    company_id    BIGINT        NOT NULL,
    item_id       BIGINT        NOT NULL,
    period_type   VARCHAR(16)   NOT NULL,
    period_value  VARCHAR(16)   NOT NULL,
    region        VARCHAR(64)   NOT NULL,
    metric        VARCHAR(16)   NOT NULL,
    share_percent NUMERIC(6, 3) NOT NULL,

    source_type   VARCHAR(32)   NOT NULL,
    source_detail VARCHAR(512),
    confidence    NUMERIC(4, 3),
    review_status VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by   VARCHAR(128),
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_market_share_company FOREIGN KEY (company_id) REFERENCES company (id) ON DELETE CASCADE,
    CONSTRAINT fk_market_share_item FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT ck_market_share_percent_range CHECK (share_percent >= 0 AND share_percent <= 100),
    CONSTRAINT ck_market_share_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

-- 唯一鍵含來源：讓「來源 A 說 70%、來源 B 說 50%」兩筆並存，由使用者判斷；同一來源重複才算衝突。
-- 以 COALESCE 包住 source_detail，否則 NULL 在 PostgreSQL 的 UNIQUE 中互不相等，同來源重複會擋不住。
CREATE UNIQUE INDEX uk_market_share_dimensions_source
    ON market_share (company_id, item_id, period_type, period_value, region, metric, COALESCE(source_detail, ''));
