-- 產業地圖核心節點：產品與零件合併為單一遞迴實體（design D1），節點為品類而非型號（design D2）。
-- normalized_name 為全站唯一鍵，是「同一個東西只有一個節點」的硬性防線（design D9）。
-- parent_category_id 表達 is-a 細分類型（design D4），與 part-of 組成關係分開儲存。
CREATE TABLE item (
    id                 BIGSERIAL     PRIMARY KEY,
    normalized_name    VARCHAR(255)  NOT NULL,
    display_name       VARCHAR(255)  NOT NULL,
    is_end_product     BOOLEAN       NOT NULL DEFAULT FALSE,
    parent_category_id BIGINT,

    -- 來源與審核欄位群（design D8），對應 ProvenanceEntity
    source_type        VARCHAR(32)   NOT NULL,
    source_detail      VARCHAR(512),
    confidence         NUMERIC(4, 3),
    review_status      VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by        VARCHAR(128),
    reviewed_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_item_normalized_name UNIQUE (normalized_name),
    -- is-a 為樹狀：單一欄位即保證至多一個上層品類，且不得指向自身
    CONSTRAINT fk_item_parent_category FOREIGN KEY (parent_category_id) REFERENCES item (id),
    CONSTRAINT ck_item_parent_category_not_self CHECK (parent_category_id IS NULL OR parent_category_id <> id),
    CONSTRAINT ck_item_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_item_parent_category ON item (parent_category_id);
CREATE INDEX idx_item_review_status ON item (review_status);
