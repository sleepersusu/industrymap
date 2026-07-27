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

## 三、資料庫現況（2026-07-27）

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

（尚無紀錄）

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
