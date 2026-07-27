## ADDED Requirements

### Requirement: 終端成品可經 API 列出

系統 SHALL 提供端點列出終端成品，讓呼叫端在**不知道任何內部 id、不存取資料庫**的前提下
取得可進入的產品清單。回應 MUST 包含足以呼叫組成樹端點的識別資訊。

清單 MUST 支援分頁，並回傳總筆數。排序 MUST 穩定——相同查詢條件下重複翻頁
MUST NOT 出現漏筆或重複筆。

#### Scenario: 取得終端成品清單
- **WHEN** 呼叫終端成品列表端點且不帶任何條件
- **THEN** 系統 SHALL 回傳已驗證的終端成品，每筆帶有可用於查詢組成樹的識別資訊

#### Scenario: 不含非終端成品
- **WHEN** 資料庫同時存在終端成品與零件節點
- **THEN** 清單 SHALL 只含終端成品，MUST NOT 含零件節點

#### Scenario: 分頁
- **WHEN** 指定頁碼與每頁筆數
- **THEN** 系統 SHALL 回傳該頁資料，並一併回傳總筆數與總頁數

#### Scenario: 分頁順序穩定
- **WHEN** 以相同條件連續取得第一頁與第二頁
- **THEN** 兩頁的內容 MUST NOT 重疊，且合併後 MUST NOT 遺漏任何符合條件的節點

#### Scenario: 名稱模糊搜尋
- **WHEN** 指定名稱關鍵字
- **THEN** 系統 SHALL 只回傳名稱包含該關鍵字的終端成品

#### Scenario: 預設不含草稿
- **WHEN** 未明確指定納入草稿
- **THEN** 清單 SHALL 只含已驗證的節點

#### Scenario: 明確指定時才含草稿
- **WHEN** 明確指定納入草稿
- **THEN** 清單 SHALL 同時含已驗證與草稿節點

#### Scenario: 已駁回節點任何情況都不外露
- **WHEN** 以任何條件查詢，包含明確指定納入草稿
- **THEN** 清單 MUST NOT 含已駁回的節點

#### Scenario: 無資料時回空清單
- **WHEN** 查詢條件下沒有任何符合的終端成品
- **THEN** 系統 SHALL 回傳空清單與總筆數 0，MUST NOT 回傳 404

### Requirement: 品類節點可修正既有欄位

系統 SHALL 提供端點修正既有品類節點的 `displayName`、是否為終端成品、以及 is-a 上層品類。

修正採**全量替換**語意：請求 MUST 帶齊上述所有欄位，缺欄位 SHALL 回傳 400。
is-a 上層品類允許為空，表示該節點沒有上層品類。

#### Scenario: 修正顯示名稱
- **WHEN** 以新的顯示名稱修正既有節點
- **THEN** 系統 SHALL 更新該節點的顯示名稱與正規化名稱

#### Scenario: 修正終端成品標記
- **WHEN** 將終端成品修正為非終端成品
- **THEN** 該節點 SHALL 不再出現於終端成品清單

#### Scenario: 清空 is-a 上層品類
- **WHEN** 修正時將 is-a 上層品類指定為空
- **THEN** 系統 SHALL 移除該節點的上層品類

#### Scenario: 缺少必填欄位
- **WHEN** 修正請求未帶齊全部欄位
- **THEN** 系統 SHALL 回傳 400，訊息 MUST 指出缺少哪些欄位

#### Scenario: 節點不存在
- **WHEN** 修正一個不存在的節點 id
- **THEN** 系統 SHALL 回傳 404

### Requirement: 改名必須套用與建立相同的名稱衝突檢查

修正節點名稱時，新的正規化名稱 MUST NOT 與**其他節點**的正規化名稱相同，
也 MUST NOT 與**任何已登記的別名**相同（包含該節點自己的別名）。
違反時 SHALL 回傳 409，訊息 MUST 指出衝突對象的性質。

此檢查 MUST 與建立節點、登記別名所使用的規則一致——同一組條件 MUST NOT 有第二份實作。

#### Scenario: 新名稱與其他節點重複
- **WHEN** 將節點改名為另一個既有節點的名稱
- **THEN** 系統 SHALL 回傳 409，且該節點 MUST NOT 被修改

#### Scenario: 新名稱與已登記別名重複
- **WHEN** 將節點改名為某個已登記的別名
- **THEN** 系統 SHALL 回傳 409，且該節點 MUST NOT 被修改

#### Scenario: 新名稱與自己的別名重複
- **WHEN** 將節點改名為該節點自己已登記的別名
- **THEN** 系統 SHALL 回傳 409

#### Scenario: 名稱維持不變
- **WHEN** 修正時送出與現況相同的名稱
- **THEN** 系統 MUST NOT 因為「與自己重複」而回傳 409

### Requirement: is-a 上層品類變更必須防止循環

修正 is-a 上層品類時，系統 MUST 拒絕會造成循環的指定，包含節點指向自己，
以及沿 is-a 鏈上溯後回到自身的情況。違反時 SHALL 回傳 409。

此檢查與組成關係（part-of）的循環偵測是**兩條獨立的關係**，各自成立。

#### Scenario: 指向自己
- **WHEN** 將節點的 is-a 上層品類指定為自己
- **THEN** 系統 SHALL 回傳 409

#### Scenario: 造成 is-a 循環
- **WHEN** 節點 B 的 is-a 上層是 A，而將 A 的 is-a 上層指定為 B
- **THEN** 系統 SHALL 回傳 409，且 A MUST NOT 被修改

#### Scenario: 上層品類不存在
- **WHEN** 指定一個不存在的節點 id 作為 is-a 上層品類
- **THEN** 系統 SHALL 回傳 404

#### Scenario: part-of 關係不受影響
- **WHEN** 節點 A 的組成關係中含有 B，而將 A 的 is-a 上層品類指定為 B
- **THEN** 系統 SHALL 允許——兩條關係各自獨立，part-of 的存在 MUST NOT 阻擋 is-a 的指定
