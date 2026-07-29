## 1. 查詢筆數守衛（先建立度量，否則後面每一步都只能靠推論）

- [x] 1.1 新增 `QueryFanoutTest`（`@Tag("integration")`）：以 Hibernate `Statistics` 量測，
      同一支查詢分別在小扇出（3）與大扇出（20）下執行，斷言 SQL 筆數相同（design D4）
- [x] 1.2 五支查詢全部納入，此時**全紅**，且數字與提案記錄的實測值逐一吻合：
      `findCompositions` 6→23、`expandTree` 5→22、`findReachableEndProducts` 8→42、
      `findSuppliers` 6→23、`findRanking` 6→23

## 2. 批次抓取（A：兜底，且是 native query 的唯一解）

- [x] 2.1 `application.properties` 加 `spring.jpa.properties.hibernate.default_batch_fetch_size=50`
- [x] 2.2 `findRanking`（native query）轉綠——`@EntityGraph` 對它無效，只有這步治得到
- [x] 2.3 `findReachableEndProducts` **仍紅**（6→23），如預期：它的筆數來自走訪迴圈而非關聯載入。
      這一步同時確認守衛真的在量走訪，而不是只量到關聯載入

## 3. `@EntityGraph`（B：熱路徑顯性化，不依賴全域設定）

- [x] 3.1 `ItemCompositionRepository` 兩支加 `@EntityGraph(attributePaths = {"parentItem", "childItem"})`
- [x] 3.2 `CompanyItemRoleRepository` 兩支加 `@EntityGraph(attributePaths = "company")`
- [x] 3.3 確認生效：`findReachableEndProducts` 的祖先批次載入消失（23→22），
      其餘四支不再依賴批次大小

## 4. 遞迴 CTE（C：走訪本身）

- [x] 4.1 新增 `ItemReachableEndProductsTest`（真實 PostgreSQL），把 `ItemCompositionServiceTest`
      兩支 mock 測試的語意搬過來並只斷言結果；原本 `verify(..., never())` 那支改以
      「經由已駁回節點才到得了的終端成品不得列入」表達
- [x] 4.2 補齊 design D2 的四條語意 + 起點不列入 + 循環仍終止 + 多路徑不重複，共 7 支，
      **確認在現行 BFS 實作下全綠**（重構安全網成立）
- [x] 4.3 `ItemCompositionRepository.findReachableEndProducts` 新增遞迴 CTE native 查詢，
      關係走 `visibleStatusNames`、節點走 `exposableStatusNames`
- [x] 4.4 `findReachableEndProducts` 改呼叫該查詢，4.2 的 7 支測試**全數維持綠燈**
- [x] 4.5 ~~深度上限 20 層~~ **實作前推翻，不加**（design D2 已更新）：`UNION` 去重與原本的
      `visited` 集合等價，終止性已保證；加上限只會讓超出的終端成品靜默消失，
      呼叫端看到「這顆零件沒裝進那些產品」與事實相反。不在效能重構裡夾帶行為變更
- [x] 4.6 `QueryFanoutTest` 的 `findReachableEndProducts` 轉綠（扇出 3 與 20 皆 2 筆）
- [x] 4.7（追加）刪除 `ItemCompositionServiceTest` 中兩支已失效的 mock 測試並留下說明，
      保留不碰 SQL 的 404 測試

## 5. 收尾

- [x] 5.1 `./mvnw clean verify` 全量通過：surefire 194、failsafe 159
- [x] 5.2 改動前後對照（扇出 20，兩次皆為實測）：
      | 查詢 | 前 | 後 |
      |---|---|---|
      | `findCompositions` | 23 | 3 |
      | `expandTree(depth=1)` | 22 | 2 |
      | `findReachableEndProducts` | 42 | 2 |
      | `findSuppliers` | 23 | 3 |
      | `findRanking` | 23 | 4 |
      `findRanking` 比其他多一筆是因為它走 native query，公司只能靠批次抓取補撈（`@EntityGraph` 無效），
      那一筆是批次載入本身，不隨扇出成長
- [x] 5.3 `.claude/rules/testing.md` 補三節：查詢筆數守衛的登記與斷言寫法、
      三種對策的適用範圍、邏輯移進 SQL 時測試要跟著搬
- [x] 5.4 以 fresh-context diff review 審查（`/code-review`），2 筆 findings 皆成立且皆已修
      （reviewer 兩筆都附了實測，非推論）：
      - **CTE 在循環資料下會回傳起點自己**：anchor 從直接上層起算只擋得住無循環的資料，
        遞迴步驟未排除起點，循環把路徑繞回起點時它就成為「可達的上層」。
        根因是改寫時只對照了迴圈裡看得見的四條語意，漏掉迴圈外的 `visited.add(itemId)`——
        它同時擔任「起點不列入」的守衛，卻因為寫在迴圈之外而沒被當成語意的一部分。
        修法：最終 SELECT 加 `i.id <> :itemId`。補上「起點是終端成品 × 位於循環中」這一格，
        原本兩支測試各自只覆蓋一個條件，交叉情境無人守
      - **查詢筆數守衛偵測不到 `@EntityGraph` 被移除**：兩種扇出（3、20）都小於批次值 50，
        批次抓取把兩者都壓成一次 `IN`，於是「筆數相同」在有沒有 `@EntityGraph` 都成立。
        修法：大扇出改為 60（跨過批次值），只有真的 join fetch 才恆定。
        已用變異測試確認守衛真的會紅（拿掉一個 `@EntityGraph` → `findSuppliers` 4→5 而 fail）。
        `findRanking` 是 native query 無法 join fetch，該格保留在批次值以下並寫明守得住什麼
- [x] 5.6（追加）兩筆 findings 寫入 `~/.claude/dev-errors/error-log.md`；
      `.claude/rules/testing.md` 的扇出規則已改寫（原本寫「兩種扇出都要小於批次值」，正是這個洞）
- [x] 5.5 不記 CHANGELOG：深度上限取消後本次**沒有任何一處改變回傳內容**，
      純效能改善，使用者不可感知
