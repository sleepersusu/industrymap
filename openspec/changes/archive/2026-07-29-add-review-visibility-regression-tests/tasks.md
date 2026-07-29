## 1. 可見性矩陣與骨架

- [x] 1.1 盤點並記錄矩陣：10 個對外 GET 端點 × 各自可觸及的內容表（8 張表）。
      逐一確認觸及範圍時以 repository 的實際 SQL 為準，不憑端點名稱推測
- [x] 1.2 建立 `ReviewVisibilityMatrixTest`（`@Tag("integration")`），矩陣以資料結構表達，
      每格帶「端點、內容表、遺漏的層次」供失敗訊息使用（design D4）。
      **與 design 的差異**：`AbstractPostgresIntegrationTest` 是 `@DataJpaTest`，裝不起 controller 與
      service，無法從 HTTP 進入點驗證整條路徑；改為新增 `AbstractPostgresWebIntegrationTest`
      （`@SpringBootTest` + MockMvc + `@Transactional` 回滾），並把容器與 `FIXTURE_PREFIX` 抽到
      `PostgresTestDatabase` 共用，兩種基底連同一個資料庫，不會在同一個測試 JVM 內開兩份 PostgreSQL
- [x] 1.3 fixture 一律帶 `FIXTURE_PREFIX`，斷言限定在自建資料上，不對全表數字下斷言（design D3）

## 2. 覆蓋率守衛（本次的核心價值）

- [x] 2.1 先寫失敗測試：**刻意**在矩陣中略過一個既有端點，確認守衛會 fail 並指名該端點；
      再實作以 `RequestMappingHandlerMapping` 取出所有 GET 端點並比對矩陣（design D2）
      → 略過 `GET /api/items/{id}/market-share`，守衛 fail 並逐字指名該端點（`guard-red` 執行）；
      同時證實實際端點恰為 10 個，且無多餘登記
- [x] 2.2 補回略過的端點，確認守衛轉綠——這一步證明守衛真的會抓，而非恆綠的裝飾
- [x] 2.3 支援「該端點不觸及任何內容表」的登記形式：可通過但必須明示理由，不得省略登記
      → `EndpointCoverage.none(reason)` + `matrixEntryWithoutContentTable_shouldStateReason`

## 3. 直接外露：已駁回資料本身不得出現在回應中

- [x] 3.1 已駁回的識別碼不得列進 `GET /api/companies/{code}` 的回應（既有行為已正確，測試為綠）
- [x] 3.2 已駁回的品類節點不得出現在 `GET /api/products`、`GET /api/items`、`GET /api/items/{id}`
- [x] 3.3 已駁回的組成關係不得出現在 `GET /api/products/{id}/components`、
      `GET /api/items/{id}/compositions`、`GET /api/items/{id}/end-products`
- [x] 3.4 已駁回的供應角色不得出現在 `GET /api/items/{id}/suppliers`
- [x] 3.5 已駁回的市佔率不得出現在 `GET /api/items/{id}/market-share`
- [x] 3.6 已駁回的公司不得出現在 `GET /api/companies`（另含識別碼不得列進列表回應）

## 4. 間接影響：已駁回資料不得改變結果（三次實際缺陷有兩次屬此類）

- [x] 4.1 已駁回的別名不得使公司被搜尋到（`GET /api/companies?name=`）
- [x] 4.2 已駁回的品類別名不得使節點被解析到（`GET /api/items?name=`）→ **紅**，見 6.1
- [x] 4.3 已駁回的識別碼不得使合法的裸代號查詢變成 409（`GET /api/companies/{code}`）
- [x] 4.4 已駁回的識別碼不得被寫進 409 錯誤訊息的候選清單
- [x] 4.5 已駁回的公司不得使裸代號查詢變成 409（另含以代號取得已駁回公司應回 404）
- [x] 4.6 已駁回的資料不得計入任何分頁端點的 `totalElements`
      → 兩支分頁端點（`/api/products`、`/api/companies`）與各自的「不得出現」併為同一個斷言組

## 5. 關係指向的實體（三次中最不直覺、最晚被抓到的一層）

- [x] 5.1 已驗證的供應角色指向已駁回的品類節點時，不得使公司出現在 `GET /api/companies?companyRole=`
- [x] 5.2 已驗證的組成關係指向已駁回的子節點時，該枝不得出現在組成樹
- [x] 5.3 已驗證的組成關係指向已駁回的父節點時，不得出現在 `GET /api/items/{id}/end-products`
- [x] 5.4 已驗證的供應角色指向已駁回的公司時，不得出現在 `GET /api/items/{id}/suppliers`
- [x] 5.5 已驗證的市佔率指向已駁回的公司時，不得出現在 `GET /api/items/{id}/market-share`
- [x] 5.6（追加）已駁回的主要識別碼不得成為供應商／市佔率回應中的公司對外識別
- [x] 5.7（追加）已驗證的組成關係指向已駁回的節點時，不得出現在 `GET /api/items/{id}/compositions`
      → **紅**，見 6.1
