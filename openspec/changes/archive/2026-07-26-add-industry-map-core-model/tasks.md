## 1. 共用基礎與 enum

- [x] 1.1 建立名稱正規化工具 `NameNormalizer`（大小寫、全半形、空白、標點統一），附單元測試涵蓋「WiFi模組 / wifi 模組 / ＷｉＦｉ模組」正規化為同一字串
- [x] 1.2 建立 enum：`SourceType`（AI_GENERATED / MANUAL / EXTERNAL）、`ReviewStatus`（DRAFT / VERIFIED / REJECTED）
- [x] 1.3 建立 enum：`CompanyRole`（DESIGN / MANUFACTURE / ASSEMBLY / BRAND / PACKAGING_TESTING）、`Necessity`（STANDARD / COMMON / OPTIONAL）
- [x] 1.4 建立 enum：`IdentifierType`（TWSE / TPEX / TSE / NASDAQ / NYSE / TAX_ID / DUNS / OTHER）、`ShareMetric`（REVENUE / VOLUME）、`PeriodType`（YEAR / QUARTER）
- [x] 1.5 建立 `@MappedSuperclass` 的 `ProvenanceEntity`，含 source_type、source_detail、confidence、review_status、reviewed_by、reviewed_at、created_at、updated_at

## 2. Flyway migration

- [x] 2.1 建立 migration：`item` 表（正規化名稱唯一鍵、display_name、is_end_product、parent_category_id 自我 FK）與 provenance 欄位群
- [x] 2.2 建立 migration：`item_alias` 表，alias 正規化後全域唯一，且與 `item` 正規化名稱不得衝突
- [x] 2.3 建立 migration：`item_composition` 表，`unique(parent_item_id, child_item_id)`，necessity 非空
- [x] 2.4 建立 migration：`company` 表（正規化名稱唯一鍵、display_name、country、is_public）與 `company_alias` 表
- [x] 2.5 建立 migration：`company_identifier` 表，`unique(type, value)`，並以 partial unique index 確保每公司至多一筆 is_primary
- [x] 2.6 建立 migration：`company_item_role` 表，`unique(company_id, item_id, role)`
- [x] 2.7 建立 migration：`market_share` 表，唯一鍵含來源：`unique(company_id, item_id, period_type, period_value, region, metric, source_detail)`；share_percent 加 0–100 check constraint
- [x] 2.8 建立查詢用索引：`item_composition(child_item_id)` 供反向查詢、`market_share(item_id, period_value, region, metric)` 供排名查詢

## 3. Entity 與 Repository

- [x] 3.1 建立 `Item` entity（含 parent_category 自我關聯）與 `ItemRepository`
- [x] 3.2 建立 `ItemAlias` entity 與 `ItemAliasRepository`，提供依正規化別名查 item 的方法
- [x] 3.3 建立 `ItemComposition` entity 與 `ItemCompositionRepository`，提供依 parent 查 children、依 child 查 parents
- [x] 3.4 建立 `Company`、`CompanyAlias` entity 與對應 Repository
- [x] 3.5 建立 `CompanyIdentifier` entity 與 Repository，提供依 (type, value) 查公司、依公司查主要識別碼
- [x] 3.6 建立 `CompanyItemRole` entity 與 Repository，提供依 item 查公司（可選角色過濾）
- [x] 3.7 建立 `MarketShare` entity 與 Repository，提供依 (item, period, region, metric) 取排名的查詢
- [x] 3.8 撰寫 Repository 層整合測試（`@Tag("integration")` + Testcontainer），驗證各唯一鍵與 check constraint 實際生效

## 4. Item 領域服務

- [x] 4.1 先寫失敗測試：建立 item 時正規化名稱重複應拋 409；再實作 `ItemService.create`
- [x] 4.2 先寫失敗測試：以別名查詢應命中既有 item；再實作別名解析（名稱與別名一併比對）
- [x] 4.3 先寫失敗測試：別名與其他 item 正規化名稱衝突應拋 409；再實作別名建立驗證
- [x] 4.4 先寫失敗測試：直接循環（A→B 後建 B→A）應拋 409；再實作 `ItemCompositionService` 的循環偵測
- [x] 4.5 先寫失敗測試：間接循環（A→B、B→C 後建 C→A）應拋 409；補強循環偵測至多層走訪
- [x] 4.6 先寫失敗測試：多重上層（A→C 後建 B→C）應成功；確認循環偵測未誤擋合法 DAG
- [x] 4.7 先寫失敗測試：未提供必要性應拋 400；再實作組成關係的驗證
- [x] 4.8 實作組成樹展開服務，支援指定深度，並附測試驗證深度限制生效
- [x] 4.9 實作反向查詢服務（由零件回溯所有可達的終端成品），附測試

