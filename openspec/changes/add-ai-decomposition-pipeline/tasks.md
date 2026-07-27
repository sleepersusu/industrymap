## 1. 設定與常數

- [ ] 1.1 於 `application.properties` 新增 RabbitMQ 連線設定，全部給預設值，未設定時應用程式仍可啟動
- [ ] 1.2 新增 LLM 設定（端點、模型識別、逾時、最大重試），API key 以 `${LLM_API_KEY:}` 佔位取自 `.env`；確認 `.env` 已在 `.gitignore`
- [ ] 1.3 新增拆解深度上限設定，並以 `@ConfigurationProperties` 綁定為型別安全的組態類別
- [ ] 1.4 建立 `job/config/RabbitConfig`：集中 queue 名稱、consumer timeout、重試策略常數（參考 ais-backend 的組織方式，見 design D2）
- [ ] 1.5 建立 enum：`JobStatus`（PENDING / RUNNING / SUCCEEDED / FAILED）、`JobType`（DECOMPOSITION）

## 2. 任務狀態持久化

- [ ] 2.1 查主線 `db/migration` 最新版號後，建立任務狀態表的 Flyway migration（狀態、類型、觸發參數、開始／結束時間、結果摘要、失敗原因）
- [ ] 2.2 建立 `JobTask` entity 與 `JobTaskRepository`
- [ ] 2.3 撰寫 Repository 整合測試（`@Tag("integration")`），驗證欄位與索引實際生效
- [ ] 2.4 先寫失敗測試：結束時間不得早於開始時間；再實作 `JobTaskService` 的狀態流轉
- [ ] 2.5 先寫失敗測試：任務標記失敗時必須帶失敗原因，否則拋 400；再實作

## 3. Queue 基礎建設

- [ ] 3.1 建立 queue、exchange、binding 與死信佇列的宣告
- [ ] 3.2 先寫失敗測試：交易回滾時不得送出訊息；再實作 producer 的 `sendAfterCommit`（以 `TransactionSynchronizationManager` 註冊 afterCommit，見 design D2）
- [ ] 3.3 先寫失敗測試：交易提交後消費端必定查得到任務紀錄；再驗證送出時序
- [ ] 3.4 建立 consumer 骨架：主流程與死信兩個處理路徑
- [ ] 3.5 先寫失敗測試：重試耗盡後任務須標記為失敗並記錄原因，不得無聲丟棄；再實作死信處理
- [ ] 3.6 先寫失敗測試：執行逾時的任務不得停留在 RUNNING；再實作逾時處理
- [ ] 3.7 消費邏輯抽為可單獨測試的 service，consumer 只負責訊息收發與狀態更新（讓測試不需真實 broker）

## 4. LLM Client

- [ ] 4.1 建立 `clients/llm/` 子包與 request/response payload（參考 ais-backend 依外部服務分子目錄的做法）
- [ ] 4.2 先寫失敗測試：回應無法解析為預期結構時應拋帶語意的例外，不得回傳空值當作成功；再實作解析（design D4）
- [ ] 4.3 先寫失敗測試：呼叫逾時應轉為明確的失敗狀態；再實作逾時處理
- [ ] 4.4 先寫失敗測試：節點缺少名稱時應略過該筆並記錄原因，其餘正常回傳；再實作欄位驗證
- [ ] 4.5 先寫失敗測試：結構合法但空結果應視為成功產出 0 筆，非失敗；再實作
- [ ] 4.6 確認 log 輸出不含 API key（design D7）
- [ ] 4.7 撰寫 client 單元測試，全程 mock HTTP，禁止打真實 API

## 5. 拆解流程

- [ ] 5.1 先寫失敗測試：生成名稱命中既有節點別名時應沿用不新建；再實作既有節點比對（design D5，須走既有 `ItemService`）
- [ ] 5.2 先寫失敗測試：生成名稱正規化後與既有節點相同時應沿用；再補強比對
- [ ] 5.3 先寫失敗測試：全新名稱應新建節點；確認比對未誤判
- [ ] 5.4 先寫失敗測試：會造成循環的組成關係應被拒絕且不影響其餘關係寫入；再實作（沿用既有循環偵測）
- [ ] 5.5 先寫失敗測試：生成資料的來源類型須為 AI 生成、審核狀態須為草稿、來源明細須含模型與任務 id；再實作（design D6）
- [ ] 5.6 先寫失敗測試：拆解不得產生任何市佔率資料；再確認流程未觸及市佔率
- [ ] 5.7 實作結果摘要：記錄新建節點數、沿用節點數、寫入關係數、略過筆數與原因

## 6. API 層

- [ ] 6.1 建立 payloads：觸發拆解的請求、任務狀態回應、任務結果回應
- [ ] 6.2 先寫失敗測試：對不存在的節點觸發應回 404；再實作觸發端點
- [ ] 6.3 先寫失敗測試：深度超過上限應回 400 且訊息指出上限、深度未指定應回 400；再實作驗證
- [ ] 6.4 先寫失敗測試：觸發後應於數秒內回 202 與任務識別碼；再實作非同步交接
- [ ] 6.5 先寫失敗測試：查詢不存在的任務應回 404；再實作任務狀態查詢端點
- [ ] 6.6 補齊 OpenAPI 註解（`@Tag`、`@Operation`、`@ApiResponses`、`@Schema`）
- [ ] 6.7 撰寫 Controller 測試（`@WebMvcTest` + `@Tag("integration")`），驗證 202／400／404 語意

## 7. 端到端驗證

- [ ] 7.1 確認本機 RabbitMQ 可用，或記錄啟動方式於 `docs/`
- [ ] 7.2 對既有的腳踏車節點觸發一次真實拆解，附任務識別碼與最終狀態
- [ ] 7.3 驗證生成資料全為草稿且預設查詢看不到，附實際回應
- [ ] 7.4 取任務結果的定位資訊，以既有批次審核端點一次審核，驗證過程不需查資料庫
- [ ] 7.5 審核後查詢組成樹，確認新節點出現，附實際回應
- [ ] 7.6 記錄本次實測發現的問題（生成品質、提示詞效果、重複節點比例）寫入 design.md 供下一個 change 參考
- [ ] 7.7 更新 `docs/insomnia/industrymap.insomnia.json`，加入拆解與任務查詢請求

## 8. 收尾

- [ ] 8.1 執行 `./mvnw clean test-compile` 後 `./mvnw surefire:test` 確認快測全綠
- [ ] 8.2 執行 `./mvnw verify` 跑全量，附實際輸出
- [ ] 8.3 以 fresh-context diff review 審查，只修正確性問題
- [ ] 8.4 更新 `.claude/rules/architecture.md`：`clients/` 與 `job/` 由「預留」改為實際結構
- [ ] 8.5 更新 `docs/CHANGELOG.md` 記錄 AI 拆解上線
