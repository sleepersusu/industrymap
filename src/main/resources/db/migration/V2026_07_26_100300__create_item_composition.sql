-- part-of 組成關係（design D3）：多對多，節點全站共用，不為了掛在不同上層而複製節點。
-- necessity 非空——品類層的組成並非總是成立（單速腳踏車沒有變速器）。
-- 循環偵測放在 service 層（design D10），PostgreSQL 無原生 DAG 約束；
-- 這裡只以 check 擋住最明顯的自我循環。
CREATE TABLE item_composition (
    id             BIGSERIAL     PRIMARY KEY,
    parent_item_id BIGINT        NOT NULL,
    child_item_id  BIGINT        NOT NULL,
    necessity      VARCHAR(32)   NOT NULL,

    source_type    VARCHAR(32)   NOT NULL,
    source_detail  VARCHAR(512),
    confidence     NUMERIC(4, 3),
    review_status  VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by    VARCHAR(128),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_item_composition UNIQUE (parent_item_id, child_item_id),
    CONSTRAINT fk_item_composition_parent FOREIGN KEY (parent_item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_composition_child FOREIGN KEY (child_item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT ck_item_composition_no_self_loop CHECK (parent_item_id <> child_item_id),
    CONSTRAINT ck_item_composition_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
