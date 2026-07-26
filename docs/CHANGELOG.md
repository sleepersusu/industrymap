# Changelog

本檔案記錄使用者可感知的行為變化。格式與規則見 `CLAUDE.md`「Changelog 規則」。

## [Unreleased]

### 新增

- (industry-map) 產業地圖核心資料模型上線：以品類節點串起「產品 → 零件 → 公司 → 市佔率」，
  產品與零件為同一種遞迴實體，零件可被多個上層產品共用 — 2026-07-26 · user · (pending)
- (item) 新增品類節點 API：建立節點、登記別名、以名稱或別名解析既有節點、建立 part-of 組成關係；
  組成關係於寫入前偵測循環，會造成循環者一律拒絕 — 2026-07-26 · user · (pending)
- (product) 新增 `GET /api/products/{id}/components` 展開組成樹，可指定展開層數與只取特定必要性
  （標配／常見／選配） — 2026-07-26 · user · (pending)
- (item) 新增反向查詢 `GET /api/items/{id}/end-products`：由零件回溯所有可達的終端成品 — 2026-07-26 · user · (pending)
- (company) 新增公司 API：建立公司（未上市公司可不帶任何代號）、登記別名與多重識別碼
  （交易所代號／統編／DUNS），並以公司代號而非內部主鍵作為路徑識別 — 2026-07-26 · user · (pending)
- (supply) 新增供應關係與市佔率：公司對零件可同時具備多個角色（設計／製造／代工組裝／品牌／封測）；
  市佔率必帶期間、地區、口徑，不同來源的衝突數值可並存並各自標示來源 — 2026-07-26 · user · (pending)
- (provenance) 所有內容資料都記錄來源與審核狀態：AI 生成資料必須帶信心度，新資料預設為草稿，
  對外查詢預設只回已驗證資料，已駁回資料一律不外露 — 2026-07-26 · user · (pending)

### 變更

- (config) 資料庫連線帳密改由專案根目錄的 `.env` 提供（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`），
  不再寫在 `application.properties`；`.env` 不進版控 — 2026-07-26 · user · (pending)
