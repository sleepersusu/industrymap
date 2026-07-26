## Context

industrymap 目前只有 Spring Boot 3.5.16 / Java 21 骨架，含共用的 `ServerResponse<T>`、
`ServerException`、`GlobalExceptionHandler`，資料庫為 PostgreSQL，`ddl-auto=validate` 搭配 Flyway，
`model/` 與 `repository/` 皆為空。

本設計的十項關鍵決定已於需求釐清階段逐項確認，本文件記錄決定內容與取捨理由，
供後續實作與 review 對照。約束條件見 `.claude/rules/architecture.md`、`api-design.md`、`code-style.md`。

## Goals / Non-Goals

**Goals:**
- 建立品類層的產業地圖核心結構，能回答「產品 → 零件 → 公司 → 市佔率」四段查詢
- 資料模型能容納 AI 生成初稿與人工審核並存，且每筆資料可追溯來源
- 節點共用（DAG）以支援跨產業反查，同時避免同義異名造成節點碎裂
- 為後續的股價、新聞、專利、公司合作關係預留掛載點

**Non-Goals:**
- 不做具體型號層（Giant TCR 2024 的實際 BOM），本次只到品類層
- 不實作 AI 生成流程本身、審核操作介面、外部資料源串接
- 不做數量欄位（品類層填不出數字），待日後型號層再議
- 不做公司之間的合作關係（`CompanyRelation`），屬後續 change

## Decisions

### D1：產品與零件合併為單一遞迴實體 `item`

主機板在 PC 語境是零件、在自己語境是產品；PCB 再往下還能拆銅箔與基板。
Product／Component 二分會逼出一個不存在的界線。

- **採用**：單一 `item` 表 + 自我參照的組成關係表，以 `is_end_product` 布林標記終端成品
- **替代方案**：Product／Component 兩張表 → 主機板歸屬永遠有爭議，查詢需 union 兩表，捨棄

### D2：節點為品類而非具體型號

使用者的三個目標問題（腳踏車有哪些零件、主機板上有什麼、誰市佔最大）全部是品類層敘述。
型號層需要拆機報告或供應鏈資料庫，取得成本高一個量級，且市佔率在型號層沒有意義。

- **採用**：品類層。日後若要型號層，可新增獨立實體並指向 `item`，不影響本次結構

### D3：組成關係為 DAG，節點全站共用

PCB、螺絲、馬達會出現在大量產品之下。若每個上層各自複製節點，市佔率要重複維護，
且「某公司橫跨哪些產業」查不出來——而那正是產業地圖的核心價值。

- **採用**：`item_composition(parent_item_id, child_item_id)` 多對多，節點共用
- **規格差異的處理**：車用 PCB 與手機 PCB 若市場與廠商實質不同，建為 PCB 的**細分類型子節點**，
  而非靠上層路徑區分（見 D4）
- **替代方案**：樹狀複製 → 資料重複、跨產業查詢失效，捨棄

### D4：`is-a` 用自我外鍵，與 `part-of` 分離

`主機板 → WiFi 模組` 是組成，`WiFi 模組 → Wi-Fi 6E 模組` 是細分類型。兩者混用會讓前端
清單出現「天線、射頻晶片、Wi-Fi 6E 模組」這種語意混亂的結果，市佔率的加總語意也會壞掉。

- **採用**：`part-of` 用 `item_composition` 表（DAG，可多上層）；`is-a` 用 `item.parent_category_id`
  自我外鍵（樹狀，至多一個上層品類）
- **理由**：is-a 是單一上層，單欄位即足夠，遞迴查詢用 recursive CTE 也更單純；
  兩種關係在儲存層即分離，DB 層自然守住「is-a 不會有多重上層」
- **替代方案**：單一 relation 表 + `relation_type` 欄位 → 兩種邊的限制不同（is-a 單上層、
  part-of 多上層），混在一張表無法用 constraint 守住，捨棄

### D5：公司代號用獨立識別碼表

代號不是單一欄位能裝的東西：類型不只一種（交易所代號／統編／DUNS）、數量不只一個
（台積電有 TWSE 2330 與 NYSE TSM）、且可能一個都沒有（SRAM 為美國私人公司）。

- **採用**：`company_identifier(company_id, type, value, is_primary)`，`unique(type, value)`，
  每家公司至多一筆 `is_primary`
- **後續效益**：接股價時直接取 `is_primary` 那筆，明確知道抓哪個市場
- **替代方案**：company 表上放 ticker／exchange／tax_id 欄位 → 多地掛牌只能記一個，
  日後加 DUNS／LEI 要改 schema，捨棄

### D6：公司與零件的關係帶角色

