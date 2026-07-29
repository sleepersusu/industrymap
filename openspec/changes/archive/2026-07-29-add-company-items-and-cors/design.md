## Context

`company_item_role` 這張表本來就是雙向的，只是至今只從零件那一側查過
（`GET /api/items/{id}/suppliers`）。本次補的是同一張表的另一個方向，
不新增資料、不改 schema，真正要決定的是**回應形狀**與**兩個既有守衛怎麼滿足**。

## Goals / Non-Goals

**Goals:**
- 公司詳情頁點得下去：知道一家公司，就能列出它供應的品類節點並繼續往下走
- 前端能從另一個 origin 呼叫 API
- 新端點依規則納入可見性矩陣與查詢筆數守衛

**Non-Goals:**
- 不做認證授權（本次刻意留下的已知風險，見 Risks）
- 不把既有端點分頁化
- 不改 `/api/items/{id}/suppliers` 的回應形狀（見 D1 的不對稱說明）

## Decisions

### D1：以品類節點為單位聚合角色，同一個零件只出現一次

一家公司對同一顆晶片可以同時是製造與封測（唯一鍵含角色，這是刻意的設計）。
回應有兩種形狀：

| 形狀 | 結果 |
|---|---|
| 每個 (零件, 角色) 一筆 | 晶片出現兩次，前端得自己 group |
| **每個零件一筆，帶角色清單** | 晶片一筆，`roles: [MANUFACTURE, PACKAGING_TESTING]` |

選後者。這個端點回答的問題是「這家公司做哪些零件」，答案的單位就是零件；
讓同一個零件出現兩次，等於把去重的責任丟給每一個呼叫端。

**刻意留下的不對稱**：`/api/items/{id}/suppliers` 是每筆一個角色，同一家公司也會出現兩次。
那一側有同樣的問題，但改它是破壞性變更，且不在本次要解的問題裡。
不因為「對稱比較好看」就把新端點也做成不好用的形狀——兩邊各自對它要回答的問題負責。

### D2：不分頁，與同層的 suppliers / market-share 一致

節點是**品類**不是型號（design D2），一家公司供應的品類數量級遠小於型號。
同一層的 `/suppliers`、`/market-share` 也都不分頁，這裡跟著一致。

風險是日後要加分頁會是破壞性變更（陣列 → `PageResponse`）。接受，理由是：
現在就分頁是為「可能不會發生的規模」付出不一致的代價，而真的需要時，
公司側的品類數會先出現在別的地方（例如列表回應變慢），不會沒有徵兆。

### D3：查詢條件收斂成 `CompanyItemQuery`，與 `SupplierQuery` 對稱

`role`（只列擔任該角色的零件）＋ `includeDrafts`。GET 端點不逐個接 `@RequestParam`
是專案既有規則，且驗證規則跟著條件本身走。

### D4：審核可見性——這個端點會觸及四張內容表

新端點必須登記進可見性矩陣，逐格對應：

| 表 | 層次 | 為什麼 |
|---|---|---|
| `company_identifier` | 主查詢 | 路徑的代號解析走它 |
| `company` | 主查詢 | 解析結果的公司本體，已駁回視為不存在 |
| `company_item_role` | 主查詢 | 供應角色本身，跟著 `includeDrafts` |
| `item` | 關係指向的實體 | **最容易漏的一格**：角色已驗證不代表它指向的節點還算數 |

第四格與 `CompanyRepository.findCompanies` 早就處理過的是同一個問題
（已驗證的角色掛在已駁回節點上）。那次是事後由 code review 抓到才補的，這次一開始就要有。

### D5：查詢筆數——`@EntityGraph` 一併載入 `item`

回應要讀每個節點的名稱與審核狀態，`item` 是 LAZY 關聯，不 join fetch 就是逐筆 N+1。
新增的 repository 查詢加 `@EntityGraph(attributePaths = "item")`，
並在 `QueryFanoutTest` 加一格、大扇出設在批次值（50）之上——低於批次值時
批次抓取會遮蔽差異，守衛在有沒有 join fetch 都會綠（上一個 change 實測踩過）。

### D6：CORS 允許來源可設定，預設只含本機開發

`industrymap.cors.allowed-origins` 以 properties 提供，預設 `http://localhost:5173,http://localhost:3000`
（Vite 與 CRA/Next 的慣用 port）。以 `WebMvcConfigurer.addCorsMappings` 實作而非
Spring Security——專案目前沒有 Security，為了 CORS 引入整套過重。

**不使用 `allowedOrigins("*")`**，即使現在沒有認證也一樣：萬用字元會在日後加上 cookie
或 Authorization header 時默默變成破口，而那時沒有人會回頭想起這個設定。
用具名清單，新增來源是一次明確的決定。

## Risks / Trade-offs

- **這個後端目前沒有任何認證，寫入端點（建立、審核、批次匯入）對外全開。**
  加上 CORS 等於讓瀏覽器也打得到這些端點。在本機開發沒問題，但
  **allowed-origins 不得放寬到不受控的來源，且在補上認證之前不得部署到公網**。
  這一條要寫進 spec 而不只是註解——它是本次刻意留下的風險，不是疏漏
- **D1 造成兩側形狀不對稱** → 已在 D1 說明取捨；若日後統一，是 `/suppliers` 那側改過來
- **不分頁** → D2 已載明，屬可接受的日後破壞性變更
