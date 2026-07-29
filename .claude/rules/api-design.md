# API Design Guidelines（API 設計規範）

本文件以 `ais-backend` 的既有慣例為基礎，套用到 `industrymap` 的產業地圖領域。
目標：API 內部一致，不強套教科書式 REST。

## 核心原則

- 成功回應統一 `ResponseEntity<ServerResponse<T>>`；錯誤統一 `ServerException` + OpenAPI 註解。
- 長時間任務、批次處理允許動作式路徑（`/sync`、`/refresh`、`/scan`、`/status` 等）。
- `DELETE` 可視需要帶 request body 做批次刪除。

## Base Path（規劃）

**開發順序對齊「主軸先行」**：先做 `/api/products` 與 `/api/companies` 核心地圖查詢，
`news` / `patents` / `stock-price` 這幾個子資源屬於後期擴充，路徑先預留規劃即可。

| 前綴 | 優先度 | 用途 | 範例 |
|------|--------|------|------|
| `/api/products` | 第一階段 | **終端成品列表**——產業地圖的進入點，呼叫端不需事先知道任何 id | `GET /api/products?name=&page=&size=` |
| `/api/products/...` | 第一階段 | 產品 / BOM 拆解 | `/api/products/{id}/components` |
| `/api/items/{id}` | 第一階段 | 品類節點的取得與**修正**（PUT 全量替換） | `PUT /api/items/{id}` |
| `/api/items/{id}/images` | 第一階段 | **節點的圖片與熱區**——互動爆炸圖的資料層，一次回圖片與其巢狀熱區 | `GET /api/items/{id}/images`、`POST /api/items/{id}/images` |
| `/api/item-hotspots` | 第一階段 | 熱區寫入（所屬圖片以 body 指定，避免第三層巢狀）；**刻意無 DELETE**，移除以審核駁回表達 | `POST /api/item-hotspots`、`PUT /api/item-hotspots/{id}` |
| `/api/companies` | 第一階段 | **公司列表**——從公司側進入地圖，可依名稱／別名、國別、公開發行狀態、供應零件過濾 | `GET /api/companies?name=&country=&itemId=&page=&size=` |
| `/api/companies/{code}` | 第一階段 | 公司基本資料與其識別碼 | `/api/companies/TWSE:2330` |
| `/api/companies/{code}/items` | 第一階段 | **公司供應的零件**——從公司側往下走的入口；同一節點只出現一筆，角色收在 `roles` 內 | `/api/companies/TWSE:2330/items?role=MANUFACTURE` |
| `/api/supply-relations/...` | 第一階段 | 供應角色與市佔率寫入（公司以**代號**指定，不收內部 id） | `/api/supply-relations/roles` |
| `/api/reviews` | 第一階段 | 審核狀態流轉，十張內容表共用單一端點 | `/api/reviews`、`/api/reviews/batch` |
| `/api/bulk/...` | 第一階段 | 內容資料的批次建立（內部作業端點） | `/api/bulk/items`、`/api/bulk/market-shares`、`/api/bulk/item-images`、`/api/bulk/item-hotspots` |
| `/api/companies/{code}/news`、`/patents`、`/stock-price` | 後期 | 公司情資子資源 | `/api/companies/{code}/stock-price` |
| `/api/internal/...` | 後期 | 內部服務間（如 job 觸發、健康檢查） | `/api/internal/market-sync/trigger` |
| `/api/public/...` | 視需求 | 對外公開查詢 | `/api/public/industry-map/{productId}` |

## URL 命名

- 一律 kebab-case、資源導向：`stock-price`、`patent`、`news-item`。
- 巢狀深度原則 2 層內；例如 `/api/companies/{code}/patents` 可以，避免再往下巢狀第三層。
- 公司以**公司代號**（股票代號 / 統一編號）作為路徑識別，不用內部自增 id 曝露於外部 API。

## 內部 id 的曝露界線

