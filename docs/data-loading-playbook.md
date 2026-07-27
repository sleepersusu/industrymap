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

## 二、四條不可違反的紀律

### 1. 建立節點前必須先解析既有節點

這是整套設計最不能踩的雷。`design D3/D9` 讓零件節點**全站共用**（PCB 同時掛在主機板、
汽車、家電之下都是同一筆），跨產業查詢與市佔率統計都建立在這個前提上。
一旦產生重複節點，「這家公司橫跨哪些產業」就會查錯，而且很難事後修。

**每個要建立的名稱，都先查一次：**

```bash
curl -s -G "http://localhost:8080/api/items" --data-urlencode "name=PCB"
```

- 查得到 → **沿用回傳的 id**，不要建新節點
- 查不到 → 才建立

此端點同時比對正規化名稱與別名，所以「無線網路模組」若已登記為「WiFi 模組」的別名也會命中。
但它擋不住尚未登記的同義詞——**AI 產出的名稱要人眼看過**，發現同義詞應改為登記別名
（`POST /api/items/{id}/aliases`）而非新建節點。

### 2. 市佔率沒有可查證來源就不填

`design D4` 明訂：**禁止自行編造百分比**。市佔率是 LLM 幻覺重災區，
一個沒有來源的數字比沒有數字更糟——它會被當真。

- 找得到可靠出處（研究機構報告、公司年報、法說會簡報）→ 填，且 `sourceDetail` 必須是**真實出處**
- 只搜到二手部落格、來源與領域對不上 → **留空**

市佔率留空不影響零件樹與供應商查詢，排名查詢會回空清單（非 404），行為正確。

> 投顧報告到手後，市佔率走文件解析路徑另行匯入，不在本手冊範圍。

### 3. 所有資料一律進草稿，經審核才對外可見

寫入端點不接受指定審核狀態，一律為 `DRAFT`。對外查詢預設只回 `VERIFIED`，
`REJECTED` 任何情況都不外露。**灌完必須審核，否則查詢會是空的。**

### 4. `sourceType` 為 `AI_GENERATED` 時必須帶 `confidence`

否則回 400。`sourceDetail` 建議寫明是哪個模型、哪一輪作業產生的，方便日後回溯。

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

用 Claude Code 蒐集資料時，對每一項主張要求出處。可信度分級：

- **高**：公司年報、法說會簡報、公開資訊觀測站、研究機構原始報告
- **中**：產業媒體報導（DIGITIMES、經濟日報等）
- **低／不可用**：內容農場、無署名部落格、AI 未附出處的斷言

**零件組成**（腳踏車有變速器）多屬常識，可直接採用。
**公司對零件的角色**（桂盟做鏈條）需要出處。
**市佔率**一律需要出處，見紀律 2。

### 步驟 2：解析既有節點

把預計要建的名稱逐一查過（見紀律 1），列出「沿用」與「新建」兩份清單。

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

**審核前請人眼看過內容**——這一步的意義就在於擋下 AI 的錯誤，全部照單全收等於沒有審核。
判斷有問題的改送 `REJECTED`（資料保留不刪除，避免下次生成又寫回同一筆錯誤）。

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
