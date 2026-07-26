## Why

industrymap 目前只有 Spring Boot 骨架，沒有任何資料模型，無法回答本專案存在的理由：
「一台腳踏車／主機板由哪些零件構成、每個零件有哪些公司在做、誰的市佔率最大」。

核心地圖（產品 → 零件 → 公司）是後續所有功能（股價、新聞、專利、公司合作關係）的掛載點，
必須先落地。且資料預計以 AI 生成初稿 + 人工審核方式進來，來源與審核欄位若不在第一版就位，
之後補上等於既有資料全部無從追溯，因此一併納入本次範圍。

## What Changes

- 新增品類層的產業地圖核心資料模型（8 張表）與對應 Flyway migration：
  - `item`：產品與零件合併為單一遞迴實體，以品類為節點（腳踏車、變速器、PCB、WiFi 模組）
  - `item_alias`：零件同義詞，供去重與搜尋使用
  - `item_composition`：part-of 組成關係，DAG（節點可被多個上層共用），帶必要性（標配／常見／選配）
  - `company`：公司主檔
  - `company_alias`：公司同義詞
  - `company_identifier`：公司代號（交易所代號／統編／DUNS），支援多重身分與未上市公司
  - `company_item_role`：公司對零件扮演的角色（設計／製造／組裝／品牌／封測）
  - `market_share`：市佔率，含期間、地區、口徑與來源
- `item` 以 `parent_category_id` 自我 FK 表達 is-a 細分類型（樹狀），與 part-of 組成關係分離
- 所有內容表加入共用的來源與審核欄位群（`source_type`、`source_detail`、`confidence`、
  `review_status`、`reviewed_by`、`reviewed_at`）
- 新增核心查詢 API：依產品展開零件組成樹、查零件的供應公司、查零件市佔率排名、查公司基本資料
- 寫入 `item_composition` 時於 service 層做循環偵測，避免前端無限展開

本次不含：股價／新聞／專利串接、AI 生成流程本身、審核操作介面。這些依賴本次的資料模型，屬後續 change。

## Capabilities

### New Capabilities
- `industry-map-model`: 產業地圖核心資料模型——item 遞迴品類節點、part-of 組成 DAG、is-a 細分類型、別名去重、循環偵測
- `company-registry`: 公司主檔與識別——公司基本資料、別名、多重代號（交易所／統編／DUNS）、未上市公司處理
- `supply-relation`: 公司與零件的供應關係——角色（設計／製造／組裝／品牌／封測）、市佔率（期間／地區／口徑）與排名查詢
- `data-provenance`: 資料來源與審核——來源類型、信心度、審核狀態流轉，套用於所有內容表

### Modified Capabilities
（無，本專案尚無既有 spec）

## Impact

- **新增程式**：`model/`、`repository/`、`service/`、`controller/`、`payloads/`、`enums/` 下的核心類別
- **資料庫**：首批 Flyway migration，建立 8 張表與索引；`spring.jpa.hibernate.ddl-auto=validate` 需與 migration 對齊
- **API**：新增 `/api/products/**`、`/api/companies/**` 路徑（見 `.claude/rules/api-design.md`）
- **既有程式**：沿用已建立的 `ServerResponse<T>`、`ServerException`、`GlobalExceptionHandler`，不改動
- **依賴**：無新增第三方依賴，現有 `pom.xml` 已涵蓋（JPA、Flyway、PostgreSQL、validation）
- **文件**：`.claude/rules/architecture.md` 的領域模型段落需依實際落地結果回填