## 5. Company 領域服務

- [x] 5.1 先寫失敗測試：建立未上市公司（無任何識別碼）應成功；再實作 `CompanyService.create`
- [x] 5.2 先寫失敗測試：(type, value) 重複應拋 409；再實作識別碼建立驗證
- [x] 5.3 先寫失敗測試：同公司第二筆 is_primary 應拋 409；再實作主要識別碼唯一性驗證
- [x] 5.4 先寫失敗測試：以代號 2330 應查得公司；再實作依識別碼查詢
- [x] 5.5 先寫失敗測試：公司別名與其他公司正規化名稱衝突應拋 409；再實作公司別名驗證

## 6. 供應關係與市佔率服務

- [x] 6.1 先寫失敗測試：同公司同零件多角色應並存、同角色重複應拋 409；再實作 `CompanyItemRoleService`
- [x] 6.2 先寫失敗測試：依角色過濾查詢零件供應商；再實作查詢方法
- [x] 6.3 先寫失敗測試：缺少地區／期間／口徑應拋 400、百分比超出 0–100 應拋 400；再實作 `MarketShareService` 驗證
- [x] 6.4 先寫失敗測試：不同來源的衝突數值應並存、同來源重複應拋 409；再實作寫入邏輯
- [x] 6.5 先寫失敗測試：市佔率可在無角色關係時先行寫入；確認未誤加 FK 限制
- [x] 6.6 先寫失敗測試：排名依百分比降冪、無資料回空清單非 404；再實作排名查詢

## 7. 來源與審核

- [x] 7.1 先寫失敗測試：缺少 source_type 應拋 400；再實作共用驗證
- [x] 7.2 先寫失敗測試：AI_GENERATED 未帶 confidence 應拋 400、MANUAL 未帶 confidence 應成功；再實作條件式驗證
- [x] 7.3 先寫失敗測試：新資料預設 DRAFT 且 reviewed_by/at 為空；再實作預設值
- [x] 7.4 先寫失敗測試：標記 VERIFIED 需提供審核者否則 400，成功時記錄審核時間；再實作審核狀態流轉
- [x] 7.5 先寫失敗測試：查詢預設只回 VERIFIED、明確指定才含 DRAFT、REJECTED 一律不外露；再實作查詢過濾

## 8. API 層

- [x] 8.1 建立 payloads：item 建立／查詢、組成樹節點、公司、識別碼、供應關係、市佔率排名的 request/response 類別
- [x] 8.2 實作 `ProductController`：`GET /api/products/{id}/components`（組成樹，支援 depth 與 necessity 過濾）
- [x] 8.3 實作 `ItemController`：item CRUD、別名管理、組成關係建立、反向查詢終端成品
- [x] 8.4 實作 `CompanyController`：`GET /api/companies/{code}`（依代號查詢，回傳公司與所有識別碼）、公司 CRUD、別名與識別碼管理
- [x] 8.5 實作供應關係與市佔率端點：查零件供應公司（可依角色過濾）、查市佔率排名
- [x] 8.6 補齊 OpenAPI 註解（`@Tag`、`@Operation`、`@ApiResponses`、`@Schema`）
- [x] 8.7 撰寫 Controller 層測試（`@WebMvcTest` + `@Tag("integration")`），驗證 404／409／400 狀態碼語意符合 `.claude/rules/api-design.md`

## 9. 收尾

- [x] 9.1 執行 `./mvnw clean test-compile` 後 `./mvnw surefire:test` 確認快測全綠
- [x] 9.2 執行 `./mvnw verify` 跑含整合測試的全量，附實際輸出
- [x] 9.3 以 `/code-review` 進行 fresh-context diff review，只修正確性問題
- [x] 9.4 依實際落地結果回填 `.claude/rules/architecture.md` 的領域模型與 package 對照表
- [x] 9.5 建立 `docs/CHANGELOG.md` 並記錄本次新增能力
