## 1. 識別值的組裝與拆解（design D1、D5）

- [ ] 1.1 先寫失敗測試（純單元）：`CompanyReferences` 對有主要識別碼的公司回傳 `<類型>:<代號值>` 限定形式，無識別碼時回正規化名稱，已駁回的識別碼不列入
- [ ] 1.2 先寫失敗測試：限定形式的拆解——`TWSE:2330` 拆成（`TWSE`, `2330`）；冒號前非合法類型（`ISIN:DE0006231004`）不視為限定形式；不含冒號者不視為限定形式；值本身含冒號時只切第一個冒號
- [ ] 1.3 實作組裝與拆解，兩者放在同一處（design D5：組裝與拆解成對，分開寫會漂移）

## 2. 解析改以自然鍵定位（design D3、D4）

- [ ] 2.1 先寫失敗測試：`CompanyIdentifierRepository` 以（類型, 代號值）查詢至多回一筆；SQL 依 `.claude/rules/architecture.md` 寫在 repository 層
- [ ] 2.2 先寫失敗測試：`getByReference` 收到限定形式時只回該交易所的公司——同時存在另一家持相同代號值、不同類型的公司
- [ ] 2.3 先寫失敗測試：`getByReference` 收到裸代號且**單筆**命中時照常回傳（相容既有操作）
- [ ] 2.4 先寫失敗測試：`getByReference` 收到裸代號且**多筆**命中時拋 409，訊息含所有候選的限定形式；MUST NOT 回傳任一家公司
- [ ] 2.5 先寫失敗測試：`getByIdentifierValue` 的多筆命中行為與 `getByReference` 一致，兩者不得各自為政
- [ ] 2.6 先寫失敗測試：未上市公司以正規化名稱查詢仍可命中（限定形式判別不得攔截名稱）
- [ ] 2.7 實作解析：限定形式走唯一鍵、裸代號走既有查詢後判斷筆數、皆未命中才退回名稱查詢

## 3. 產出端與呼叫端同步

- [ ] 3.1 先寫失敗測試：`referenceOf` 與 `referencesOf`（批次）產出的值與 `CompanyReferences` 一致，批次與單筆不得給出不同形狀
- [ ] 3.2 先寫失敗測試：公司資料、供應商、市佔率三處回應的公司對外識別為同一個限定形式值（spec 的「跨端點一致」）
- [ ] 3.3 先寫失敗測試（回歸）：取任一回應的對外識別值查詢，回到的是**同一家**公司——以刻意建構的跨交易所撞號資料驗證，不能只驗單一公司
- [ ] 3.4 **先搜尋所有引用再改**：`grep -r "getByReference\|referenceOf\|referencesOf\|CompanyReferences" src/test/`，逐檔同步既有測試中以裸代號斷言的部分，`./mvnw -DskipTests test-compile` 通過（`.claude/rules/testing.md` 的既有測試同步規則）
- [ ] 3.5 確認以 `companyCode` 定位的寫入路徑（`CompanyItemRoleService`、`MarketShareService`、`BulkAuthoringService`、`NaturalKeyResolver`）在新解析規則下行為正確，補齊缺少的邊界測試

## 4. 路徑契約驗證（design D2 的 URL 風險）

- [ ] 4.1 先寫失敗測試（`@WebMvcTest`，需 `@Tag("integration")`）：`GET /api/companies/TWSE:2330` 能正確綁定路徑變數——**必須以實際請求驗證**，不可只靠「冒號在 path segment 合法」的推論
- [ ] 4.2 同上驗證 `POST /api/companies/{code}/identifiers` 與 `/aliases` 兩個子路徑在限定形式下仍可用
- [ ] 4.3 補 OpenAPI 註解與 `@Schema` 範例，讓 Swagger 上的 `code` 參數範例是限定形式而非裸代號

## 5. 文件與收尾

- [ ] 5.1 更新 `docs/data-loading-playbook.md`：公司代號欄位說明與所有 curl 範例改用限定形式，並說明裸代號仍可用但撞號時會回 409
- [ ] 5.2 確認無 schema 變更、不需要 Flyway migration（對外識別是回應層投影，不落地）
- [ ] 5.3 收尾執行一次 `./mvnw clean verify`，附實際輸出（長時間 build 背景執行，靠完成通知取回結果，禁止輪詢迴圈）
- [ ] 5.4 對本次 diff 跑 `/code-review`（fresh context），只修正確性 findings
- [ ] 5.5 於 `docs/CHANGELOG.md` 的 `[Unreleased]` 記錄**破壞性變更**（對外識別與公司路徑改為限定形式；裸代號撞號回 409），hash 欄寫 `(pending)`；追加前先掃既有項目
- [ ] 5.6 commit（changelog 與程式同一筆）