「不曝露內部自增 id」這條規則的適用範圍是**公司的路徑識別**，不是「所有回應都不得含 id」。
過度推論成後者，會做出資料進得去、出不來的端點——公司識別碼就曾因此無法經 API 審核。

- **路徑識別**：公司一律用代號（未上市公司用正規化名稱）；品類節點沿用內部 id，
  因為節點沒有代號體系，也不對外公開。
- **回應主體**：查詢回應可以帶內部 id（如 `ItemResponse.id`、`CompositionResponse.id`），
  但不得只靠 id 才能操作該筆資料。
- **內部作業端點**（審核、批次建立）：目標 SHALL 支援以**自然鍵**定位，即該資料類型的資料庫唯一鍵；
  id 定位可同時保留，兩者同時提供時以 id 為準。自然鍵欄位不足回 400 且訊息須列出缺少哪些欄位，
  欄位齊全但查無資料才回 404。
- **判準**：任一寫入端點的回應，都必須足以讓呼叫端在不查資料庫、不再打其他端點的前提下審核該筆資料。

## Status Code（專案語意）

- `404`：查無此公司代號 / 產品 / 零組件。
- `409`：任務重入、外部同步任務狀態衝突。
- `503`：外部股價 / 新聞 / 專利來源不可用（含逾時）。
- 其餘依標準語意；`201` 用於明確建立新資源或觸發新的同步任務。

## Response 格式

- 一般 JSON API：`ResponseEntity<ServerResponse<T>>`，不自行發明另一套包裝。
- 錯誤：拋 `ServerException` + `HttpStatus`；訊息需標明是「內部資料查無」還是「外部來源失敗」，
  兩者對前端呈現方式不同（前者是空狀態、後者是「資料可能非最新，稍後再試」）。

## 非同步任務

- 呼叫外部股價 / 新聞 / 專利來源的流程一律走 job / queue（`job/producer` → `job/consumer`）。
- 觸發同步後回傳可追蹤的 job / task id，不同步等待整體完成；查詢結果走既有快取資料 + 最後更新時間戳。
- 每筆外部資料（股價、新聞、專利）需記錄「最後成功同步時間」，供前端顯示資料新鮮度。

## 參數與驗證

- Request body 用 DTO / Payload，加 `@Valid` + `jakarta.validation`。
- 日期 / 時間欄位一律 ISO 8601；股價數值需明確幣別（如 `TWD`）。

### 可選與衍生參數的組合語意（`dev-mistake-digest` 升格，2026-07-28）

驗證與設計若只停在**單一參數**，就會漏掉參數之間的組合與衍生值。此模式已犯 3 次：
分頁位移量 int 相乘溢位成負 OFFSET、空關鍵字的語意未定義而拋 400、
可選的 `companyRole` 巢狀在可選的 `itemId` 之下而被靜默忽略。

- 每個可選參數都要定義「未指定」與「指定為空字串」分別代表什麼；**空字串預設視為未指定**
  （前端把所有參數都帶上、值留空是常見行為，照字面過濾會回 0 筆，
  使用者看到的是「一筆都沒有」而不是「這個條件沒生效」）。
- 參數之間有依賴關係時，以 `jakarta.validation` 在 payload 層表達，
  **不得讓 SQL 的巢狀結構靜默決定**——靜默忽略一個條件會讓呼叫端把錯誤結果當成正確答案。
  三選一：回 400、讓它獨立生效、或在 `@Schema` 與 OpenAPI 明確記載會被忽略。
- 由使用者輸入相乘 / 相加得出的值（分頁位移量等）一律先轉 `long` 再運算，下游型別跟著放大；
  只驗單一參數的邊界不足以保證衍生值安全。

## OpenAPI

- 對外 API 補 `@Tag`、`@Operation`、`@ApiResponses`；DTO 欄位補 `@Schema`。
- Swagger 與實際回傳不一致時，以實際為準並修正註解。
