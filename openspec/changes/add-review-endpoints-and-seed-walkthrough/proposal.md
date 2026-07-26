## Why

核心資料模型已上線，但**資料進得去、出不來**：寫入一律為草稿（`ProvenanceRequest` 無審核狀態欄位）、
對外查詢預設只回已驗證資料，而 `ReviewService.applyReview()` 雖已實作且有測試，
**沒有任何 API 端點呼叫它**。因此目前無論塞多少資料，所有查詢都會回空。

同時，八張表至今沒有任何一筆真實資料，整套 API 從未被實際使用過。在接商工登記批次匯入或
AI 生成流程（兩者都會一次灌入大量資料）之前，必須先以小規模資料走完一次完整路徑，
確認 API 真的能回答「這台腳踏車的零件來自哪些公司、誰市佔最大」，並暴露設計上的不順之處。

## What Changes

- 新增審核 API：可將任一內容資料的審核狀態在草稿／已驗證／已駁回之間流轉
- 審核 API 支援批次，單次可審核多筆、跨不同資料類型，並逐筆回報結果
- **BREAKING** 供應角色與市佔率的寫入改以公司代號指定公司，不再要求呼叫端提供公司內部識別碼：
  目前 `CompanyResponse` 不回 `id` 而寫入端點卻要 `companyId`，導致建完公司後無從建立供應關係，
  只能直接查資料庫；此形狀同時牴觸 `.claude/rules/api-design.md`「不曝露內部自增 id」的原則
- 以真實 API（非直接寫入資料庫）建立一台腳踏車的種子資料，涵蓋品類節點、組成關係、
  公司、識別碼、供應角色與市佔率，並更新 Insomnia collection 保留可重跑的請求
- 端到端驗證三個核心查詢確實回傳預期結果，並記錄過程中發現的 API 可用性問題

本次不含：審核操作的前端介面、商工登記批次匯入、AI 生成流程、股價／新聞／專利串接。

## Capabilities

### New Capabilities
（無）

### Modified Capabilities
- `data-provenance`: 新增審核 API 的對外行為要求——目前 spec 只定義了審核狀態流轉的語意，
  未定義任何可觸發流轉的介面，導致草稿資料無法轉為已驗證
- `supply-relation`: 供應角色與市佔率的寫入改以公司代號指定公司，不再要求公司內部識別碼

## Impact

- **新增程式**：`controller/ReviewController`、`payloads/review/` 下的 request/response 類別
- **既有程式**：沿用 `ReviewService.applyReview()`，不改動其邏輯；需新增依 id 取得各類實體的查找路徑
- **資料庫**：無 schema 變更，不需新增 Flyway migration
- **API**：新增審核端點；既有端點不變，無破壞性變更
- **文件**：`docs/insomnia/industrymap.insomnia.json` 補上審核與種子資料請求；
  `docs/CHANGELOG.md` 記錄審核 API 上線
