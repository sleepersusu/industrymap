-- 圖片上的可點擊區域（design D2、D5）：綁一張圖、指向一個品類節點，
-- 並帶位置標籤（前煞車／後煞車）與 0–1 相對比例的多邊形座標。
--
-- 唯一鍵刻意不含 child_item_id（design D3）：同一張圖上可以有兩個位置標籤不同的熱區指向
-- 同一個節點（這正是位置標籤存在的理由），但同一個位置標籤不該同時是煞車又是變速器。
--
-- position_label 非空由 CHECK 守住：PostgreSQL 的 UNIQUE 視 NULL 互不相等，
-- 允許為空等同唯一鍵失效，同一張圖可以堆進無限多筆指向同一節點的熱區。
--
-- polygon 的點數與座標範圍無法以 DB constraint 表達（JSONB），只能靠 payload annotation
-- 與 service 擋；繞過 service 直接寫 DB 就會失效，與既有的循環偵測、別名去重是同一類取捨。
CREATE TABLE item_hotspot (
    id             BIGSERIAL     PRIMARY KEY,
    item_image_id  BIGINT        NOT NULL,
    child_item_id  BIGINT        NOT NULL,
    position_label VARCHAR(64)   NOT NULL,
    polygon        JSONB         NOT NULL,

    source_type    VARCHAR(32)   NOT NULL,
    source_detail  VARCHAR(512),
    confidence     NUMERIC(4, 3),
    review_status  VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by    VARCHAR(128),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_item_hotspot UNIQUE (item_image_id, position_label),
    CONSTRAINT fk_item_hotspot_image FOREIGN KEY (item_image_id) REFERENCES item_image (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_hotspot_child_item FOREIGN KEY (child_item_id) REFERENCES item (id) ON DELETE CASCADE,
    CONSTRAINT ck_item_hotspot_position_label_not_blank CHECK (btrim(position_label) <> ''),
    CONSTRAINT ck_item_hotspot_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

-- 熱區指向的節點被刪除時要沿 FK 級聯，沒有索引會退化成整表掃描；
-- uk_item_hotspot 只服務得到 item_image_id 這一側。
CREATE INDEX idx_item_hotspot_child_item ON item_hotspot (child_item_id);
