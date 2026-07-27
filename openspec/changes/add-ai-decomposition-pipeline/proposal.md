## Why

核心地圖與寫入介面都已就緒，但整張地圖目前只有 12 個品類節點、6 家公司，全是手動一筆一筆建的。
手動建資料在品類層完全不可行：光是一台腳踏車拆到第三層就是數百筆，主機板更多。

`design D8` 從一開始就把資料來源定為「AI 生成初稿 + 人工審核」，前兩個 change 鋪的路
（自然鍵定位、批次建立、批次審核）都是為了這一步。**現在是唯一缺的那一塊：產生初稿的機制本身。**

LLM 拆解一個產品需時 30–90 秒，無法塞進同步 HTTP 請求，因此本次一併建立
job / queue 基礎建設（`.claude/rules/architecture.md` 既有規範，且日後股價／新聞定期同步會共用）。

## What Changes

- **新增 job / queue 基礎建設**：RabbitMQ 設定、producer、consumer、任務狀態追蹤。
  這是本專案第一個非同步流程，日後股價／新聞同步共用同一套
- **新增 LLM client**（`clients/`）：封裝對外的模型呼叫，回傳結構化的拆解結果；
  逾時、失敗、回傳格式不符皆轉為明確狀態，不讓例外逸出到 consumer 之外
- **新增拆解任務端點**：給定一個品類節點與展開深度，觸發非同步拆解，回傳可追蹤的 job id；
  另有查詢任務狀態與結果的端點
- **拆解結果寫入為草稿**：consumer 取得 LLM 結果後，經既有的 service 層驗證與去重
  （名稱正規化、別名比對、循環偵測）寫入，來源類型為 AI 生成且必帶信心度，
  審核狀態一律草稿，與現行規則完全一致
- **生成前先比對既有節點**：LLM 產出的名稱先經正規化與別名解析，命中既有節點則沿用，
  不新建——這是 `design D9`（DAG 節點共用）能否成立的關鍵

本次不含：投顧報告等外部文件的解析匯入（待實際文件格式確認後另開 change）、
市佔率生成（無可查證來源時不得產生數字，見 `design D4`）、前端、權限控管。

## Capabilities

### New Capabilities
- `ai-decomposition`: AI 產品拆解——非同步任務的觸發與追蹤、LLM 呼叫與結果解析、寫入前的既有節點比對與去重
- `async-job`: 非同步任務基礎建設——queue 設定、producer/consumer、任務狀態流轉與查詢，供本次與日後的外部資料同步共用

### Modified Capabilities
- `data-provenance`: AI 生成資料的來源明細需可追溯到產生它的模型與任務

## Impact

- **新增依賴**：無（`spring-boot-starter-amqp` 已在 `pom.xml`；LLM client 以既有 WebFlux 實作）
- **新增設定**：RabbitMQ 連線、LLM 端點與模型識別、逾時參數。
  **API key 一律走 `.env`／環境變數**，禁止寫入設定檔（`CLAUDE.md` 禁止事項）
- **新增程式**：`clients/`、`job/{config,producer,consumer,scheduler,service}`、
  `controller/DecompositionController`、對應 payloads 與任務狀態實體
- **資料庫**：新增任務狀態表，需 Flyway migration
- **既有程式**：沿用 `ItemService` / `ItemCompositionService` / `BulkAuthoringService` 的寫入與驗證，
  不重寫一套——繞過 service 直接寫 DB 會讓循環偵測與去重失效
- **測試**：LLM client 一律 mock，禁止打真實 API（`.claude/rules/testing.md`）；
  排程需 `@Profile("!test")` 避免整合測試期間觸發外部呼叫
- **文件**：`.claude/rules/architecture.md` 的 `clients/` 與 `job/` 段落需由「預留」改為實際結構
