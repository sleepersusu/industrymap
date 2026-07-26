-- 零件同義詞（design D9）：WiFi 模組／無線網卡／WLAN Module 指向同一節點。
-- normalized_alias 全域唯一，避免同一別名被掛到兩個節點。
-- 「別名不得與其他 item 的 normalized_name 衝突」屬跨表限制，PostgreSQL 無法以單一 constraint 表達，
-- 由 service 層於寫入前比對（見 ItemAliasService），DB 僅守住別名自身的唯一性。
CREATE TABLE item_alias (
    id               BIGSERIAL     PRIMARY KEY,
    item_id          BIGINT        NOT NULL,
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

    CONSTRAINT uk_item_alias_normalized UNIQUE (normalized_alias),
    CONSTRAINT fk_item_alias_item FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT ck_item_alias_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_item_alias_item ON item_alias (item_id);
