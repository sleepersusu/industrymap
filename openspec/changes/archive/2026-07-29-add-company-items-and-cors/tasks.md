## 1. CORS（先做：不通的話前端連驗證都開始不了）

- [x] 1.1 新增 `config/WebCorsConfig`（本專案第一個 `config` 類別），以
      `WebMvcConfigurer.addCorsMappings` 實作，不引入 Spring Security（design D6）
- [x] 1.2 允許來源走 `industrymap.cors.allowed-origins`，預設
      `http://localhost:5173,http://localhost:3000`，可經 `CORS_ALLOWED_ORIGINS` 覆寫；**未使用 `*`**
- [x] 1.3 測試：允許清單內的 Origin 取得 `Access-Control-Allow-Origin`、清單外的取不到（回 403）、
      預檢請求回報允許的方法

## 2. 公司供應的零件清單

- [x] 2.1 `GET /api/companies/{code}/items` 端點與行為測試
- [x] 2.2 `CompanyItemRoleRepository` 新增 `findByCompanyIdAndReviewStatusIn` /
      `findByCompanyIdAndCompanyRoleAndReviewStatusIn`，加 `@EntityGraph(attributePaths = "item")`
- [x] 2.3 `CompanyItemRoleService.findSuppliedItems`：公司走 `getVisibleByReference`、
      角色走 `visibleStatuses`、角色指向的節點走 `isExposable`（design D4 第四格）
- [x] 2.4 以品類節點為單位聚合角色，同一節點只出現一筆（design D1），單元測試覆蓋
- [x] 2.5 `CompanyItemResponse` + `CompanyItemQuery`（`role` + `includeDrafts`，design D3）
- [x] 2.6 `CompanyController` 加端點與 OpenAPI 註解
- [x] 2.7 邊界：無角色回空清單而非 404、已駁回公司回 404、依角色過濾、查無公司回 404

## 3. 兩道既有守衛（本次是它們第一次對新端點生效）

- [x] 3.1 **實測確認守衛會擋**：端點寫好但不動矩陣時，`ReviewVisibilityMatrixTest` fail 並逐字指名
      `GET /api/companies/{code}/items` 未登記。上一個 change 建的守衛第一次對真正的新程式碼生效
- [x] 3.2 矩陣登記四格並各配一支斷言：`company_identifier`／`company`／`company_item_role`
      為主查詢、`item` 為關係指向的實體（34 → 38 格）
- [x] 3.3 `QueryFanoutTest` 加一格，大扇出用 60（批次值之上）（5 → 6 格）

## 4. 收尾

- [x] 4.1 `./mvnw clean verify` 全量通過
- [x] 4.2 `.claude/rules/api-design.md` 路徑表補上新端點並拆開 `/{code}` 與 `/{code}/items`；
      `architecture.md` 的 `config` 標為已落地，並註明日後補 Security 時 CORS 應移交過濾鏈
- [x] 4.3 `docs/CHANGELOG.md` 記兩筆（新端點、CORS 設定含安全前提）
- [x] 4.4 以 fresh-context diff review 審查（`/code-review`），4 筆 findings 全部處理：
      - **[中] CORS 預設值完全沒有測試守住**：測試用 `@TestPropertySource` 把設定蓋掉，
        驗到的只是「機制會動」，而真正會上線的預設值改壞了不會紅——正是這個設定要解決的問題本身。
        改為直接對預設值下斷言，並以 `@ParameterizedTest` 逐個驗每個來源
        （只驗第一個的話，逗號被誤寫成空白仍會通過）。**已用變異測試確認會紅**：
        拿掉預設值裡的 `:3000` → `defaultOrigins_shouldReceiveCorsHeader` 由 200 變 403 而 fail
      - **[低] 回應順序未定義**：兩支衍生查詢都沒有 `ORDER BY`，任一筆角色被審核（UPDATE）
        就可能讓清單重排，前端看到的順序無故變動。專案對其他列表查詢本來就有
        「排序固定 display_name 再 id」的規則，這裡跟上；`roles` 陣列同樣排序
      - **[低] `allowedMethods` 有 GET 卻沒有 HEAD**：兩者可見性完全相同，允許其一卻擋另一個
        是任意缺口而非刻意限制，已補上。`/api-docs` 維持不納入並在 javadoc 寫明理由
        （CLI 產型別不經瀏覽器、不受 CORS 管，不預先開放不需要的路徑）
      - **[低] 查詢筆數守衛只覆蓋 `role == null` 那條路徑**：端點有兩支查詢，
        另一支的 `@EntityGraph` 被拿掉時不會紅，已補上第二次量測
- [x] 4.5 手動確認前端串得起來（實機啟動非只跑測試）：允許來源回 200 + 正確標頭、
      未允許來源回 403 無標頭、預檢回報方法清單；新端點對 10 家公司實測皆正確，
      Intel 對 CPU 回 `["DESIGN","MANUFACTURE"]`，多角色聚合在真實資料上成立
- [x] 4.6（追加）CORS 預設值那筆寫入 `~/.claude/dev-errors/error-log.md`
