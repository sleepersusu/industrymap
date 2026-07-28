# Architecture Patterns（架構模式）

本文件描述 `industrymap` 預計採用的後端架構：Spring Boot 為核心，結合同步 API、
非同步 job / queue（外部資料抓取與同步）、排程與外部服務整合。
分層主軸：Controller → Service → Repository / Clients / Job Producer →
PostgreSQL / RabbitMQ（或等效 queue）/ Redis（快取）/ 外部資料來源（股價、新聞、專利、公司登記資料）。

沿用 `ais-backend` 的整體模式（layered + orchestration、job/queue 處理外部呼叫、
`ServerResponse<T>` / `ServerException` 回應與例外慣例），但領域改為產業地圖。

## 開發主軸與分期（重要）

**主軸是「產業地圖」本身**：`Product` → `Component` → `Company` 的拆解與對應關係，
才是核心資料模型與近期實作重點。`Patent`（專利）、`StockPrice`（股價）、`NewsItem`（新聞）、
公司合作對象等屬於**後期逐步擴充**的情資層，建在核心地圖穩定之後，不必第一階段就一次到位。

- 第一階段：把 `Product` / `Component` / `Company` 的關聯資料模型、CRUD、查詢 API 建好，
  能回答「這台腳踏車的零件分別來自哪些公司」。
- 後續階段：才逐步接上外部資料來源（股價、新聞、專利、公司間合作關係），且這些多半需要
  非同步 job / queue（見下方「非同步」段落），不影響核心地圖查詢的即時性。
- 因此 `clients/`（外部股價 / 新聞 / 專利來源）、`job/`（同步排程）等 package 先預留位置即可，
  不需要在專案初期就實作完整。

## 核心領域模型（第一階段已落地，見 `openspec/changes/add-industry-map-core-model/design.md`）

**產品與零件不分表**：主機板在 PC 語境是零件、在自己語境是產品，二分會逼出一個不存在的界線，
因此合併為單一遞迴實體 `Item`（design D1），節點為**品類**而非具體型號（D2）。

| 實體 | 資料表 | 狀態 | 說明 |
|------|--------|------|------|
| `Item` | `item` | 已落地 | 品類節點（腳踏車、變速器、PCB）；`is_end_product` 標記終端成品；`parent_category_id` 自我 FK 表達 is-a 細分類型 |
| `ItemAlias` | `item_alias` | 已落地 | 零件同義詞，正規化別名全域唯一，供去重與搜尋 |
| `ItemComposition` | `item_composition` | 已落地 | part-of 組成關係，DAG 且節點全站共用，帶必要性（標配／常見／選配） |
| `Company` | `company` | 已落地 | 公司主檔；未上市公司不因缺代號而無法登錄 |
| `CompanyAlias` | `company_alias` | 已落地 | 公司同義詞 |
| `CompanyIdentifier` | `company_identifier` | 已落地 | 公司代號（交易所／統編／DUNS），`unique(type, value)`，每公司至多一筆 `is_primary` |
| `CompanyItemRole` | `company_item_role` | 已落地 | 公司對零件的角色（設計／製造／代工組裝／品牌／封測），唯一鍵含角色 |
| `MarketShare` | `market_share` | 已落地 | 市佔率，必帶期間／地區／口徑；唯一鍵含來源，讓衝突數值並存 |
| `ProvenanceEntity` | （`@MappedSuperclass`） | 已落地 | 八張內容表共用的來源與審核欄位群 |
| `CompanyRelation`（公司合作關係） | — | 後期 | 公司之間的供應鏈 / 合作關係 |
| `Patent`（專利） | — | 後期 | 公司持有、與特定零組件相關的專利 |
| `StockPrice`（股價） | — | 後期 | 公司股價時間序列，定期同步；掛在 `CompanyIdentifier` 的主要識別碼上 |
| `NewsItem`（新聞） | — | 後期 | 與公司相關的最近新聞，定期抓取 |

**兩種關係刻意分離**（design D4）：`part-of` 走 `item_composition`（多對多、可多上層），
`is-a` 走 `item.parent_category_id`（單一欄位、至多一個上層品類）。混在一張表無法用 constraint
守住「is-a 不會有多重上層」，因此分開儲存也分開回傳。

**審核目標可用自然鍵定位**：查詢回應刻意不曝露公司內部 id，若審核只收 id，就會有資料進得去、
出不來的類型（公司識別碼即是）。因此審核端點同時支援內部 id 與自然鍵，各類型的自然鍵即其資料庫唯一鍵，
解析集中在 `service.review.NaturalKeyResolver` 一處（新增內容表時只要多註冊一行）。
批次建立端點（`service.bulk`）的回應一併回傳同一組自然鍵，讓「建立 → 審核」兩次呼叫就走得完。

**跨表限制由 service 把關**：別名不得撞到其他節點／公司的正規化名稱、組成關係不得形成循環，
這兩者 PostgreSQL 都表達不了，一律走 service（`ItemService`、`ItemCompositionService`）。
繞過 service 直接寫 DB 就會失效，日後的批次匯入必須共用同一組檢查。

## Package 對照（★ 為第一階段已落地）