- [x] 5.8（追加）路徑指名的節點本身已駁回時，組成樹／組成關係／終端成品回溯應回 404
      → **紅**，見 6.1、6.2

## 6. 收尾

- [x] 6.1 逐項檢視測試揭露的失敗：31 格中 5 格紅，**全部判定為「程式漏過濾」而非設計取捨，
      本次一併修正**（三筆已寫入 `~/.claude/dev-errors/error-log.md`）：
      - `ItemService.resolveVisibleByName`：只過濾最終取得的節點，漏過濾**用來命中它的別名**。
        已駁回的別名仍能把節點解析出來。改為別名命中後先檢查別名自身狀態，不再委給寫入用的
        `resolveByName`（那層 filter 看得到 Item、看不到中途的別名）
      - `ItemCompositionService.findCompositions`：只過濾關係自身狀態，漏過濾**關係另一端的節點**。
        同一個類別的 `buildNode` 早已處理過同型問題。回應只有 id 也算外露，改為兩端都過濾
      - `ItemCompositionService` 三支對外查詢（`expandTree` / `findCompositions` /
        `findReachableEndProducts`）：以不過濾狀態的 `getItem` 解析**路徑指名的節點**，
        已駁回節點被當成存在的資源回 200，組成樹更直接回傳它的名稱與狀態。
        新增 `getVisibleItem` 供對外查詢使用（`ItemService` 早已拆出 `getById` / `getVisibleById`，
        本類別沒跟著拆）
- [x] 6.2 已知的不一致已一併解決：指名已駁回的品類節點時，`/api/products/{id}/components` 原本
      回該節點、`/api/companies?itemId=` 回空清單。**判定「回該節點」為錯**——憲章層級的規則是
      已駁回一律不外露，而同一個節點在 `GET /api/items/{id}` 本來就回 404，
      組成樹回傳它的 displayName 與 reviewStatus 是直接外露，不是設計取捨。三支查詢統一為 404，
      不需另開 change
- [x] 6.3 `./mvnw clean verify` 跑全量，附實際輸出
- [x] 6.4 `.claude/rules/testing.md` 補一條：新增對外查詢端點時必須登記進可見性矩陣
- [x] 6.5 以 fresh-context diff review 審查（`/code-review`），只修正確性 findings。
      4 筆 findings，3 筆成立並已修，1 筆前提有誤：
      - **成立**：`/api/items/{id}/suppliers`、`/api/items/{id}/market-share` 完全不解析路徑上的 itemId，
        指名已駁回節點時照常列出資料；`/api/companies?itemId=` 的存在性檢查不看審核狀態，
        已駁回節點回 200 空清單而不存在的節點回 404，狀態碼本身就洩漏了該節點存在。
        矩陣漏了這三格 `ITEM × MAIN_TABLE`——守衛擋得住「端點沒登記」，擋不住「端點登記了但漏一格」。
        修法確立一條可測的不變量：**已駁回的節點必須與不存在的節點在每個端點上不可區分**，
        因此各端點沿用自己既有的「查無此節點」行為（公司列表 404、供應商與市佔率空清單），
        測試以「對照不存在的 id」表達，不寫死狀態碼
      - **成立**：所有「不得出現」的斷言都沒有前提檢查。查詢條件一改名端點回 400，
        `body.contains(...)` 天生為 false，整組矩陣會在沒執行到端點的情況下全綠還宣稱驗過了。
        新增 `okBody()` 先斷言 200 才取回應內容
      - **成立**：守衛以 `.controller` package 前綴篩，與路徑前綴有對稱的漏洞——
        日後 `web.PatentController` 會被靜默放行。改以根 package `com.profetai.industrymap` 判定，
        涵蓋本專案每一支 handler，springdoc 與 actuator 自然排除
      - **前提有誤，不修**：指 `pointsAtExposableItems` 引入 N+1。但 `Item` 的 `@Id` 標在欄位上，
        Hibernate 無法在代理上攔截 identifier getter（本專案 `CompanyIdentifierRepository` 的 javadoc
        對 `Company` 已寫明同一件事），原本的 `CompositionResponse.from` 呼叫 `getParentItem().getId()`
        就已經初始化代理。N+1 是既有特性而非本次引入，屬另案
- [x] 6.6 因 6.1 產生了使用者可感知的行為修正，已記入 `docs/CHANGELOG.md`（測試本身不記）
