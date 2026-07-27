# industry-map-model Specification

## Purpose
產業地圖的骨架：把產品與零件收斂成單一遞迴實體（主機板在 PC 語境是零件、在自己語境是產品，
二分會逼出一個不存在的界線），節點代表品類而非具體型號，並以全站共用的節點串成 part-of 有向無環圖。
「同一個東西全站只有一個節點」是這裡的核心約束——唯有如此，「這顆零件最後裝進了哪些產品」
這類跨產業回溯才查得出來。part-of（組成）與 is-a（細分類型）刻意分開儲存也分開回傳。
## Requirements
### Requirement: 品類節點以單一遞迴實體表示

系統 SHALL 以單一 `item` 實體表示產品與零件，兩者不分表。每個 `item` 代表一個**品類**
（例：腳踏車、變速器、PCB、WiFi 模組），而非具體型號。`item` MUST 具備正規化名稱作為唯一鍵，
且 MUST 可標記是否為終端成品。

#### Scenario: 同一節點同時是零件與產品
- **WHEN** 主機板已存在為 `item`，且已建立「PC → 主機板」與「主機板 → PCB」兩條組成關係
- **THEN** 主機板 SHALL 同時作為 PC 的下層節點與 PCB 的上層節點，無需建立第二筆 `item` 資料

#### Scenario: 正規化名稱重複時拒絕建立
- **WHEN** 嘗試建立正規化名稱與既有 `item` 相同的節點
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突

### Requirement: 組成關係為可共用節點的有向無環圖

系統 SHALL 以 `item_composition` 表示 part-of 組成關係。同一個 `item` MUST 可被多個上層節點共用
（例：PCB 同時掛在主機板、汽車、家電之下），且系統 MUST NOT 為此複製節點。

#### Scenario: 零件被多個上層共用
- **WHEN** 查詢 PCB 這個 `item` 的所有上層節點
- **THEN** 系統 SHALL 回傳主機板、汽車、家電等全部上層節點，且三者指向同一筆 PCB 資料

#### Scenario: 反向查詢跨產業關聯
- **WHEN** 查詢某零件所屬的所有終端成品
- **THEN** 系統 SHALL 沿組成關係向上回溯並回傳所有可達的終端成品節點

### Requirement: 組成關係必須標記必要性

每筆 `item_composition` MUST 帶有必要性（標配、常見、選配），用以表達品類層的組成並非總是成立
（例：單速腳踏車沒有變速器）。

#### Scenario: 依必要性篩選組成清單
- **WHEN** 查詢腳踏車的組成零件並指定只取標配
- **THEN** 系統 SHALL 只回傳必要性為標配的零件，排除選配零件

#### Scenario: 未指定必要性時拒絕寫入
- **WHEN** 建立組成關係但未提供必要性
- **THEN** 系統 SHALL 拒絕寫入並回傳 400 驗證錯誤

### Requirement: 細分類型與組成關係分離

系統 SHALL 以 `item.parent_category_id` 自我外鍵表達 is-a 細分類型（例：車用 PCB is-a PCB），
且此關係 MUST 與 part-of 組成關係分開儲存與回傳。單一 `item` MUST 最多只有一個上層品類。

#### Scenario: 查詢節點時分別回傳兩種關係
- **WHEN** 查詢 WiFi 模組節點的下層資訊
- **THEN** 系統 SHALL 分別回傳「組成零件」（天線、射頻晶片）與「細分類型」（Wi-Fi 6E 模組）兩份清單，不混為一份

#### Scenario: 細分類型不得形成多重上層
- **WHEN** 嘗試將某 `item` 的 `parent_category_id` 指向第二個品類
- **THEN** 系統 SHALL 以單一欄位覆寫語意處理，任一時點該節點只有一個上層品類

### Requirement: 組成關係必須防止循環

系統 SHALL 於寫入 `item_composition` 時偵測循環，若新關係會造成節點經組成路徑回到自身，
MUST 拒絕寫入。此檢查於 service 層執行。

#### Scenario: 直接循環
- **WHEN** 已存在「A → B」組成關係，嘗試建立「B → A」
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突，訊息指出會造成循環

#### Scenario: 間接循環
- **WHEN** 已存在「A → B」與「B → C」，嘗試建立「C → A」
- **THEN** 系統 SHALL 偵測到經多層路徑的循環並拒絕寫入

#### Scenario: 合法的多重上層不視為循環
- **WHEN** 已存在「A → C」，嘗試建立「B → C」
- **THEN** 系統 SHALL 允許寫入，因 C 有多個上層屬 DAG 的正常情況

### Requirement: 零件別名支援去重與搜尋

系統 SHALL 以 `item_alias` 儲存零件同義詞（例：WiFi 模組／無線網卡／WLAN Module）。
別名 MUST 可用於查詢既有節點，避免同義異名產生重複節點。

#### Scenario: 以別名查得既有節點
- **WHEN** 以「無線網卡」查詢，且該詞已登記為 WiFi 模組的別名
- **THEN** 系統 SHALL 回傳 WiFi 模組節點

#### Scenario: 別名與既有節點名稱衝突
- **WHEN** 嘗試將某字串登記為別名，但該字串已是另一個 `item` 的正規化名稱
- **THEN** 系統 SHALL 拒絕寫入並回傳 409 衝突

### Requirement: 依產品展開組成樹

系統 SHALL 提供 API，給定一個 `item` 後回傳其組成樹，並 MUST 支援指定展開層數以避免一次回傳過大結構。

#### Scenario: 展開指定層數
- **WHEN** 查詢腳踏車的組成樹並指定深度為 2
- **THEN** 系統 SHALL 回傳兩層以內的組成節點，不再往下展開

#### Scenario: 查詢不存在的節點
- **WHEN** 查詢不存在的 `item`
- **THEN** 系統 SHALL 回傳 404

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