| Package | 職責 |
|---------|------|
| `controller` ★ | REST API 入口，`@Valid` 驗證，回 `ServerResponse<T>`；`ProductController`、`ItemController`、`CompanyController`、`SupplyRelationController`、`ReviewController`、`BulkAuthoringController` |
| `service` ★ | 業務邏輯，依領域切分：`service.item`、`service.company`、`service.supply`、`service.review`、`service.bulk`。**單一 service 上限 500 行，超過就拆** |
| `repository` ★ | Spring Data JPA 資料存取。**SQL 一律寫在這層，service 只呼叫；手寫查詢用 `@Query(nativeQuery = true)`，enum 以字串傳入** |
| `model` ★ | JPA Entity（`Item`、`ItemAlias`、`ItemComposition`、`Company`、`CompanyAlias`、`CompanyIdentifier`、`CompanyItemRole`、`MarketShare`、`ProvenanceEntity`） |
| `payloads` ★ | API request / response 契約（複數，對齊 `ais-backend` 慣例）；依領域分 `payloads.item`、`payloads.company`、`payloads.supply`、`payloads.review`、`payloads.bulk`。含 `ServerResponses` 這層 `ResponseEntity` 包裝，讓 controller 不重複樣板 |
| `enums` ★ | `SourceType`、`ReviewStatus`、`ReviewTargetType`、`CompanyRole`、`Necessity`、`IdentifierType`、`ShareMetric`、`PeriodType` |
| `helper` ★ | `ProvenanceValidator`（來源欄位共用驗證）、`ReviewScopes`（查詢的審核範圍）、`CompanyReferences`（公司對外識別的唯一組裝規則） |
| `util` ★ | `NameNormalizer`（名稱正規化，無外部相依） |
| `clients` | 外部資料來源封裝：股價行情 API、新聞來源 API、專利檢索（如智慧財產局 / Google Patents）、公司登記 / 公開資訊觀測站 API |
| `job` | 背景任務：`config`（queue 設定）、`producer`、`consumer`、`scheduler`、`service`——用於定期同步股價 / 新聞 / 專利 |
| `scheduler` | 排程任務入口（觸發 job producer） |
| `config` | Spring 組態（Async、Security、WebClient、Flyway、Properties） |
| `advice` ★ / `exceptions` ★ / `events` | 橫切與共用；`GlobalExceptionHandler` 已涵蓋 `ServerException` 與各類驗證失敗（400） |

## 實際採用的模式

- **Layered + Orchestration**：Controller → 主 Service → 輔助 Service / Client / Producer → Repository。不把所有邏輯塞單一巨型 Service。
- **Job / Queue**：呼叫外部股價 / 新聞 / 專利來源一律走 `job/producer` 送 queue、`job/consumer` 消費，
  不在 request thread 內同步等待外部 API（避免外部來源慢/掛掉拖垮 API 回應）。狀態與進度可透過 Redis 傳遞。
- **Transaction boundary**：長流程拆分交易，避免長交易持有連線；只讀查詢用 `@Transactional(readOnly = true)`；避免 self-invocation 觸發交易。
- **外部整合**：對外呼叫集中在 `clients/*`，錯誤轉為 `ServerException` 或明確狀態；股價/新聞來源不穩時優先走 job 流程並保留上次成功資料（不因單次抓取失敗清空既有資料）。

## DTO / Payload / Entity

- API 契約主要用 `payloads`；轉換以手動組裝、builder 為主，不引入 ModelMapper / MapStruct（對齊 `ais-backend` 現況）。
  entity → response 的組裝寫成 response payload 上的靜態 `from(...)`（如 `ItemResponse.from(item)`），controller 保持薄。
- 回傳形狀明顯不同於 entity 時建 payload；例如組成樹回應是 `ComponentNode` 這種遞迴 payload，而非直接回 entity。
- **查詢條件也用 payload**：GET 端點不逐個接 `@RequestParam`，而是收斂成 `*Query` 物件以
  `@Valid @ModelAttribute` 綁定（`ComponentTreeQuery`、`MarketShareQuery`、`SupplierQuery`、`ReviewScopeQuery` 等），
  驗證規則跟著條件本身走。資源識別仍留在路徑上（`{id}` / `{code}`）。
- 能用 `jakarta.validation` annotation 表達的規則一律用 annotation；只有需要查 DB 的規則
  （重複、循環、跨表衝突）才留在 service。來源欄位另有 `ProvenanceValidator` 在 service 層再擋一次，
  讓未來的批次匯入等非 HTTP 進入點套用同一組規則。

## 審核狀態過濾是查詢的預設義務，不是選配

此規則由 `dev-mistake-digest` 升格（2026-07-28）：同一個模式犯了 3 次，分別漏掉識別碼、別名、
以及「角色所指向的品類節點」的 `review_status`，另有 1 次是同一過濾套用過頭而改變了 fallback 路徑。
三次都不是不知道規則——`ReviewScopes` 就是為此存在，`ItemCompositionService.buildNode` 也早已示範
「關係已驗證但指向已駁回節點」的處理。**規則與示範都在，缺的是寫新查詢時的檢查點。**

新增或修改任何對外查詢時，必須逐項確認：

1. 主查詢的表有 `review_status` → 必須過濾。
2. 每個 join / 子查詢的表有 `review_status` → 必須過濾。
3. 每個關係**指向**的實體有 `review_status` → 必須過濾。**這項最常漏**。
4. 判定該欄位屬**關係**還是**實體**：
   - 關係（組成、供應角色、市佔率）跟著呼叫端的 `includeDrafts` 走 → `ReviewScopes.visibleStatusNames`
   - 實體（品類節點、公司、別名、識別碼）只擋 REJECTED → `ReviewScopes.exposableStatusNames`
5. 過濾必須發生在**計數、分頁與組裝錯誤訊息之前**——曾有已駁回的識別碼被寫進 409 訊息而外露。

判斷「這裡不需要過濾」時，必須在該查詢的 javadoc 寫明理由，不得留白。

## 禁止事項

- 不把長流程（尤其是呼叫外部股價 / 新聞 / 專利 API）塞進 Controller。
- 不把需要 repo / client / queue 的業務邏輯塞進 `util`。
- 不在同步 API 內直接呼叫外部資料來源阻塞等待；一律走 job / queue 或先讀快取。