同一顆晶片，聯發科設計、台積電製造、日月光封測——只有一條「做這個零件」的關係會讓三家長得一樣。

- **採用**：`company_item_role(company_id, item_id, role)`，同組可多筆
- **原料供應不另設角色**：南亞做銅箔、銅箔是 PCB 的下層節點，「南亞是 PCB 上游」由遞迴路徑推導，
  不需在角色 enum 內重複表達

### D7：市佔率獨立表且必帶維度

市佔率會隨時間變動、隨地區不同、隨口徑（營收／出貨量）不同而有完全不同的排名。
塞進關係表當單一欄位，明年就是錯的。

- **採用**：`market_share(company_id, item_id, period_type, period_value, region, metric, share_percent, ...)`
- **不強制 FK 至 `company_item_role`**：市佔率資料常先於角色關係到達（報告先說 Shimano 七成），
  強制關聯會卡住匯入
- **衝突資料並存**：唯一鍵含來源，讓「來源 A 說 70%、來源 B 說 50%」兩筆並存，由使用者判斷

### D8：來源與審核欄位群套用於所有內容表

資料以 AI 生成初稿 + 人工審核方式進來。市佔率數字尤其是幻覺重災區，沒有來源的數字沒有價值。
這組欄位若第一版沒有，之後補上等於既有資料全部無從追溯。

- **採用**：所有內容表帶 `source_type`、`source_detail`、`confidence`、`review_status`、
  `reviewed_by`、`reviewed_at`；JPA 以 `@MappedSuperclass` 共用，避免八份重複宣告
- **查詢預設只回已驗證**：草稿需明確參數才納入，已駁回一律不外露

### D9：同義異名以別名表 + 硬性唯一鍵防堵

D3 的節點共用前提是「同一個東西全站只有一個節點」，而 AI 生成最擅長破壞這件事
（WiFi模組／無線網路模組／WLAN Module）。一旦碎裂，跨產業查詢與市佔率都會失真。

- **採用**：`item_alias`、`company_alias` 兩張別名表；`item` 以正規化名稱為唯一鍵，
  `company` 以正規化名稱為唯一鍵並輔以 `company_identifier` 的硬性唯一鍵
- **寫入前比對**：新增節點前先查名稱與別名，命中則沿用既有節點
- **已知限制**：外國未上市公司（如 SRAM）沒有任何硬性唯一鍵，只能靠名稱與別名，無法完全避免

### D10：循環偵測放在 service 層

`A → B → C → A` 會讓前端展開組成樹時無限遞迴。

- **採用**：service 層於寫入 `item_composition` 前，自 child 出發沿組成關係向下走訪，
  若可達 parent 則拒絕寫入
- **替代方案**：DB 層 trigger 或 constraint → PostgreSQL 無原生 DAG 循環約束，
  自寫 trigger 維護成本高且難測試，捨棄

## Risks / Trade-offs

- **AI 生成造成節點碎裂** → 別名表 + 寫入前比對（D9）；但外國未上市公司無硬鍵，
  需接受殘留風險，後續可補實體合併（merge）工具
- **DAG 共用節點造成語意過寬**（車用 PCB 與手機 PCB 混為一談）→ 以細分類型子節點切開（D4）；
  代價是需要人判斷何時該切，切太細會讓地圖零碎
- **循環偵測在 service 層，繞過 service 直接寫 DB 就失效** → 所有寫入路徑統一走 service；
  日後若開放批次匯入，匯入流程須共用同一組檢查
- **深層組成樹查詢效能** → API 強制指定展開層數，避免一次拉出整張圖；
  必要時後續加物化路徑或快取，本次不預先優化
- **市佔率允許衝突資料並存** → 查詢時可能回傳多個互相矛盾的數字，前端需一併呈現來源；
  這是刻意取捨，優於系統擅自選一個數字

## Migration Plan

1. 首批 Flyway migration 建立 8 張表與索引，命名依 `.claude/rules/flyway.md` 的
   `VYYYY_MM_DD_HHMMSS__<description>.sql` 格式
2. 全新專案無既有資料，不需資料轉換
3. `ddl-auto` 維持 `validate`，確保 entity 與 migration 不漂移
4. 回滾：本次為首批 migration，如需回滾直接重建資料庫

## Open Questions

- 正規化名稱的正規化規則（大小寫、全半形、空白、繁簡）需於實作時定義並寫成共用工具，
  否則「WiFi模組」與「wifi 模組」仍會產生兩個節點
- 角色 enum 的完整清單（目前擬定：設計、製造、代工組裝、品牌、封測）是否足夠涵蓋非電子業，
  待實際建資料時驗證
- 地區欄位採自由字串或受控清單，待第一批實際市佔率資料進來後決定
