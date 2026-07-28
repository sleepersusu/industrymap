## Why

產業地圖目前只能**從產品側進入**：`GET /api/products` 列出終端成品，往下展開零件，再看每個零件的供應公司。
但反方向走不通——`GET /api/companies/{code}` 必須先知道代號，而沒有任何端點能列出或搜尋公司。

資料庫已有 52 家公司、85 筆供應角色，「桂盟做哪些東西」「台灣有哪些公司在做 PCB」
這類從公司出發的問題，資料都在，只是沒有入口。對產業地圖而言，從公司反查是與從產品展開
同等自然的用法。

此缺口在 `docs/data-loading-playbook.md` 第八節已記為 G1 的一部分；品類節點側已由
`add-item-listing-and-amendment` 補上列表端點，公司側尚未。**前端即將開工，導覽結構取決於
有沒有這個入口——事後補會需要改動已成形的資訊架構，因此先補齊。**

## What Changes

- 新增 `GET /api/companies` 公司列表端點，比照 `GET /api/products` 的既有慣例：
  - 分頁（`page` / `size`，`size` 上限 100）
  - 名稱關鍵字模糊搜尋，正規化後做包含比對，且 MUST 同時比對別名
  - 支援 `includeDrafts`；已駁回資料任何條件下都不外露
  - 回應沿用 `PageResponse<CompanyResponse>`，含總筆數與總頁數
- 支援依國別與是否公開發行過濾，讓「台灣有哪些公司在做這個」問得出來
- 支援依供應的零件過濾，回答「這個零件有哪些公司在做」時可跨產品彙總

本次不含：公司的修改與刪除、從公司展開其所有供應零件的樹狀視圖、前端。

## Capabilities

### Modified Capabilities
- `company-registry`: 新增公司列表與搜尋的查詢能力（既有的代號查詢與識別碼規則不變）

## Impact

- **新增程式**：`payloads/company/CompanyQuery`、`CompanyController` 的列表端點、
  `CompanyService` 與 `CompanyRepository` 的查詢方法
- **既有程式**：沿用 `PageResponse`、`ReviewScopes` 與 `CompanyResponse`，不改動既有端點行為
- **資料庫**：無 schema 變更；名稱關鍵字查詢走既有的正規化名稱欄位，
  依零件過濾走既有的 `company_item_role`，必要時評估補索引
- **API**：`/api/companies` 新增 GET；既有的 `POST /api/companies` 與
  `GET /api/companies/{code}` 不受影響
- **文件**：`.claude/rules/api-design.md` 的 base path 表需補上公司列表為第一階段端點；
  `docs/data-loading-playbook.md` 第八節 G1 可據此更新
