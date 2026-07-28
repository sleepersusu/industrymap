## Why

公司的對外識別目前是**裸代號**（台積電 = `2330`），而代號只在發行它的交易所內唯一——
四位數字代號在台股、港股、滬深股是共通格式，號碼空間完全重疊。資料庫層已經正確地把
交易所放進唯一鍵（`unique(identifier_type, identifier_value)`），但查詢層把它丟掉了：
`CompanyService.getByReference` / `getByIdentifierValue` 只用 `identifier_value` 查，
再無排序地取 `identifiers.get(0)`。

在 `add-exchange-identifier-types` 之前這個缺陷藏得住，因為境外代號被迫寫成
`OTHER` / `HKEX:6088` 這種自帶前綴的畸形值，剛好全域唯一。那支 change 把值轉正成裸的 `6088`、
並補上 `HKEX` / `SSE` / `SZSE` 等類型後，命名空間就真的不見了——跨交易所撞號從
「不可能」變成「遲早」，而且會靜默回錯公司，不會報錯。

同一件事的另一半在產出端：`CompanyReferences.of` 回傳主要識別碼的裸代號，
因此兩家不同交易所的公司可以拿到**同一個** `companyReference`。這違反現行 spec
「跨端點的對外識別一致」與「對外識別可直接用於查詢」的意圖——值雖然查得到公司，但查到的是誰不確定。

## What Changes

- **BREAKING**：公司對外識別改為**交易所限定形式** `<類型>:<代號值>`（如 `TWSE:2330`、`HKEX:6088`）。
  所有回應的 `reference` / `companyReference` 欄位與公司路徑 `/api/companies/{code}` 同步改變。
  未上市公司（無任何識別碼）維持正規化名稱，不受影響
- **解析改為以自然鍵定位**：限定形式解析成（類型, 代號值）走唯一鍵查詢，命中至多一筆
- **裸代號仍可查，但不再靜默猜**：裸代號單筆命中照常回傳；**多筆命中回 409** 並於訊息列出候選的限定形式，
  讓呼叫端知道該用哪一個。此前這種情況取 `get(0)`，回哪一家取決於資料庫回傳順序
- **非交易所類型一併適用**：`TAX_ID` / `DUNS` / `OTHER` 也走同一套限定形式，識別的組裝規則只留一份
- 不含：識別碼的修正端點；改變 `unique(type, value)`；限制不同交易所使用相同代號值（那是現實，不是錯誤）

## Capabilities

### Modified Capabilities
- `company-registry`: 「公司查詢以代號為對外識別」的對外識別形狀由裸代號改為交易所限定形式，
  並明訂裸代號多筆命中時 MUST NOT 任選一筆

## Impact

- **API 契約（破壞性）**：`CompanyResponse.reference`、`SupplierResponse.companyReference`、
  `MarketShareResponse.companyReference`、批次建立回應的 `companyCode` 值形狀改變；
  `GET /api/companies/{code}`、`POST /api/companies/{code}/identifiers`、
  `POST /api/companies/{code}/aliases` 的路徑參數形狀改變。目前無外部呼叫端，
  但 `docs/data-loading-playbook.md` 的操作指令需同步
- **既有程式**：`helper/CompanyReferences`（組裝規則）、`service/company/CompanyService`
  （`getByReference`、`getByIdentifierValue`、`referenceOf`、`referencesOf`）、
  `repository/CompanyIdentifierRepository`（需要以類型加值查詢的方法）。
  以 `companyCode` 定位的呼叫端（`service/supply/CompanyItemRoleService`、
  `service/supply/MarketShareService`、`service/bulk/BulkAuthoringService`、
  `service/review/NaturalKeyResolver`）不需改動邏輯，但其行為會隨解析規則改變，測試需同步
- **資料庫**：無 schema 變更、無 migration——對外識別是回應層的投影，不落地成欄位
- **文件**：`docs/data-loading-playbook.md` 的 curl 範例與公司代號欄位說明
