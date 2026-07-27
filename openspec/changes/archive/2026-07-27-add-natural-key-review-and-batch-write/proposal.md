## Why

上一個 change 的種子資料走查（手動建一台腳踏車、約 30 筆）暴露三個問題，其中一個是閉環上的實質缺口：

**審核端點以「類型 + 內部 id」定位目標，但多數查詢回應刻意不給 id。**
`CompanyIdentifierResponse` 完全不含 id、組成關係沒有 GET 端點、組成樹回應只給 `itemId`
不給關係本身的 id。結果是**公司識別碼這一類資料目前無法經 API 審核**——上次是直接查資料庫
取 id 才走完流程的。資料進得去、出不來，閉環仍缺一角。

同時，手動 30 筆就已經要「自行從各建立回應收集 id、識別碼還得查資料庫」。AI 生成 pipeline
一次會產生數百筆，這個介面在那個規模下無法使用。**先把介面修好，pipeline 才有合理的東西可呼叫。**

## What Changes

- **審核目標改為可用自然鍵定位**（取代必須知道內部 id）：
  - 公司識別碼 → `(identifierType, identifierValue)`
  - 組成關係 → `(parentItemId, childItemId)`
  - 節點別名／公司別名 → 正規化名稱
  - 供應角色 → `(companyReference, itemId, companyRole)`
  - 市佔率 → 完整維度組合（公司、零件、期間、地區、口徑、來源）
  - 品類節點／公司 → 既有的名稱或代號識別
  - 原有的 id 定位方式維持可用，不移除
- **內容建立端點支援批次**，且回應一併回傳各筆的定位資訊，供隨後的批次審核直接使用
- **統一公司對外識別的產生邏輯**：`SupplierResponse.companyReference` 改為與
  `CompanyResponse.reference` 同一套規則（優先主要代號，無代號才退回正規化名稱），
  由共用組裝邏輯產出，避免同一概念在兩個回應給出不同值

本次不含：AI 生成流程本身、商工登記批次匯入、前端、權限控管。

## Capabilities

### New Capabilities
- `bulk-authoring`: 內容資料的批次建立——單次提交多筆、逐筆回報結果、回應帶可直接用於審核的定位資訊

### Modified Capabilities
- `data-provenance`: 審核目標的定位方式擴充為支援自然鍵，不再限定內部 id
- `company-registry`: 公司對外識別的規則明確化，並要求所有回應一致採用

## Impact

- **API 契約**：`ReviewRequest` / `ReviewTarget` 新增自然鍵欄位（id 欄位改為非必填）；
  各建立端點新增批次變體；`SupplierResponse.companyReference` 語意改變（**行為變更**，
  但兩種值都餵得進 `GET /api/companies/{code}`，呼叫端不會斷）
- **新增程式**：`payloads/review` 的自然鍵定位型別、`service/review` 的自然鍵解析、
  批次建立的 service 與 controller 端點
- **既有程式**：`ReviewApplyService` / `BatchReviewService` 擴充定位邏輯；
  `SupplierResponse.from` 改用共用的公司識別組裝
- **資料庫**：無 schema 變更，不需 Flyway migration
- **文件**：`.claude/rules/api-design.md` 需補上「內部作業端點可用自然鍵定位」的界線說明
