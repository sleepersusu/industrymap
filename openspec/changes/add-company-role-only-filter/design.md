## Context

`add-company-listing`（已歸檔）把角色條件寫在 `itemId` 的 `EXISTS` 區塊內：

```sql
AND (CAST(:itemId AS bigint) IS NULL
     OR EXISTS (SELECT 1 FROM company_item_role r
                WHERE r.company_id = c.id
                  AND r.item_id = CAST(:itemId AS bigint)
                  AND r.review_status IN (:roleReviewStatuses)
                  AND (CAST(:companyRole AS text) IS NULL
                       OR r.company_role = CAST(:companyRole AS text))))
```

未指定 `itemId` 時整段短路為 true，角色條件從未參與比對。該 change 的 review 抓到這點，
以 `@AssertTrue` 回 400 讓實作與當時的 spec 一致——那是誠實化，不是能力補完。本 change 補完能力。

資料現況：85 筆供應角色，5 種角色（DESIGN／MANUFACTURE／ASSEMBLY／BRAND／PACKAGING_TESTING），
50 家公司。規模小，效能不是本次的約束。

## Goals / Non-Goals

**Goals:**
- `companyRole` 可獨立使用，語意為「對任何零件具有該角色」
- `itemId` + `companyRole` 併用時語意不變
- 內容與 count 兩支查詢維持條件逐條一致（`add-company-listing` design D2 的既有紀律）

**Non-Goals:**
- 不做多角色（`companyRole=DESIGN,MANUFACTURE`）——需要先確認前端是 OR 還是 AND 語意，
  兩種都合理且不可互推，猜錯比不做更糟
- 不做「排除某角色」（例如「只做設計不做製造」）——那是否定條件，SQL 是 `NOT EXISTS`，
  與本次的肯定條件不同形狀，且需求尚未明確
- 不預先補索引（見 Risks）

## Decisions

### D1：以「兩個條件各自可選」重寫 EXISTS 的守衛，而非拆成兩個 EXISTS

守衛條件從「`itemId` 為 null 就跳過」改成「兩者皆為 null 才跳過」，
`item_id` 的比對本身也變成可選：

```sql
AND (CAST(:itemId AS bigint) IS NULL AND CAST(:companyRole AS text) IS NULL
     OR EXISTS (SELECT 1 FROM company_item_role r
                WHERE r.company_id = c.id
                  AND (CAST(:itemId AS bigint) IS NULL
                       OR r.item_id = CAST(:itemId AS bigint))
                  AND r.review_status IN (:roleReviewStatuses)
                  AND (CAST(:companyRole AS text) IS NULL
                       OR r.company_role = CAST(:companyRole AS text))))
```

- **理由**：單一 `EXISTS` 讓「同一筆角色列必須同時滿足零件與角色」這件事直接由 SQL 表達。
  拆成兩個 `EXISTS` 會允許「A 零件的製造角色」與「B 零件的組裝角色」分別命中，
  併用時就變成錯的語意
- **易錯處**：`AND` 的優先序高於 `OR`，上面第一行若少了括號會被解析成
  `(itemId IS NULL AND role IS NULL) OR EXISTS(...)`——這正好是想要的，但**是碰巧而非明示**。
  實作時必須加上外層括號讓意圖顯性，並以「兩者皆不指定時不過濾」的測試守住

### D2：`@AssertTrue` 直接移除，不保留任何替代驗證

該驗證存在的唯一理由是「能力還沒做，不要靜默忽略」。能力補上後它就是純粹的阻擋。

- **同時移除**：`CompanyQuery.companyRole` 的 `@Schema` 說明「僅在指定 itemId 時有意義」須改寫

### D3：角色的審核範圍沿用 `roleReviewStatuses`，不另立規則

角色是**關係**不是實體，因此跟著呼叫端的 `includeDrafts` 走（`ReviewScopes.visibleStatusNames`），
與 `add-company-listing` 一致。獨立使用角色時這點更重要：未指定零件會讓掃描範圍變大，
草稿角色若被採計，「有哪些封測廠」會混入一批還沒審的猜測。

## Risks / Trade-offs

- **掃描範圍變大** → 不指定 `itemId` 時 `EXISTS` 只靠 `company_id` 收斂。
  85 筆角色的規模下無虞；本次**不預先補索引**，待資料量成長後依實際查詢計畫決定，
  避免加一個用不到的索引還要維護
- **SQL 條件複雜度上升** → 三個可選條件交織在同一個 `EXISTS`，優先序容易寫錯。
  對策是 D1 的顯性括號 ＋ 覆蓋四種組合（都不指定／只給零件／只給角色／兩者併用）的整合測試，
  且 count 與內容都要測——兩支查詢改動一致才不會出現「清單有資料但總筆數為 0」
- **既有測試需改寫語意** → `listCompanies_companyRoleWithoutItemId_shouldReturnBadRequest`
  斷言的是舊行為，必須改寫而非刪除，改寫後應驗證新語意確實生效
