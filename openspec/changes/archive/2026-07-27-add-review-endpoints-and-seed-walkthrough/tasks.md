## 1. 審核 payload 與查找

- [x] 1.1 建立 `ReviewTargetType` enum（ITEM / ITEM_ALIAS / ITEM_COMPOSITION / COMPANY / COMPANY_ALIAS / COMPANY_IDENTIFIER / COMPANY_ITEM_ROLE / MARKET_SHARE），作為審核端點的類型白名單
- [x] 1.2 建立 `payloads/review/ReviewRequest`：目標類型、識別碼、目標狀態、審核者；**必填與長度規則一律用 `jakarta.validation` annotation 表達**（`@NotNull`、`@NotBlank`），不在 service 重寫同義檢查
- [x] 1.3 建立 `payloads/review/BatchReviewRequest`：審核目標清單 + 共用的目標狀態與審核者；以 `@NotEmpty`、`@Valid` 串接巢狀驗證（空批次由 annotation 擋下，非 service）
- [x] 1.4 建立 `payloads/review/ReviewResultResponse`：逐筆回報目標類型、識別碼、成功與否、失敗原因；以 Lombok `@Builder` + 靜態 `from(...)` 組裝，controller 不做組裝邏輯
- [x] 1.5 先寫失敗測試：不支援的類型應回 400 而非 500；再實作類型解析

## 2. ReviewLookupService（依類型取實體）

- [x] 2.1 建立 `service/review/ReviewLookupService`，依 `ReviewTargetType` 取對應 repository 查實體；**查詢一律呼叫 repository，不在 service 寫任何 SQL 或 JPQL**
- [x] 2.2 先寫失敗測試：識別碼不存在應拋 404 `ServerException`；再實作查找
- [x] 2.3 確認八個 repository 都具備依 id 取實體的能力（Spring Data 內建 `findById` 即可，不需新增自訂查詢）
- [x] 2.4 檢查 `ReviewLookupService` 行數，若因八個分支而膨脹接近 500 行，改以 Map 註冊查找函式收斂（見 `.claude/rules/architecture.md` service 上限）

## 3. 單筆審核端點

- [x] 3.1 先寫失敗測試：草稿轉已驗證應更新狀態並記錄審核者與時間；再實作 `ReviewController` 單筆端點，**沿用既有 `ReviewService.applyReview()`，不重寫審核邏輯**
- [x] 3.2 先寫失敗測試：轉已驗證未提供審核者應回 400；確認由 annotation 或既有 service 驗證擋下
- [x] 3.3 先寫失敗測試：已驗證資料轉回草稿應清空審核者與審核時間；驗證既有 service 行為經 API 正確生效
- [x] 3.4 先寫失敗測試：標記已駁回後，該筆資料在任何對外查詢都不得出現；驗證與 `ReviewScopes` 一致
- [x] 3.5 先寫失敗測試：識別碼不存在應回 404；再串接 `ReviewLookupService`
- [x] 3.6 **controller 回應一律 `ResponseEntity<ServerResponse<T>>`，透過既有 `ServerResponses` 包裝**，不自行 new `ResponseEntity`，與四支既有 controller 形狀一致
- [x] 3.7 補齊 OpenAPI 註解（`@Tag`、`@Operation`、`@ApiResponses`），並於 `@Schema` 標明支援的目標類型清單

## 4. 批次審核端點

- [x] 4.1 先寫失敗測試：批次全部有效時應全部更新並逐筆回報成功；再實作批次端點
- [x] 4.2 先寫失敗測試：批次中一筆識別碼不存在時，其餘各筆仍完成審核，該筆標示失敗與原因；確認**未整批 rollback**
- [x] 4.3 先寫失敗測試：空批次應回 400；確認由 `@NotEmpty` annotation 擋下
- [x] 4.4 確認每筆審核各自為獨立交易，未共用長交易（見 `.claude/rules/architecture.md` transaction boundary）
- [x] 4.5 先寫失敗測試：批次可跨不同目標類型（同時審節點與市佔率）；再驗證實作

## 5. 供應關係改以公司代號指定（解除種子資料的死結）

- [x] 5.1 先寫失敗測試：以公司代號建立供應角色應成功；再將 `CreateCompanyItemRoleRequest` 的 `companyId` 改為 `companyCode`，**必填以 `@NotBlank` annotation 表達**
- [x] 5.2 先寫失敗測試：以公司代號建立市佔率應成功；再同步調整 `CreateMarketShareRequest`
- [x] 5.3 先寫失敗測試：不存在的公司代號應回 404；再實作代號解析，**沿用 `CompanyService` 既有的依代號查詢，不重寫查找邏輯**
- [x] 5.4 檢查 `CompanyItemRoleService` 與 `MarketShareService` 行數未因此逼近 500 行上限
- [x] 5.5 同步更新既有 controller 測試與 OpenAPI 註解，`./mvnw -DskipTests test-compile` 通過
- [x] 5.6 更新 `docs/insomnia/industrymap.insomnia.json`：改用代號，並移除「companyId 無法從 API 取得」的已知問題註記

## 6. 端到端種子資料（一台腳踏車）

- [x] 6.1 啟動應用程式，確認可連上本機 PostgreSQL 且 Flyway 狀態正常
- [x] 6.2 以 API 建立品類節點：腳踏車（終端成品）、車架、變速系統、輪組、煞車、鏈條
- [x] 6.3 以 API 建立第二層節點與組成關係：變速系統 → 前變速器、後變速器、飛輪；各關係標明必要性
- [x] 6.4 以 API 建立公司與識別碼：巨大機械（TWSE 9921）、桂盟（TWSE 5306）、Shimano（日本交易所代號）、SRAM（未上市、不帶任何識別碼）
- [x] 6.5 以 API 建立供應角色：各公司對應零件的角色（製造／品牌等），**全程以公司代號指定，不查資料庫**
- [x] 6.6 以 API 建立市佔率：**僅填有可查證來源者，`source_detail` 必須是真實出處；查不到可靠數字即留空不填**（見 design D4，禁止自行編造百分比）
- [x] 6.7 以批次審核端點將上述種子資料全部轉為已驗證
- [x] 6.8 更新 `docs/insomnia/industrymap.insomnia.json`，保留可重跑的請求（含審核端點）

## 7. 驗證與收尾

- [x] 7.1 驗證 `GET /api/products/{腳踏車id}/components?depth=2` 回傳完整零件樹，附實際回應
- [x] 7.2 驗證 `GET /api/items/{鏈條id}/suppliers` 回傳對應公司與角色，附實際回應
- [x] 7.3 驗證 `GET /api/items/{零件id}/market-share` 回傳排名（若有資料），附實際回應
- [x] 7.4 驗證未審核的資料不會出現在預設查詢、已駁回資料任何情況都不出現
- [x] 7.5 記錄本次操作過程中發現的 API 可用性問題（例如建關係必須先知道 id 是否太難用），寫入 design.md 的 Open Questions 供下一個 change 參考
- [x] 7.6 執行 `./mvnw clean test-compile` 後 `./mvnw surefire:test` 確認快測全綠
- [x] 7.7 執行 `./mvnw verify` 跑全量，附實際輸出
- [x] 7.8 以 fresh-context diff review 審查，只修正確性問題（修正三則：實體層未擋已駁回、
      審核測試恆真斷言、批次只攔 `ServerException`；見 design D7）
- [x] 7.9 更新 `docs/CHANGELOG.md` 記錄審核 API 上線
