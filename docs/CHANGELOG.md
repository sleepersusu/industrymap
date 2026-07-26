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
  對外查詢預設只回已驗證資料。已駁回資料一律不外露，且範圍涵蓋資料本身——被駁回的品類節點
  不會出現在組成樹與終端成品回溯中，被駁回的公司連同其供應關係與市佔率一併不外露 — 2026-07-26 · user · (pending)
- (review) 新增審核 API `POST /api/reviews`：可將任一內容資料在草稿／已驗證／已駁回之間流轉，
  轉為已驗證或已駁回需填審核者，退回草稿則清空審核紀錄。此前草稿資料無法轉為已驗證，
  等於寫進去的資料一律查不到 — 2026-07-26 · user · (pending)
- (review) 審核支援批次 `POST /api/reviews/batch`：單次可審多筆並跨不同資料類型，
  個別失敗不影響其他項目，逐筆回報成功或失敗原因 — 2026-07-26 · user · (pending)
- (review) 審核目標可改用自然鍵定位，不必先知道內部識別碼：公司識別碼用「類型 + 代號值」、
  組成關係用「上層 + 下層節點」、供應角色用「公司 + 零件 + 角色」、市佔率用完整維度組合。
  此前公司識別碼這一類的查詢回應完全不含識別碼，只能直接查資料庫才審得掉 — 2026-07-26 · user · (pending)
- (bulk) 新增批次建立端點 `/api/bulk/{items,compositions,companies,identifiers,supply-roles,market-shares}`：
  單次提交多筆，個別失敗不影響其他項目並逐筆回報原因；每筆成功項目回傳自然鍵，
  可直接轉成批次審核請求，建立到審核之間不需再查詢任何端點 — 2026-07-26 · user · (pending)
- (item) 新增 `GET /api/items/{id}/compositions` 查節點的組成關係，回傳上下層節點、必要性與審核狀態；
  組成樹回應只給節點識別碼，此前拿不到關係本身的定位資訊 — 2026-07-26 · user · (pending)

### 修正

- (company) 修正以公司代號查詢公司時回 500 的問題（`GET /api/companies/{代號}`）；
  以正規化名稱查詢的路徑不受影響 — 2026-07-26 · user · (pending)

### 變更

- (supply) **破壞性變更**：建立供應角色與寫入市佔率改以公司代號指定公司（`companyCode`），
  不再要求呼叫端提供內部識別碼（`companyId`）。此前建完公司後無從取得該識別碼，
  只能直接查資料庫才建得了供應關係 — 2026-07-26 · user · (pending)

- (company) **行為變更**：供應商與市佔率回應的 `companyReference` 改與 `GET /api/companies/{code}` 的
  `reference` 同一套規則——優先主要代號，公司無任何識別碼時才退回正規化名稱。此前同一家公司在
  兩處會拿到不同值，兩個值雖然都查得到公司，但呼叫端容易誤判成兩家 — 2026-07-26 · user · (pending)

- (config) 資料庫連線帳密改由專案根目錄的 `.env` 提供（`DB_URL` / `DB_USERNAME` / `DB_PASSWORD`），
  不再寫在 `application.properties`；`.env` 不進版控 — 2026-07-26 · user · (pending)
