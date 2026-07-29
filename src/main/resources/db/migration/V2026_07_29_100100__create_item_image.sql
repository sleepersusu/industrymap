-- 品類節點的圖片（design D4）：只存物件儲存的位置，二進位不進資料庫——
-- 圖片進 DB 之後備份與還原時間跟著膨脹、JPA 讀節點會順手把 blob 拉進記憶體、CDN 也服務不到。
-- 原圖尺寸留著供前端估算版面，不是驗證用的（座標一律是 0–1 相對比例，見 item_hotspot）。
CREATE TABLE item_image (
    id            BIGSERIAL     PRIMARY KEY,
    item_id       BIGINT        NOT NULL,
    view_label    VARCHAR(64)   NOT NULL,
    storage_key   VARCHAR(1024) NOT NULL,
    width_px      INTEGER,
    height_px     INTEGER,

    source_type   VARCHAR(32)   NOT NULL,
    source_detail VARCHAR(512),
    confidence    NUMERIC(4, 3),
    review_status VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    reviewed_by   VARCHAR(128),
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,

    -- 自然鍵：一個節點的「爆炸圖」「側視圖」各一張（design D3）
    CONSTRAINT uk_item_image UNIQUE (item_id, view_label),
    CONSTRAINT fk_item_image_item FOREIGN KEY (item_id) REFERENCES item (id) ON DELETE CASCADE,
    -- 視角標籤是自然鍵的一半，空白字串會讓同一節點堆出一串無從區分的圖
    CONSTRAINT ck_item_image_view_label_not_blank CHECK (btrim(view_label) <> ''),
    CONSTRAINT ck_item_image_storage_key_not_blank CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_item_image_dimensions_positive CHECK (
        (width_px IS NULL OR width_px > 0) AND (height_px IS NULL OR height_px > 0)),
    CONSTRAINT ck_item_image_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);
