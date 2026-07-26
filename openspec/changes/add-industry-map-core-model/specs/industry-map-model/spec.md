## ADDED Requirements

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
