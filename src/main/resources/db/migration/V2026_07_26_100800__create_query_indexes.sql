-- 查詢用索引。組成關係的 unique(parent_item_id, child_item_id) 已可服務「由上層查下層」，
-- 但反向查詢（由零件回溯終端成品）以 child 為進入點，需要獨立索引。
CREATE INDEX idx_item_composition_child ON item_composition (child_item_id);

-- 市佔率排名查詢固定帶 item + 期間 + 地區 + 口徑四個維度。
CREATE INDEX idx_market_share_ranking ON market_share (item_id, period_value, region, metric);
