## 1. 查詢條件 payload

- [ ] 1.1 建立 `payloads/company/CompanyQuery`：`page`、`size`（1–100）、`name`、`country`、`publicCompany`、`itemId`、`companyRole`、`includeDrafts`，比照 `EndProductQuery` 以 annotation 表達分頁邊界驗證
- [ ] 1.2 先寫失敗測試：`size` 超過 100 應拋 400、`page` 為負數應拋 400；再確認 annotation 生效

## 2. Repository 查詢

- [ ] 2.1 先寫失敗測試：以名稱關鍵字查詢應同時比對名稱與別名；再實作 repository 查詢（見 design D2）
- [ ] 2.2 先寫失敗測試：關鍵字同時命中名稱與多個別名時，該公司只出現一次；再實作內容去重
- [ ] 2.3 先寫失敗測試：**同一情境下總筆數也不得重複計算**；再實作 count 查詢的去重（design D2 明列此為易錯處，內容去重了但 count 沒去重會讓前端頁數全錯）
- [ ] 2.4 先寫失敗測試：關鍵字大小寫與全形差異應命中同一公司；再確認走既有正規化規則
- [ ] 2.5 先寫失敗測試：依國別過濾不分大小寫（`tw` 與 `TW` 相同）；再實作（design D5）
- [ ] 2.6 先寫失敗測試：依 `publicCompany` 過濾；再實作
- [ ] 2.7 先寫失敗測試：依 `itemId` 過濾只回傳對該零件有供應角色的公司，且同公司多重角色只出現一次；再實作（design D3）
- [ ] 2.8 先寫失敗測試：`itemId` 併用 `companyRole` 時只回傳該角色的公司；再實作
- [ ] 2.9 先寫失敗測試：依零件過濾時只採計可見的供應角色——草稿角色不得讓公司出現在預設查詢中；再實作（design D3 的可見範圍細節）
- [ ] 2.10 撰寫 Repository 整合測試（`@Tag("integration")`），涵蓋上述各條件與其併用

## 3. Service 層

- [ ] 3.1 先寫失敗測試：預設只回已驗證公司；再實作 `CompanyService.findCompanies`，沿用 `ReviewScopes`
- [ ] 3.2 先寫失敗測試：指定納入草稿時一併回傳且各筆標示審核狀態；再實作
- [ ] 3.3 先寫失敗測試：納入草稿時已駁回公司仍不得出現；再確認過濾
- [ ] 3.4 先寫失敗測試：指定不存在的 `itemId` 應拋 404；再實作
- [ ] 3.5 先寫失敗測試：無符合資料時回空清單與總筆數 0，非 404；再實作
- [ ] 3.6 確認 `CompanyService` 行數未超過 500 行上限，超過則依領域拆分

## 4. API 層

- [ ] 4.1 於 `CompanyController` 新增 `GET /api/companies`，以 `@Valid @ModelAttribute CompanyQuery` 綁定，回 `PageResponse<CompanyResponse>`
- [ ] 4.2 補齊 OpenAPI 註解（`@Operation`、`@ApiResponses`、各參數 `@Schema`）
- [ ] 4.3 撰寫 Controller 測試（`@WebMvcTest` + `@Tag("integration")`），驗證 200／400／404 語意
- [ ] 4.4 確認既有的 `POST /api/companies` 與 `GET /api/companies/{code}` 行為未受影響

## 5. 收尾

- [ ] 5.1 執行 `./mvnw clean test-compile` 後 `./mvnw surefire:test` 確認快測全綠
- [ ] 5.2 執行 `./mvnw verify` 跑全量，附實際輸出
- [ ] 5.3 以實際資料驗證：搜尋「桂盟」、依 `country=TW` 過濾、依某零件查供應公司，附實際回應
- [ ] 5.4 確認並行的 `fix-cross-exchange-identifier-lookup` 若已合入，兩者於 `CompanyController` 與 `CompanyService` 的改動已正確整併
- [ ] 5.5 以 fresh-context diff review 審查，只修正確性問題
- [ ] 5.6 更新 `.claude/rules/api-design.md` 的 base path 表，補上公司列表為第一階段端點
- [ ] 5.7 更新 `docs/data-loading-playbook.md` 第八節 G1（公司側已補齊，可簡化為僅剩節點清單需求）
- [ ] 5.8 更新 `docs/CHANGELOG.md` 記錄公司列表 API 上線
