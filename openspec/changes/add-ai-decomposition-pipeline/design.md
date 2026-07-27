## Context

核心地圖、寫入介面與審核閉環都已就緒，但整張地圖只有 12 個品類節點、6 家公司，全是手動建的。
`design D8`（前一批 change）從一開始就把資料來源定為「AI 生成初稿 + 人工審核」，
自然鍵定位與批次建立都是為這一步鋪的路。

現況約束：
- `job/` 底下只有空目錄，`clients/` 不存在，`application.properties` 無任何 RabbitMQ 設定——
  本次是本專案第一個非同步流程，基礎建設要從零建立
- `spring-boot-starter-amqp` 已在 `pom.xml`，不需新增依賴
- LLM 拆解一個產品需時 30–90 秒，無法塞進同步 HTTP 請求

**參考來源**：`ais-backend`（`~/Desktop/profetai/ais-backend`）已有成熟的 job/queue 與 client 實作，
本設計參考其**模式**，但不引用其實作——原因見 D1。

## Goals / Non-Goals

**Goals:**
- 讓一個品類節點能經由 AI 拆解出多層零件與供應公司，產出量從數十筆提升到數百筆
- 生成結果一律走既有 service 寫入，沿用名稱正規化、別名去重、循環偵測，不繞過任何既有驗證
- 建立可重用的非同步任務基礎建設，日後股價／新聞定期同步共用同一套
- 生成的每一筆資料可追溯到產生它的模型與任務

**Non-Goals:**
- 不實作投顧報告等外部文件的解析匯入（格式未定，待實際文件到手後另開 change）
- 不生成市佔率數字——無可查證來源時不得產生（`design D4`），市佔率走文件解析路徑
- 不做任務的暫停／取消（`ais-backend` 的 `JobContext` 有，但本專案尚無此需求，YAGNI）
- 不做前端、權限控管

## Decisions

### D1：自己用原生 Spring AMQP 實作，不引用 ais-backend 的 job-queue

`ais-backend` 的 `job/consumer` 依賴 `com.profetai.aistudio.jobqueue.*`（`@JobConsumer`、`JobContext`、
`JobPayload`），而 `JobProducer` 是打 `jobQueueProperties.getApi().getBaseUrl()`——
**那是一個獨立的 job-queue 微服務**，industrymap 沒有部署該服務。

- **採用**：原生 `spring-boot-starter-amqp`（`@RabbitListener` + `RabbitTemplate`）自行實作
- **替代方案**：引入 `job-queue-spring` / `job-queue-client` 依賴 → 本機 `~/.m2` 雖有 1.2.2 快取，
  但內部 Nexus（`nexus.profetai.org`）在此環境連不通，且缺少對應的微服務，捨棄

### D2：沿用 ais-backend 的 queue 組織模式

雖然不用它的函式庫，其組織方式經過實戰驗證，值得照搬：

- **每種任務一個獨立 queue**，各自帶 consumer timeout 與 retry policy 常數，集中在 `job/config/RabbitConfig`。
  不共用單一大 queue——拆解任務慢，混在一起會拖垮其他任務
- **consumer 分主流程與死信兩個處理路徑**：主流程重試耗盡後進 DLQ，由死信處理記錄失敗原因並將任務標為失敗，
  而不是讓訊息無聲消失
- **交易提交後才送訊息**：以 `TransactionSynchronizationManager` 註冊 `afterCommit` 再送出
  （對應 `ais-backend` 的 `sendJobAfterCommit`）。先送訊息後 commit 會讓 consumer 撈到還不存在的任務資料

### D3：任務狀態存資料庫而非 Redis

`ais-backend` 以 Redis 傳遞 job 狀態，但本專案尚未引入 Redis，且拆解任務數量級低（人工觸發、一天數十次）。

- **採用**：新增任務狀態表，記錄狀態、進度、觸發參數、結果摘要與失敗原因，走 Flyway migration
- **理由**：不為了單一功能引入新的基礎設施；任務紀錄本身有查詢與稽核價值，存 DB 比存 Redis 合適
- **替代方案**：Redis → 需新增依賴與部署元件，且任務歷史會過期消失，捨棄

