## Why

`GET /api/companies` 已能回答「**這個零件**有哪些公司在做」，但答不出「有哪些代工組裝廠」——
角色過濾目前從屬於零件，`companyRole` 必須搭配 `itemId`，單獨使用會回 400
（`add-company-listing` 刻意如此，避免靜默忽略條件）。

跨零件的角色彙總是產業地圖的自然問法：「台灣有哪些封測廠」「哪些公司只做設計不做製造」
這類問題，資料都在 `company_item_role`，缺的是入口。目前只能逐個零件查再自行合併，
呼叫端得先知道有哪些零件，等於把彙總工作推給前端。

前端即將開工，公司列表的過濾器面板若要提供「角色」這個維度，它必須能獨立生效——
不然使用者得先選一個零件才能選角色，是很奇怪的互動。

## What Changes

- `companyRole` 可**單獨使用**：不指定 `itemId` 時，回傳「對**任何**零件具有該角色」的公司
- 同時指定 `itemId` 與 `companyRole` 時語意不變：仍是「對**該零件**具有該角色」
- 移除 `companyRole` 必須搭配 `itemId` 的 400 驗證（該驗證是本 change 的前身刻意加的守衛，
  能力補上後即失去存在理由）
- 角色的審核可見範圍沿用既有規則：只採計可見的供應角色，草稿角色不因此讓公司出現在預設查詢

**非破壞性**：既有的 `itemId` + `companyRole` 組合、以及只給 `itemId` 的用法行為完全不變；
唯一的行為變化是「只給 `companyRole`」從 400 變成回傳結果。

## Capabilities

### Modified Capabilities
- `company-registry`: 「公司列表支援依供應零件過濾」這條 requirement 的角色部分放寬——
  角色從「僅在指定零件時可用」改為「可獨立使用，亦可與零件併用」

## Impact

- **既有程式**：`CompanyRepository.findCompanies` / `countCompanies` 的 SQL 條件結構
  （角色條件要移出 `itemId` 的守衛）、`CompanyQuery` 移除 `@AssertTrue` 驗證
- **資料庫**：無 schema 變更；走既有的 `company_item_role`，
  但需評估 `(company_role, review_status)` 是否要補索引——現況無 `itemId` 收斂時掃描範圍變大
- **API**：`GET /api/companies` 的 `companyRole` 參數語意擴充，OpenAPI 說明需同步
- **測試**：`CompanyControllerTest` 中「只給 companyRole 應回 400」的測試需改寫為驗證新語意
- **文件**：`docs/CHANGELOG.md`
