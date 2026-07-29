## Context

三種手段治的不是同一件事，混談會做錯取捨：

| 手段 | 治的問題 | 治不到的 |
|---|---|---|
| `default_batch_fetch_size` | 代理逐筆初始化 | 迴圈本身送出的查詢 |
| `@EntityGraph` | 同上，但精準且顯性 | native query（Hibernate 不套用） |
| 遞迴 CTE | 走訪迴圈 | — |

實測（扇出 20）：現況 22–42 筆；只開批次抓取 → 3–4 筆，但 `findReachableEndProducts` 仍 23 筆。
那 23 筆裡只有 1 筆是關聯載入，其餘 21 筆是 BFS 每 pop 一個節點查一次邊。

## Goals / Non-Goals

**Goals:**
- 對外查詢的 SQL 筆數不隨結果筆數線性成長
- `findReachableEndProducts` 的走訪語意逐條保住，改寫不得靜默丟失任一條
- 留下會紅的守衛，讓 N+1 長回來時 build 就失敗

**Non-Goals:**
- 不改任何對外 API 的路徑、參數或回傳形狀
- 不追求「每支查詢都剛好 1 筆 SQL」——存在性檢查、批次載入各自有其理由，
  硬合併會把不相干的關注點綁在一起
- 不處理寫入路徑的查詢筆數（批次建立另有自己的取捨，不在本次範圍）

## Decisions

### D1：批次抓取與 `@EntityGraph` 兩者都要，不是二選一

- `@EntityGraph` 用在四支衍生查詢上：**意圖顯性**——這支查詢一定會用到該關聯，寫在查詢上，
  日後有人改查詢時看得到。且它是 join fetch，永遠 1 筆，不受批次大小影響
- `default_batch_fetch_size` 作為**兜底**：`MarketShareRepository.findRanking` 是 native query，
  Hibernate 不會對它套用 `@EntityGraph`；此外任何日後新寫的查詢在補上 `@EntityGraph` 之前，
  也先有一層保護
- **不只靠全域設定**：它是隱性的，讀單一查詢看不出關聯會不會被載入，而且批次大小一旦不夠
  （扇出大於設定值）又會退回多筆

批次大小取 50：市佔率排名與供應商清單的實際扇出遠小於此，一次 `IN` 就吃完。

### D2：`findReachableEndProducts` 改遞迴 CTE，語意逐條對照

現行 BFS 的語意有四條，SQL 必須逐條對上，這是本次最容易靜默出錯的地方：

| # | 現行語意 | CTE 對應 |
|---|---|---|
| 1 | 只沿**已驗證（或含草稿）**的組成關係向上 | 遞迴步驟的 join 帶 `review_status IN (:reviewStatuses)` |
| 2 | 已駁回的祖先**不列入結果** | 遞迴步驟 join `item` 並要求 `review_status IN (:exposableStatuses)` |
| 3 | 已駁回的祖先**不作為續走的起點** | 同上——被 join 濾掉的列不會進入下一輪遞迴，語意天生成立 |
| 4 | 已走訪過的節點不重複展開（既有資料若含循環仍須終止） | `UNION`（非 `UNION ALL`）去重即可終止 |

**不加深度上限**（原先規劃要加，實作前推翻）：`UNION` 的去重與現行 BFS 的 `visited` 集合等價，
終止性已經保證，上限並非必要。而它會引入**靜默截斷結果**的風險——超過上限的終端成品會無聲消失，
呼叫端看到的是「這顆零件沒有裝進那些產品」，與事實相反。它想防的「惡性資料拖垮查詢」在現行
BFS 之下同樣存在，不是本次要解的問題；在效能重構裡夾帶一個會改變回傳內容的行為變更，
取捨方向是錯的。真要設上限應另案，並且要先想清楚超限時該回什麼，而不是默默少給幾筆。

第 2 與第 3 條在 Java 版是兩行不同的程式（`continue` 前的判斷與迴圈是否 push），
在 CTE 裡收斂成同一個 join 條件——**這是簡化，不是省略**，因為「被濾掉的列不會進入下一輪」
本來就是遞迴 CTE 的語意。

**起點節點自身不列入結果**：現行 BFS 從 `itemId` 出發但只把「祖先」加進結果，
即使起點本身是終端成品也不列入自己。CTE 的 anchor 不得產出結果列。

### D3：測試從 mock 搬到真實資料庫

`ItemCompositionServiceTest` 的三支 `findReachableEndProducts` 測試 mock 掉 repository，
邏輯移進 SQL 後它們驗的東西不復存在——其中一支甚至用 `verify(..., never())` 斷言
「不曾查詢已駁回祖先的上層」，那是驗實作互動，正是 `testing.md` 禁止的寫法，
也正是它擋住重構的原因。

改寫為 `AbstractPostgresIntegrationTest` 底下的整合測試，逐條驗 D2 的四條語意 + 起點不列入。
mock 測試只保留「查無節點回 404」這種不碰 SQL 的部分。

### D4：查詢筆數以 Hibernate `Statistics` 斷言，且**斷言與扇出無關**

守衛的寫法決定它有沒有用：斷言「SQL 筆數 ≤ 某常數」會隨實作微調而反覆紅。
改為**同一支查詢跑兩種扇出，斷言筆數相同**——這直接對應「不隨結果筆數成長」這條要求本身，
而不是對應某個實作細節。實作多一次無關的查詢不會讓它紅，真的長回 N+1 才會。

`Statistics` 在測試中以 `setStatisticsEnabled(true)` 於執行期開啟，
不動 `application.properties`，也就不會多一個 Spring context。

## Risks / Trade-offs

- **全域批次抓取影響所有查詢** → 它只改變「已經要載入的關聯怎麼載」，不改變載入與否，
  語意不變；風險在於單次 `IN` 查詢變大，取 50 遠低於 PostgreSQL 的參數上限
- **CTE 把邏輯移進 SQL，可讀性與可測性下降** → 以 D3 的整合測試逐條補回，
  且 SQL 留在 repository 層符合既有規則。走訪邏輯本來就該由資料庫做，Java 版是把資料庫當作
  逐筆讀取的儲存體在用
- ~~深度上限是新增的行為~~ → 已於 D2 推翻，不加上限。因此**本次沒有任何一處會改變回傳內容**，
  這也讓「改寫前後同一組測試都必須綠」成為可執行的驗收標準
- **`@EntityGraph` 與 `@Query` 併用的限制** → 衍生查詢沒問題；若日後那幾支改寫成 native，
  `@EntityGraph` 會靜默失效，屆時得靠 D4 的守衛抓到
