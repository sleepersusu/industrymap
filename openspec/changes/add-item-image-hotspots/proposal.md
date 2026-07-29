## Why

前端要做的是**互動爆炸圖**：一張腳踏車拆解圖，點圖上的變速器就下鑽到做變速器的公司。
瀏覽器不知道「照片上這一塊叫變速器」——圖片對它只是像素，因此必須另存一份
「這塊區域對應哪個品類節點」的資料。這份資料目前完全不存在，是前端唯一開不了工的地方。

而且它撞到既有 schema 的一個真實限制：`item_composition` 的唯一鍵是
`UNIQUE (parent_item_id, child_item_id)`，「腳踏車 → 煞車」只能有**一條邊**，
但爆炸圖上前煞車與後煞車是**兩個熱區**。熱區若只存「指向哪個節點」，兩筆資料會完全相同，
使用者點前煞車與點後煞車會拿到一模一樣的結果。

## What Changes

- **新增 `item_image`**：品類節點的圖片，一個節點可有多張（爆炸圖、不同視角）。
  只存物件儲存的 key 或 URL，**二進位不進資料庫**
- **新增 `item_hotspot`**：圖片上的可點擊區域，綁圖片 + 指向 `child_item_id`
  + 帶**位置標籤**（前煞車／後煞車）+ 以 0–1 相對比例表達的多邊形座標點集
- **刻意不動 `item_composition`**：位置是**圖的性質**，不是組成關係的性質。
  「腳踏車有煞車」不因車上有兩個煞車而變成兩筆事實；位置只在畫圖時才需要存在。
  同一條 composition 邊 SHALL 可對應多個位置標籤不同的熱區
- **新增讀取端點** `GET /api/items/{id}/images`：一次回圖片與其熱區（巢狀），
  前端畫一張圖只需一次呼叫
- **兩張表皆為內容資料**：沿用 `ProvenanceEntity` 的來源與審核欄位、納入 `ReviewTargetType`
  白名單與 `NaturalKeyResolver`、補批次建立、新端點登記進可見性矩陣與查詢筆數守衛
- **熱區可修正**：`PUT` 全量替換座標，沿用「內容變更後審核狀態退回草稿」既有語意

本次不含：**圖片上傳端點**（先接受外部已存在的 URL，見 design D4）、
**熱區編輯器 UI**、具體車款組態層（熱區指向的是品類節點，即示意爆炸圖語意，見 design D1）、
熱區的實體刪除（以審核駁回表達，見 design D7）。

## Capabilities

### New Capabilities
- `item-imagery`: 品類節點的圖片與熱區——互動爆炸圖的資料層，含位置標籤如何讓同一條組成邊對應多個熱區

### Modified Capabilities
- `data-provenance`: 內容表由八張增為十張，兩張新表納入來源記錄與自然鍵定位
- `bulk-authoring`: 圖片與熱區支援批次建立，熱區以「一張圖」為自然的批次單位

## Impact

- **資料庫**：新增 `item_image`、`item_hotspot` 兩張表，需 Flyway migration；
  座標以 JSONB 儲存
- **新增程式**：`model/{ItemImage,ItemHotspot}`、對應 repository、
  `service.item.ItemImageService`、`payloads.item` 下的請求／回應／查詢物件、
  `ItemController` 新增端點
- **既有程式**：`ReviewTargetType` 加兩個常數、`NaturalKeyResolver` 與 `ReviewLookupService`
  各註冊兩行、`BulkAuthoringService` 加兩種批次類型
- **序列化地雷**：座標若以 `@Type(JsonType.class)` 存自訂 POJO，
  物件圖**每一層**都必須 `implements Serializable`，否則存檔直接 500
  （`.claude/rules/code-style.md` 既有規則）
- **既有守衛**：`ReviewVisibilityMatrixTest` 需登記新端點的四格、
  `QueryFanoutTest` 需加一格（6 → 7）
- **文件**：`.claude/rules/architecture.md` 的核心領域模型表補兩列、
  `api-design.md` 路徑表補新端點、`docs/CHANGELOG.md` 記錄