### D4：LLM 回傳結構化結果，不接受自由文字

拆解結果要直接寫進八張表，任何解析歧義都會變成髒資料。

- **採用**：要求模型回傳固定結構（節點清單、組成關係清單、公司與角色清單），
  client 負責解析與型別驗證；格式不符視為該次任務失敗，記錄原始回應供除錯，**不做猜測性修補**
- **對齊 `.claude/rules/code-style.md`**：client 的失敗一律轉為明確狀態或帶語意的例外，
  禁止 `catch (Exception)` 後回傳空值當作沒事——那會讓一次失敗的拆解看起來像「這個產品沒有零件」

### D5：寫入前必須先比對既有節點

這是 `design D9`（DAG 節點共用）能否成立的關鍵。LLM 每次生成都可能吐出
「WiFi模組／無線網路模組／WLAN Module」指向同一個東西。

- **採用**：對每個生成的名稱先做正規化並查既有節點與別名，命中則沿用該節點，
  未命中才新建。此邏輯**必須走既有的 `ItemService`**，不另寫一套
- **理由**：繞過 service 直接寫 DB 會讓循環偵測與別名衝突檢查同時失效，
  這在 `.claude/rules/architecture.md` 已明列為禁止事項

### D6：生成資料一律為草稿，且來源可追溯到任務

- 來源類型固定為 AI 生成、必帶信心度（既有 `ProvenanceValidator` 已強制）
- **來源明細須包含模型識別與任務 id**，讓事後能回答「這筆奇怪的資料是哪次拆解、哪個模型產生的」
- 審核狀態一律草稿，人工審核走既有的批次審核端點——本次不新增任何審核路徑

### D7：API key 走環境變數

`CLAUDE.md` 禁止事項明列不得將 API key 寫入程式碼、設定檔或 log。
沿用既有的 `.env` 機制（`spring.config.import=optional:file:.env[.properties]`），
`application.properties` 只放 `${LLM_API_KEY:}` 這類佔位。log 輸出時不得帶入 key。

## Risks / Trade-offs

- **LLM 幻覺產生不存在的公司或零件** → 全部進草稿、強制人工審核；來源明細記錄模型與任務 id 供追溯。
  這是既有設計已承擔的風險，本次不改變
- **同義異名仍可能漏網**（D5 只能擋正規化後相同或已登記別名者）→ 接受殘留風險，
  審核階段由人判斷；日後可補實體合併工具
- **拆解深度過大導致任務過久或成本失控** → 端點強制指定深度上限，超過即拒絕；
  consumer 設 timeout，逾時進 DLQ
- **RabbitMQ 成為新的部署依賴** → 本機開發需額外跑一個 RabbitMQ；
  整合測試不得依賴真實 broker，consumer 邏輯抽成可單獨測試的 service
- **首次建立 queue 基礎建設，模式定錯會影響日後所有非同步任務** → 因此刻意照搬 ais-backend
  已驗證的組織方式（D2），而非自創

## Migration Plan

1. 新增任務狀態表的 Flyway migration，版號依 `.claude/rules/flyway.md` 查主線最新一支後遞增
2. 新增設定項（RabbitMQ 連線、LLM 端點與模型、逾時、深度上限），全部給預設值，
   未設定時應用程式仍能啟動（拆解功能不可用但不影響既有 API）
3. 無既有資料需轉換

## Open Questions

- 拆解的提示詞（prompt）要放程式碼常數還是設定檔？放設定檔可不重啟調整，
  但版本控管與可重現性較差——待實作時依調整頻率決定
- 一次拆解要不要同時產生供應公司，還是分成「拆零件」與「找公司」兩種任務？
  合併呼叫次數少但單次輸出大、易出錯；分開則流程長。待第一版實測 LLM 輸出品質後決定
- consumer 的重試次數與退避策略需依實際 LLM 失敗率調整，第一版先取保守值
