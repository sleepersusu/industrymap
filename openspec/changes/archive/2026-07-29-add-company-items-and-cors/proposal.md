## Why

前端要開始串接，但有兩件事擋著。

**一、瀏覽器連第一支 API 都打不到。** 專案至今沒有任何 CORS 設定（也沒有 `config` package）。
前端跑在另一個 port 時，瀏覽器會擋掉每一個跨來源請求。這不是「體驗不好」而是完全串不起來。

**二、從公司側進去就走不下去。** `.claude/rules/api-design.md` 把 `/api/companies/{code}` 標為
第一階段的「公司基本資料（**含代號、對應零組件**）」，但實際只回了公司與識別碼。
目前只有反向的 `/api/companies?itemId=`（給零件找公司），沒有正向的「這家公司做哪些零件」。
公司詳情頁做到一半就沒東西可點了——而「從公司側進入地圖」本來就是第一階段的目標之一。

本次只解這兩件。資料規模化（`add-ai-decomposition-pipeline`）與認證授權都刻意不動：
目前的目的是用現有的 12 個品類節點、6 家公司驗證前端的形狀，不是把系統建完。

## What Changes

- 新增 `GET /api/companies/{code}/items`：列出該公司供應的品類節點，逐筆帶它擔任的角色
- 新增可設定的 CORS 允許來源，預設只含本機開發用的 origin
- **不做**：認證授權、分頁化既有端點、AI 拆解 pipeline

## Capabilities

### Added Capabilities
- `api-access`: 瀏覽器可跨來源呼叫 API；允許來源必須可設定，且在補上認證之前不得放寬

### Modified Capabilities
- `company-registry`: 補上「公司供應的零件清單」，讓公司側的地圖走得下去

## Impact

- **新增**：`config/WebCorsConfig`（本專案第一個 `config` 類別）、
  `payloads/company/CompanyItemResponse`、`CompanyItemQuery`
- **修改**：`CompanyController`、`CompanyItemRoleService`、`CompanyItemRoleRepository`、
  `application.properties`
- **兩道既有守衛會擋住這次的新程式碼，這是它們第一次對新端點生效**：
  - `ReviewVisibilityMatrixTest` 的覆蓋率守衛——新 GET 端點未登記進可見性矩陣即 fail
  - `QueryFanoutTest` ——新的多筆查詢要加一格，且大扇出設在批次值之上
- **無 schema 變更**（只讀既有的 `company_item_role`）
- **CHANGELOG**：新端點屬使用者可感知，要記；CORS 屬設定，一併記於同一筆
