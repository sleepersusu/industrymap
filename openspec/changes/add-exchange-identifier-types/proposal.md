## Why

`IdentifierType` 目前是 `TWSE` / `TPEX` / `TSE` / `NASDAQ` / `NYSE` / `TAX_ID` / `DUNS` / `OTHER`，
涵蓋不到香港、法蘭克福等交易所。2026-07-28 的兩輪資料灌入已經因此產生 3 筆變形資料：

| 公司 | 現況 | 應為 |
|---|---|---|
| 鴻騰精密科技 | `OTHER` / `HKEX:6088` | `HKEX` / `6088` |
| Lenovo | `OTHER` / `HKEX:0992` | `HKEX` / `0992` |
| Infineon | `OTHER` / `FSE:IFX` | `FSE` / `IFX` |

因為 enum 沒有對應類型，只能用 `OTHER` 並把交易所自行塞進**值**裡。後果有三個：

1. **`unique(type, value)` 的語意被稀釋**——`OTHER` 底下混雜多個編碼體系，值的格式全靠人工約定，
   沒有任何機制擋住下一個人寫成 `HK:6088` 或 `6088.HK`
2. **無法依交易所查詢**（例如「所有港股供應商」），因為交易所資訊藏在字串前綴裡
3. **會持續累積**。供應鏈往上游走必然遇到日、韓、中、歐廠商；現在只有 3 筆要修，越晚越多

這是 `docs/data-loading-playbook.md` 第八節記的 G4。

## What Changes

- **`IdentifierType` 擴充**，補上實際會遇到的交易所類型（至少 `HKEX`、`FSE`；
  一併評估 `KRX`、`SSE`、`SZSE`、`LSE`、`AMS` 等，在 design 決定範圍與取捨）
- **既有 3 筆 `OTHER` 資料轉為正規類型**，值去掉自訂前綴
- **`OTHER` 回歸真正的例外用途**，並在 spec 明確它的適用界線——
  避免下一個人遇到新交易所時又走 `OTHER` 加前綴這條路

本次不含：`IdentifierType` 以外的 enum；識別碼的修正端點（本次的既有資料以 migration 轉換，
不需要端點）；公司列表與修正端點。

## Capabilities

### Modified Capabilities
- `company-registry`: 公司識別碼的類型涵蓋範圍擴充至主要境外交易所，
  並明確 `OTHER` 的適用界線

## Impact

- **API 契約**：`identifierType` 的可用值增加。**既有值的語意不變**，
  但那 3 筆資料的 `identifierType` 與 `identifierValue` 會改變（**行為變更**，
  需在 CHANGELOG 記錄）
- **新增程式**：`enums/IdentifierType` 新增列舉值
- **既有程式**：預期無邏輯改動——`identifier_type` 在資料庫是 `VARCHAR(32)` 且**無 check constraint**
  （見 `V2026_07_26_100500__create_company_identifier.sql`），entity 以 `@Enumerated(EnumType.STRING)`
  持久化，因此新增列舉值不需要 schema 變更
- **資料庫**：**需要一支 Flyway data migration** 轉換既有 3 筆資料。
  這是本 change 唯一的 migration，不含任何 schema 變更
- **文件**：`docs/data-loading-playbook.md` 第八節 G4 於完成後移除或標記已解決
