## Why

產業地圖的查詢每回一筆資料就多送一次 SQL。實測（真實 PostgreSQL、扇出 20 筆）：

| 查詢 | 現況 SQL 筆數 |
|---|---|
| `GET /api/items/{id}/compositions` | 23 |
| `GET /api/products/{id}/components`（depth=1） | 22 |
| `GET /api/items/{id}/end-products` | 42 |
| `GET /api/items/{id}/suppliers` | 23 |
| `GET /api/items/{id}/market-share` | 23 |

根因是 `Item` / `Company` 的 `@Id` 標在**欄位**上，Hibernate 無法在代理上攔截 identifier getter——
**連讀主鍵都會觸發初始化**。因此 `CompositionResponse.from` 的 `getParentItem().getId()`、
`SupplierResponse.from` 的 `getCompany().getDisplayName()` 都是逐筆各一次 SELECT。
`CompanyIdentifierRepository` 早就為同一個成因寫明理由並用 `@EntityGraph` 解掉，但只解了那一支。

`findReachableEndProducts` 的 42 筆是**兩種**問題疊加：逐筆祖先載入，加上 BFS 迴圈每 pop 一個節點
就查一次邊。後者與 lazy loading 無關，只有把走訪本身交給資料庫才治得掉。

這是靜默劣化：資料量小時看不出來，且沒有任何東西會在它長回來時攔住。

## What Changes

- 開啟 Hibernate 的批次抓取（`default_batch_fetch_size`），讓代理初始化由逐筆變成一次 `IN` 查詢。
  這是唯一能治到 `MarketShareRepository.findRanking` 的手段——它是 native query，`@EntityGraph` 對其無效
- 熱路徑的四支衍生查詢改以 `@EntityGraph` 明示要一併載入的關聯，不依賴全域設定
- `findReachableEndProducts` 的向上走訪由 Java BFS 改為單支遞迴 CTE，走訪次數與圖的大小脫鉤
- **新增查詢筆數回歸測試**：對外查詢的 SQL 筆數不得隨結果筆數成長，長回來就讓 build 紅

## Capabilities

### Added Capabilities
- `query-performance`: 對外查詢的 SQL 筆數與結果筆數脫鉤，並要求以可執行的驗證守住

### Modified Capabilities
- `industry-map-model`: 反向查詢終端成品的走訪語意改由資料庫執行，語意本身不變但需明文載明
  （已駁回的節點不列入**且不經由它續走**），否則改寫時會靜默丟失

## Impact

- **設定**：`application.properties` 新增一行批次抓取設定，影響全域 lazy 關聯載入行為
- **程式**：`ItemCompositionRepository`（2 支加 `@EntityGraph`、1 支新增 CTE）、
  `CompanyItemRoleRepository`（2 支加 `@EntityGraph`）、`ItemCompositionService.findReachableEndProducts`
- **測試搬遷**：`ItemCompositionServiceTest` 中 3 支 `findReachableEndProducts` 測試以 mock repository 驗證，
  邏輯移進 SQL 後它們不只會壞，而且**再也測不到任何邏輯**。必須改寫為真實 PostgreSQL 的整合測試。
  其中一支目前用 `verify(..., never())` 斷言「不經由已駁回祖先續走」，該行為要改以結果表達
- **無 schema 變更、無對外 API 變更、回傳形狀不變**
- **CHANGELOG**：查詢筆數是效能而非使用者可感知的行為變化，原則上不記；
  若實測回應時間有明顯改善再評估
