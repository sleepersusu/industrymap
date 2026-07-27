# 資料灌入作業手冊（人工 + Claude Code）

本文件供**以外部 AI 工具產生產業地圖資料、再經既有 API 灌入**的作業使用。
後端**零改動**——所有端點在 `add-natural-key-review-and-batch-write` change 已完成。

## 這份手冊的定位

| | 方案 A（後端內建 LLM） | **方案 B（本手冊）** |
|---|---|---|
| 拆解由誰執行 | 後端呼叫模型 API | 人 + Claude Code |
| 需要 API key | 是 | **否** |
| 需要 RabbitMQ | 是 | **否** |
| 後端改動 | 大 | **無** |
| 狀態 | `openspec/changes/add-ai-decomposition-pipeline`（**暫停中**） | 現行做法 |

方案 A 最大的未知是「提示詞怎麼寫才產得出乾淨結果」。本手冊的每一輪作業都應留下
**提示詞**與**品質觀察**，那是日後恢復方案 A 時的直接輸入。

---

## 一、前置：把服務跑起來

```bash
cd /Users/user/Desktop/industrymap && ./mvnw spring-boot:run
```

**⚠️ 不要用 `/actuator/health` 判斷是否就緒。** 它會回 `{"status":"DOWN"}`，
原因是 `spring-boot-starter-amqp` 在 classpath 上，Spring Boot 自動註冊了 RabbitMQ 健康檢查，
而 `application.properties` 目前尚無任何 `spring.rabbitmq.*` 設定，於是它以預設的 `guest/guest`
連線而被拒（RabbitMQ 限制 `guest` 僅能自 loopback 連線）。**這不影響任何業務 API。**

改用這個判斷就緒：

```bash
curl -s -G "http://localhost:8080/api/items" --data-urlencode "name=腳踏車"
```

回傳品類節點或「查無此品類節點」皆表示服務正常。

**⚠️ 查詢參數含中文時必須用 `-G` + `--data-urlencode`。** 直接寫
`"...?name=腳踏車"` 會被 Tomcat 以 400 擋下（未編碼的 UTF-8 出現在 query string），
而且回的是 HTML 錯誤頁不是 JSON，很容易誤判成服務有問題。

Swagger UI：http://localhost:8080/swagger-ui.html

---

## 二、五條不可違反的紀律

### 1. 建立節點前必須先解析既有節點

這是整套設計最不能踩的雷。`design D3/D9` 讓零件節點**全站共用**（PCB 同時掛在主機板、
汽車、家電之下都是同一筆），跨產業查詢與市佔率統計都建立在這個前提上。
一旦產生重複節點，「這家公司橫跨哪些產業」就會查錯，而且很難事後修。

**做法：拆解前先把既有節點與公司的完整清單撈出來，當成 background 餵給模型**，
而不是產完再一筆一筆查。逐一查只能擋住字面完全相同的名稱；把清單先給模型看，
它才有機會判斷「印刷電路板」跟既有的「PCB」是同一個東西——這種同義詞正是逐一查擋不住的。

清單指令見步驟 2。拿到清單後，要求模型在產出時對每個名稱明確標示是
**沿用既有節點（附 id）** 還是 **新建**。

寫入前再以解析端點覆核一次，作為第二道防線：

```bash
curl -s -G "http://localhost:8080/api/items" --data-urlencode "name=PCB"
```

- 查得到 → **沿用回傳的 id**，不要建新節點
- 查不到 → 才建立

此端點同時比對正規化名稱與別名。若發現模型產出的是既有節點的同義詞，
應改為登記別名（`POST /api/items/{id}/aliases`）而非新建節點——
別名登記後，下次拆解就擋得住同一個同義詞。

### 2. 公司必須查證存在，且確實做該產品

不得憑模型印象斷言「某某公司做某零件」。每一筆公司資料與供應角色，
**要嘛查得到官方登記與產品線佐證，要嘛公開資料的可信度達 80% 以上**，兩者取其一。

**台灣公司——優先走官方來源，兩件事都要查：**

| 要查什麼 | 去哪查 |
|---|---|
| 公司**確實存在**、代號正確 | [上市公司基本資料（政府資料開放平臺）](https://data.gov.tw/dataset/18419)、[證交所 OpenAPI](https://openapi.twse.com.tw/) |
| 未上市公司存在性、統一編號 | [商工登記公示資料查詢](https://findbiz.nat.gov.tw/) |
| **確實做該產品** | [公開資訊觀測站](https://mops.twse.com.tw/)的年報／法說會簡報產品線說明、公司官網 |

「查得到這家公司」與「這家公司做這個零件」是**兩件獨立的事**，都要成立才寫入。
公司存在但產品線對不上（例如它做的是另一類零件），該筆供應角色不可寫。

**外國公司**（Shimano、SRAM 等）台灣證交所查不到，改用當地交易所、公司官網產品頁或
年報佐證；查不到官方來源時適用下面的 80% 規則。

**80% 門檻——寫進 `confidence` 欄位：**

`provenance.confidence` 就是這個門檻的載體，取值 0–1：

- **`confidence >= 0.8`** → 可寫入。官方來源查證通過者通常落在 0.9–1.0；
  多個獨立公開來源一致者可給 0.8–0.9
- **`confidence < 0.8`** → **不要寫入**。與其寫一筆信心不足的資料等人審核，
  不如不寫——來源不足的資料進了資料庫，審核者也一樣無從判斷

`sourceDetail` 必須寫**實際查到的出處**（報告名稱、年報年度、網址），
不能只寫「Claude Code 拆解」——那不是來源，那是產生方式。

> **零件組成關係**（腳踏車有變速器、主機板有 PCB）屬常識，不適用本條，
> 直接採用即可。本條規範的是**公司**與**公司對零件的角色**。

### 3. 市佔率沒有可查證來源就不填

`design D4` 明訂：**禁止自行編造百分比**。市佔率是 LLM 幻覺重災區，
一個沒有來源的數字比沒有數字更糟——它會被當真。

- 找得到可靠出處（研究機構報告、公司年報、法說會簡報）→ 填，且 `sourceDetail` 必須是**真實出處**
- 只搜到二手部落格、來源與領域對不上 → **留空**

市佔率留空不影響零件樹與供應商查詢，排名查詢會回空清單（非 404），行為正確。

> 投顧報告到手後，市佔率走文件解析路徑另行匯入，不在本手冊範圍。

### 4. 所有資料一律進草稿，經審核才對外可見

寫入端點不接受指定審核狀態，一律為 `DRAFT`。對外查詢預設只回 `VERIFIED`，
`REJECTED` 任何情況都不外露。**灌完必須審核，否則查詢會是空的。**

### 5. `sourceType` 為 `AI_GENERATED` 時必須帶 `confidence`

否則回 400。`confidence` 的取值依紀律 2 的門檻決定，`sourceDetail` 必須寫實際查到的出處。

---

## 三、資料庫現況（2026-07-27 腳踏車輪次結束時）

> ⚠️ 此表為**腳踏車輪次**結束時的快照。2026-07-27 主機板輪次又新增了 21 個節點
> （id 241–261）與 17 家公司（id 242–258），詳見第七節作業紀錄。
> **不要拿這張表當最新清單**——每輪灌資料前一律重跑步驟 2 的查詢。

灌新資料前先確認有無重疊。目前既有節點：

| id | 名稱 | 狀態 |
|---|---|---|
| 70 | 腳踏車 ★成品 | VERIFIED |
| 71 | 車架 | VERIFIED |
| 72 | 變速系統 | VERIFIED |
| 73 | 輪組 | VERIFIED |
| 74 | 煞車 | VERIFIED |
| 75 | 鏈條 | VERIFIED |
| 76 | 前變速器 | VERIFIED |
| 77 | 後變速器 | VERIFIED |
| 78 | 飛輪 | VERIFIED |
| 79 | 貨架 | DRAFT |
| 34, 35 | 驗證用腳踏車、驗證用變速器 | DRAFT（走查殘留） |

既有公司：巨大機械、桂盟國際、Shimano、SRAM（皆 VERIFIED）；
另有「驗證用台積電」「驗證用公司B」為走查殘留（DRAFT）。

> 走查殘留資料因為是 DRAFT，不會出現在對外查詢，但**解析端點查得到**。
> 看到「驗證用」開頭的節點請忽略，不要拿來接關係。

---

## 四、作業流程

順序不可顛倒——組成關係需要節點 id，供應角色需要公司代號與節點 id。

### 步驟 1：研究與查證

用 Claude Code 蒐集資料時，對每一項主張要求出處，並依紀律 2 決定 `confidence`：

| 查證程度 | `confidence` | 可否寫入 |
|---|---|---|
| 官方來源查證通過（證交所／商工登記／年報） | 0.9–1.0 | ✅ |
| 多個獨立公開來源一致 | 0.8–0.9 | ✅ |
| 單一來源、或來源可靠度存疑 | < 0.8 | ❌ 不寫入 |

- **零件組成**（腳踏車有變速器）屬常識，不需查證出處
- **公司**與**公司對零件的角色**：適用紀律 2，兩件事都要成立——公司存在、且確實做該產品
- **市佔率**：適用紀律 3，一律需要真實出處，查不到就留空

### 步驟 2：撈出既有清單，當 background 餵給模型

**這一步要在拆解之前做**，不是產完再比對。

> ⚠️ 目前**沒有「列出所有節點」的 API**——`GET /api/items` 只能以名稱解析單筆。
> 因此清單直接查資料庫（唯讀，不繞過任何寫入驗證）。
> 這是既有 API 的缺口，已記於本文件第八節。

```bash
cd /Users/user/Desktop/industrymap
export PGPASSWORD=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)
PSQL=/Library/PostgreSQL/16/bin/psql

# 既有品類節點（含別名）
$PSQL -h localhost -U postgres -d industrymap -A -F$'\t' -c "
SELECT i.id, i.display_name, i.review_status,
       CASE WHEN i.is_end_product THEN '成品' ELSE '零件' END AS kind,
       coalesce(string_agg(a.display_alias, ', '), '') AS aliases
FROM item i LEFT JOIN item_alias a ON a.item_id = i.id
GROUP BY i.id ORDER BY i.id;"

# 既有公司（含代號與別名）
$PSQL -h localhost -U postgres -d industrymap -A -F$'\t' -c "
SELECT c.id, c.display_name, c.review_status,
       coalesce(string_agg(DISTINCT ci.identifier_value, ', '), '') AS codes,
       coalesce(string_agg(DISTINCT ca.display_alias, ', '), '') AS aliases
FROM company c
LEFT JOIN company_identifier ci ON ci.company_id = c.id
LEFT JOIN company_alias ca ON ca.company_id = c.id
GROUP BY c.id ORDER BY c.id;"

# 既有組成關係（避免重複建立）
$PSQL -h localhost -U postgres -d industrymap -A -F$'\t' -c "
SELECT p.display_name AS parent, ch.display_name AS child, ic.necessity, ic.review_status
FROM item_composition ic
JOIN item p ON p.id = ic.parent_item_id
JOIN item ch ON ch.id = ic.child_item_id
ORDER BY p.display_name, ch.display_name;"
```

**把這三份清單完整放進拆解的提示詞裡**，並明確要求模型：

> 以下是資料庫既有的節點與公司清單。產出拆解結果時，每個名稱都必須標示是
> 「沿用」（附既有 id）還是「新建」。若你要產出的名稱與清單中某項是同義詞
> （例如「印刷電路板」對應既有的「PCB」），一律標示為沿用該既有節點，不要新建。

清單中 `review_status` 為 `DRAFT` 且名稱以「驗證用」開頭者是走查殘留，
可以沿用去重判斷，但**不要拿來接新的關係**。

### 步驟 3：批次建立品類節點

```bash
curl -X POST http://localhost:8080/api/bulk/items \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "displayName": "主機板",
        "endProduct": true,
        "provenance": {
          "sourceType": "AI_GENERATED",
          "sourceDetail": "Claude Code 2026-07-27 主機板拆解第一輪",
          "confidence": 0.9
        }
      }
    ]
  }'
```

`endProduct` 只有終端成品（腳踏車、主機板）為 `true`，零件為 `false`（可省略）。
`parentCategoryId` 用於 is-a 細分類型（車用 PCB is-a PCB），**不是**組成關係，通常不填。

**回應會逐筆回傳 `targetId` 與 `naturalKey`**：

```json
{"success": true, "data": [
  {"index": 0, "success": true, "statusCode": 201,
   "targetType": "ITEM", "targetId": 80, "naturalKey": {"name": "主機板"}}
]}
```

**把整份回應留著**——步驟 4 需要 `targetId`，步驟 7 需要 `naturalKey`。
個別失敗不影響其他筆，失敗者 `success: false` 並附原因（如 409 名稱重複）。

### 步驟 4：批次建立組成關係

```bash
curl -X POST http://localhost:8080/api/bulk/compositions \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "parentItemId": 80,
        "childItemId": 81,
        "necessity": "STANDARD",
        "provenance": {"sourceType": "AI_GENERATED", "sourceDetail": "同上", "confidence": 0.9}
      }
    ]
  }'
```

`necessity`：`STANDARD`（標配）／`COMMON`（常見）／`OPTIONAL`（選配）。
**品類層的組成並非總是成立**——單速腳踏車沒有變速器、有些主機板沒有 WiFi 模組，
這類請用 `COMMON` 或 `OPTIONAL`，不要一律 `STANDARD`。

會造成循環的關係一律被拒（409），其餘照常寫入。

### 步驟 5：批次建立公司

```bash
curl -X POST http://localhost:8080/api/bulk/companies \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "displayName": "欣興電子",
        "country": "TW",
        "publicCompany": true,
        "provenance": {"sourceType": "AI_GENERATED", "sourceDetail": "同上", "confidence": 0.85}
      }
    ]
  }'
```

未上市公司照樣建立，`publicCompany` 設 `false`、不帶任何識別碼即可（如 SRAM）。

### 步驟 6：批次登記識別碼與供應角色

識別碼（`companyCode` 用公司的**正規化名稱**指定，因為此時還沒有代號）：

```bash
curl -X POST http://localhost:8080/api/bulk/identifiers \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "companyCode": "欣興電子",
        "identifierType": "TWSE",
        "identifierValue": "3037",
        "primary": true,
        "provenance": {"sourceType": "AI_GENERATED", "sourceDetail": "同上", "confidence": 0.95}
      }
    ]
  }'
```

`identifierType`：`TWSE` / `TPEX` / `TSE` / `NASDAQ` / `NYSE` / `TAX_ID` / `DUNS` / `OTHER`。
每家公司至多一筆 `primary`。

供應角色：

```bash
curl -X POST http://localhost:8080/api/bulk/supply-roles \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "companyCode": "3037",
        "itemId": 81,
        "companyRole": "MANUFACTURE",
        "provenance": {"sourceType": "AI_GENERATED", "sourceDetail": "同上", "confidence": 0.8}
      }
    ]
  }'
```

`companyRole`：`DESIGN`（設計）／`MANUFACTURE`（製造）／`ASSEMBLY`（代工組裝）／
`BRAND`（品牌）／`PACKAGING_TESTING`（封測）。
**同一家公司對同一零件可有多個角色**（台積電對某晶片可同時是製造與封測），各建一筆。

登記識別碼後 `companyCode` 可改用代號（如 `3037`）；未上市公司仍用正規化名稱。

### 步驟 7：批次審核

把前面各步回應中的 `targetType` 與 `naturalKey` 收集起來，一次送出：

```bash
curl -X POST http://localhost:8080/api/reviews/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "targets": [
      {"targetType": "ITEM", "naturalKey": {"name": "主機板"}},
      {"targetType": "ITEM_COMPOSITION", "naturalKey": {"parentItemId": 80, "childItemId": 81}},
      {"targetType": "COMPANY_IDENTIFIER",
       "naturalKey": {"identifierType": "TWSE", "identifierValue": "3037"}}
    ],
    "targetStatus": "VERIFIED",
    "reviewer": "你的名字"
  }'
```

`targetId` 與 `naturalKey` 擇一即可，同時提供時以 `targetId` 為準。
逐筆回報結果，個別失敗不影響其他筆。

**本流程不設人工停點**：紀律 2 的 80% 門檻就是唯一的關卡。
凡是寫得進去的資料（代表查證時 `confidence >= 0.8`），這一步全部轉為 `VERIFIED`，
不需要等人確認。

> ⚠️ **這代表門檻必須誠實套用。** 關卡從「事後人工審核」前移到「寫入前查證」，
> 中間沒有第二道防線。若查證不足卻給 0.85 讓資料過關，錯誤會直接進入已驗證狀態、
> 出現在對外查詢，而且沒有人會發現。**查不到就不要寫**——不寫的成本遠低於寫錯。

`reviewer` 請填得能看出是自動流程（例如 `claude-code-2026-07-27`），
日後要回頭清查某一輪灌入的資料時才找得到。

事後若發現錯誤，改送 `REJECTED` 而非刪除——資料保留著，下次生成才不會又寫回同一筆錯誤。

### 步驟 8：驗證

```bash
# 組成樹
curl -s "http://localhost:8080/api/products/80/components?depth=2"

# 零件的供應公司
curl -s "http://localhost:8080/api/items/81/suppliers"

# 反向查詢：這個零件出現在哪些終端成品
curl -s "http://localhost:8080/api/items/81/end-products"
```

若回傳為空，最常見的原因是**忘記審核**（資料還在 DRAFT）。
加 `includeDrafts=true` 可確認資料是否確實寫入。

---

## 五、每輪作業後要留下的東西

這些是日後恢復方案 A 的直接輸入，請記錄於本文件末端或另開紀錄：

1. **本輪使用的提示詞全文**
2. **產出品質觀察**：節點總數、其中重複／同義的比例、明顯錯誤的例子
3. **人工修正了什麼**：哪些被 `REJECTED`、哪些名稱需要改成別名
4. **拆解深度與耗時**

第 2 點特別重要——重複節點比例若高，代表方案 A 的 `design D5`（寫入前比對）
必須做得比現在設計的更強。

---

## 六、常見問題

| 症狀 | 原因 | 處理 |
|---|---|---|
| 查詢回空 | 資料還是 DRAFT | 執行步驟 7 審核 |
| 建立回 409 | 正規化名稱重複 | 該節點已存在，改用既有 id |
| 建立組成關係回 409 | 會造成循環 | 檢查方向是否顛倒 |
| `/actuator/health` DOWN | RabbitMQ 健康檢查（amqp 在 classpath 但無設定） | 忽略，不影響業務 API |
| 查詢回 400 且內容是 HTML | 中文參數未編碼 | 改用 `-G` + `--data-urlencode` |
| 審核回 404 | 自然鍵組合查無資料 | 檢查 `naturalKey` 欄位是否齊全 |
| 審核回 400 | 自然鍵欄位不足 | 訊息會列出缺少哪些欄位 |

---

## 七、作業紀錄

> 每輪作業後在此追加一節。

### 2026-07-27：主機板（第一輪）

**執行者**：Claude Code（`reviewer` = `claude-code-2026-07-27-motherboard`）
**耗時**：約 30 分鐘（17:53–18:02 為寫入與驗證，其餘為查證）
**拆解深度**：3 層（主機板 → 供電模組 → MOSFET；主機板 → PCB → 銅箔基板）

#### 寫入結果

| 步驟 | 筆數 | 結果 |
|---|---|---|
| 品類節點 | 21（id 241–261） | 21/21 成功 |
| 組成關係 | 20 | 20/20 成功 |
| 公司 | 17（id 242–258） | 17/17 成功 |
| 識別碼 | 17 | 17/17 成功 |
| 供應角色 | 23 | 23/23 成功 |
| 別名（品類 13＋公司 30） | 43 | 43/43 成功 |
| 批次審核 → VERIFIED | 141 | 141/141 成功 |

#### 本輪使用的提示詞

本輪**沒有對外部模型下提示詞**——拆解由 Claude Code 直接進行，因此留下的是實際套用的
拆解準則，日後方案 A 要寫系統提示詞時可直接改寫為提示詞：

> 以 PC 主機板為終端成品，拆到「品類」層級（不是具體型號）。一級零件涵蓋
> 板材、運算相關晶片、插槽／連接器、供電、韌體儲存、周邊控制晶片、散熱；
> 對「不是每片主機板都有」的項目（CPU 直焊板沒有 CPU 插槽、非高階板沒有 WiFi 模組）
> 使用 `COMMON` / `OPTIONAL`，不得一律 `STANDARD`。
> 每個名稱先與既有清單比對，同義詞一律沿用既有節點。
> 公司與供應角色需分別查證「公司存在」與「確實做該產品」，兩者皆成立才寫入，
> `sourceDetail` 必須是實際查到的出處。

查證路徑：公司存在與代號一律走
[證交所 OpenAPI `t187ap03_L`](https://openapi.twse.com.tw/v1/opendata/t187ap03_L)（一次撈全表比對，
比逐家查快得多，**建議日後固定這樣做**）；產品線則走公司官網產品頁 > 官方規格頁 > 產業媒體報導。

#### 產出品質觀察

- **重複節點比例 0%**。21 個節點與既有 12 筆（腳踏車領域）零重疊——但這是因為兩個領域
  完全無交集，**不足以證明去重機制有效**。真正的考驗會在灌第二個電子類產品
  （例如顯示卡、伺服器）時出現，屆時 PCB、電容、MOSFET 等節點必須沿用 241–261 這批。
- 主動登記 13 筆品類別名與 30 筆公司別名，就是為了讓下一輪擋得住同義詞
  （已驗證：查 `印刷電路板` 正確解析回 id 242 `PCB`）。**這一步不應省略**。
- 節點名稱正規化會吃掉空白與大小寫（`CPU 插槽` → `cpu插槽`、`M.2 插槽` → `m2插槽`），
  審核時用原始顯示名稱送 `naturalKey` 仍解析得到，不需自行正規化。

#### 查證後剔除的項目（誠實套用 80% 門檻的結果）

| 對象 | 原本想寫 | 剔除原因 |
|---|---|---|
| 和碩聯合科技（4938） | 主機板 ASSEMBLY | 官網產品頁抓不到內容，查不到主機板代工的官方佐證。公司存在成立、產品線不成立 → 不寫 |
| 欣興電子（3037）、華通電腦（2313） | PCB MANUFACTURE | 兩家確為 PCB 廠，但本輪查到的資料主力分別是 IC 載板與手機 HDI，沒有直接對到主機板用 PCB 的佐證 → 不寫。手冊步驟 5 的範例正好用欣興，**範例不等於查證** |
| 奇力新（2456） | 電感 MANUFACTURE | 證交所上市清單查無此代號，確認已因國巨併購下市 → 剔除。**這筆證明了「先撈證交所全表比對」會擋掉憑印象寫的過期資料** |
| 四大品牌的 `DESIGN` 角色 | 華碩／技嘉／微星／華擎 DESIGN | 查到的出處（DigiTimes 出貨量統計）只佐證品牌地位與出貨，沒佐證設計 → 只登記 `BRAND` |

未做人工修正、無 `REJECTED`：本輪寫進去的都是查證通過的，沒有事後推翻的項目。

#### 給下一輪的提醒

門檻前移到寫入前之後，**最大的風險是「差不多就給 0.85」**。本輪刻意剔除 4 類項目，
清單裡少 6 家公司比多 6 家錯的公司好——資料一旦 `VERIFIED` 就出現在對外查詢，沒有人會再看第二眼。

### 2026-07-28：主機板供應商補齊（第二輪）

**執行者**：Claude Code（`reviewer` = `claude-code-2026-07-28-motherboard-fill`）
**起因**：第一輪結束時主機板樹有 7 個零件掛零供應商。使用者指出「怎麼可能沒有」——
覆核後確認多數確實只是查證收得太緊，不是真的沒有廠商。

**寫入**：節點 1（DrMOS，id 262）、組成關係 1、公司 11、識別碼 11、供應角色 19、別名 21、審核 64，全數成功。

| 原本掛零的零件 | 補上的公司 |
|---|---|
| 銅箔基板 | 台光電 2383、聯茂 6213、台燿 6274 |
| PWM 控制器 | 茂達 6138、Infineon、MPS、Renesas |
| M.2 插槽 | 嘉澤 3533、鴻騰精密 HKEX:6088 |
| SATA 連接埠 | 嘉澤 3533、鴻騰精密 HKEX:6088 |
| 散熱片 | 奇鋐 3017、雙鴻 3324 |
| 無線網路模組 | Intel（BE200 模組）、啟碁 6285 |
| 供電模組 | **仍為 0，見下** |

#### 兩個值得記下來的教訓

1. **第一輪漏掉的不是資料，是查詢角度。** 銅箔基板、散熱片、連接器這幾類，第一輪
   根本沒去查，不是查了查不到。灌完一輪後應該**主動跑一次「哪些節點掛零供應商」的
   反查**（本文件第八節 G1 缺的列表端點就是為此），把掛零清單當成下一輪的待辦，
   而不是等人看出來。
2. **掛零時先問「這是採購件還是組裝出來的」**，再決定要補公司還是補子節點。
   `供電模組` 在桌機 DIY 板上是板廠用 MOSFET／電感／電容／PWM 焊出來的子組件，
   真正被採購的是**整合驅動器與功率 MOSFET 的功率級**，因此本輪新增 `DrMOS`（id 262）
   掛在供電模組下（`COMMON`——高階板標配，入門板仍用分離式），供應商 Infineon、MPS、Renesas。

   > ⚠️ **本輪據此判定「`供電模組` 維持 0 供應商是正確結果」，後來證明是錯的**——
   > 見下方 2026-07-28 電源模組補件。錯在**用桌機 DIY 板的視角去判斷一個涵蓋伺服器板的品類節點**。
   > 教訓修正為：判斷「是不是採購件」之前，先確認**這個品類節點涵蓋的範圍有多寬**。
   > 同一個名稱在不同應用層級可能是完全不同的東西。

#### 本輪剔除

- **鴻海 2317**：M.2／SATA 連接器的官方產品頁屬於子公司**鴻騰精密**（香港 6088），
  掛在母公司名下會是實質誤植，因此另建鴻騰精密而非沿用鴻海。
- **聯發科 2454**：Filogic 是 WiFi **晶片**，節點是 WiFi **模組**，層級對不上 → 不寫。
- **南亞塑膠 1303**（CCL）、**超眾 6230**（散熱）：本輪沒查到直接佐證，留待下輪。

### 2026-07-28：桌上型電腦（第三輪，樹根上移）

**執行者**：Claude Code（`reviewer` = `claude-code-2026-07-28-desktop-pc`）
**起因**：使用者檢查主機板那棵樹時問「最上層會顯示什麼讓使用者點下去」，
點出主機板被當成樹根是錯的層級——它是**電腦**的零件。

**寫入**：節點 13（id 263–275）、組成關係 18、公司 12、識別碼 12、供應角色 29、別名 43、審核 127，全數成功。

樹：桌上型電腦 → 主機板／CPU／記憶體模組／固態硬碟／機械硬碟／顯示卡／電源供應器／機殼／散熱器，
再往下 記憶體模組→DRAM 顆粒、固態硬碟→SSD 控制晶片、顯示卡→GPU 晶片＋散熱器、
散熱器→風扇、機殼→風扇。

#### 這一輪要記住的三件事

1. **樹根層級是資料品質問題，不是美觀問題。** 節點內容全對，但掛錯層級，
   使用者就看到「腳踏車」和「主機板」並列。**每次灌完新的終端成品，
   要問一次「它上面還有沒有更完整的產品」**——這比檢查零件對不對更容易漏。
2. **`is_end_product` 不是「能不能零售單買」。** 查程式後確認它只控制兩件事：
   出現在終端成品清單、以及反查時算不算終點；**任何節點都能查自己的組成樹**
   （`/api/products/{id}/components` 對非終端成品照樣有效，已實測）。
   若用「零售可單買」當標準，顯示卡、CPU、記憶體、SSD、電源供應器、機殼全都符合，
   這個旗標就選不出任何東西。判準應是「這是不是使用者會想從這裡開始逛的完整產品」。
3. **DAG 的價值這輪第一次真的顯現。** `PCB` 同時掛在主機板、記憶體模組、固態硬碟、顯示卡之下，
   反查 `PCB` 現在回傳 `['主機板', '桌上型電腦']`——跨產品的節點共用（design D3）
   到這輪才有東西可驗。前兩輪腳踏車與主機板零交集，證明不了任何事。

#### 已知未完成

- **主機板（241）仍是 `endProduct = true`**，因此同時出現在終端成品清單與桌上型電腦樹的第二層。
  修正需要 `add-item-listing-and-amendment` 的修正端點，本輪無法處理。
- **`.claude/rules/architecture.md` 與本手冊步驟 3 都把主機板當終端成品範例**，
  而 `ItemCompositionService.java:156` 的註解寫的是「中間節點（主機板）不算終端成品」——
  兩邊打架。修正端點完成後要一併改掉，否則下一輪的人還會照錯的範例做。
- 本輪未補供應商的節點：**機械硬碟**（台廠無此產品線，需查 Seagate／WD／東芝）。
  其餘 12 個新節點都已有供應商。

### 2026-07-28：電源模組補件（推翻前一輪的判斷）

**執行者**：Claude Code（`reviewer` = `claude-code-2026-07-28-power-module`）
**起因**：使用者問「供電模組台灣應該也有人做？」——查證後確認**前一輪的判斷是錯的**。

**寫入**：公司 3（光寶科 2301、康舒 6282、群光電能 6412）、識別碼 3、供應角色 5、
別名 6、審核 17，全數成功。

| 節點 | 補上的公司 |
|---|---|
| 供電模組（246） | 台達電 2308、康舒 6282 |
| 電源供應器（272） | 光寶科 2301、群光電能 6412、康舒 6282（原有台達電、全漢、迎廣） |

#### 錯在哪裡

前一輪判定「供電模組不是採購件、維持 0 供應商才正確」，理由是桌機主機板的 VRM
是板廠自己焊的。這個推理**在桌機 DIY 板上成立，但 `主機板` 是品類節點，涵蓋伺服器板**——
而伺服器／AI 板的板上供電正是模組化採購件。決定性證據是台達電官網
[DC-DC 模組電源](https://www.deltaww.com/zh-TW/products/DC-DC-Converters)頁面直接列出
**「非隔離型板載模組電源 — POL 系列」**，POL（Point of Load）就是晶片旁的板上供電模組。

**這個錯誤的形狀值得記住**：它不是查證不足，是**查證方向被自己的預設框住**——
先認定「這是板廠焊的」，就不會再去查「有沒有人把它做成模組賣」。
而且錯誤被寫成「教訓」記進手冊，如果沒被質疑，下一輪會照著錯的規則做。

#### 據此修正的規則

判斷一個節點「是不是採購件」之前，**先確認這個品類節點涵蓋的應用範圍有多寬**。
桌機、伺服器、工控、車用同名的東西，供應鏈可以完全不同。
若某個判斷只在其中一種應用下成立，那它就不是這個節點的結論。

### 2026-07-28：機械硬碟補件（覆蓋度歸零）

**執行者**：Claude Code（`reviewer` = `claude-code-2026-07-28-hdd`）

補上 Seagate（NASDAQ: STX）、Western Digital（NASDAQ: WDC）、Toshiba，
公司 3、識別碼 2、供應角色 3、別名 4、審核 12，全數成功。

**至此 id ≥ 241 的所有節點都有供應商，掛零數為 0。**

#### 這輪抓到的一個地雷

**東芝已於 2023-12-20 自東京證券交易所下市**（日本產業合作夥伴 JIP 收購私有化），
硬碟事業則持續營運。若照印象登記 `TSE / 6502` 就是寫入一個已不存在的識別碼。
最後以 `publicCompany = false`、不帶任何識別碼登記，`companyCode` 走正規化名稱。

同樣的地雷已經踩到第二次（第一次是奇力新 2456 因國巨併購下市）。
**規則**：外國公司登記識別碼前，除了查代號，還要查**現在是否仍在該交易所掛牌**——
併購下市、私有化、分拆都會讓記憶中的代號失效。台灣公司走證交所／櫃買 OpenAPI
全表比對天然能擋掉（查無即下市），外國公司沒有這層保護，必須自己查一次。

> 順帶：Western Digital 於 2025 年 2 月分拆 Flash 事業為 Sandisk 後為純 HDD 廠，
> 因此只掛在機械硬碟，未掛固態硬碟。分拆同樣會改變一家公司「做什麼」。

---

## 八、已知缺口

作業過程中發現、但尚未修補的問題。日後開 change 時的候選項目。

### G1：沒有「列出所有節點／公司」的 API

`GET /api/items` 只能以名稱或別名解析**單筆**，沒有列表端點。
步驟 2 要撈既有清單當 background，只能直接查資料庫。

影響：任何需要總覽的用途（灌資料前的去重、日後的前端瀏覽、方案 A 的 pipeline
在生成前取既有節點）都缺這個端點。方案 A 的 `design D5`（寫入前比對既有節點）
若要在後端實作，也需要有效率地取得候選清單。

可能方向：新增分頁列表端點，支援依審核狀態過濾與名稱模糊搜尋。

### G2：同義詞去重仍依賴模型判斷

解析端點只比對正規化名稱與已登記別名，擋不住尚未登記的同義詞。
目前靠「把既有清單當 background 餵給模型」降低發生率，但沒有系統性保證。

影響：同義節點一旦建立，DAG 節點共用即失效，且事後合併需改寫所有已建關係。

可能方向：寫入前做名稱相似度比對並提示疑似重複；或提供實體合併工具。

### G3：別名沒有批次端點

`POST /api/items/{id}/aliases` 與 `POST /api/companies/{code}/aliases` 都是單筆，
`/api/bulk/*` 底下也沒有對應端點。2026-07-27 主機板輪次為了登記 43 筆別名，
只能送 43 次請求（且回應不含 `naturalKey`，審核時要自行拼回別名字串）。

影響：紀律 1 把「登記別名」當成擋同義詞的主要手段，但這條路徑的成本明顯高於其他寫入，
量一大就容易被跳過——而跳過的代價正好是 G2 描述的那個問題。

可能方向：補 `/api/bulk/item-aliases` 與 `/api/bulk/company-aliases`，回應對齊其他批次端點
（帶 `targetType` 與 `naturalKey`）。

### G4：`IdentifierType` 涵蓋不到香港、法蘭克福等交易所

enum 目前是 `TWSE` / `TPEX` / `TSE` / `NASDAQ` / `NYSE` / `TAX_ID` / `DUNS` / `OTHER`。
2026-07-28 輪次要登記鴻騰精密（香港 6088）與 Infineon（法蘭克福 IFX）時無對應類型，
只能用 `OTHER` 並把交易所塞進值裡（`HKEX:6088`、`FSE:IFX`）。

影響：`unique(type, value)` 的語意被稀釋——`OTHER` 底下混雜多個編碼體系，值的格式全靠人工約定；
日後要以交易所為條件查詢（例如「所有港股供應商」）也做不到。供應鏈往上游走一定會遇到日、韓、
中、歐廠商，這個問題只會擴大。

可能方向：擴充 enum（`HKEX`、`FSE`、`KRX`、`SSE`／`SZSE` 等），或改為「交易所代碼 + 證券代號」
兩欄位，讓 `OTHER` 回歸真正的例外用途。

### G5：沒有「掛零供應商的節點」反查

2026-07-28 輪次的起因是第一輪留下 7 個零供應商節點，而這件事是使用者看出來的，
不是系統報出來的——目前只能自己寫 SQL 遞迴查。

影響：灌完一輪不知道自己漏了什麼，覆蓋缺口只能靠人盯。

可能方向：在 G1 的列表端點上加「無供應角色」「無組成關係」等覆蓋度過濾條件，
讓每輪收尾能直接產出待辦清單。
