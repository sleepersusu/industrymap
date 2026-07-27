## Why

2026-07-27 / 07-28 兩輪主機板資料灌入（記錄於 `docs/data-loading-playbook.md` 第七節）
把兩個缺口從「不方便」推成「擋路」：

**1. 沒有任何 API 能列出終端成品——首頁沒有東西可以 render。**
實測結果：`GET /api/products` 回 404（無此端點）、`GET /api/items` 不帶 `name` 回 400
（該端點只做單筆名稱解析）。唯一能進入某棵樹的是 `GET /api/products/{id}/components`，
但**前提是呼叫端已經知道 id**。目前要看到主機板那棵樹，只能有人先告訴你「id 是 241」。
這是第八節 G1 記過的缺口，但當時被寫成「灌資料去重不方便」，低估了——
它其實是使用者根本點不進來。

**2. 節點建立後無法修正——灌錯就是永久的。**
controller 層完全沒有 PUT / PATCH / DELETE。實例：`主機板`（id 241）被建成
`is_end_product = true` 且沒有任何上層，但主機板是電腦的零件，這個標記現在改不掉。
唯一的辦法是直接改資料庫，而那會繞過 `ItemService` 的跨表衝突檢查
（別名撞名、is-a 循環），正是手冊訂「資料庫唯讀」要避免的事。

兩者都不是效能或體驗問題，而是**閉環缺角**：資料進得去、看不到，也改不了。

## What Changes

- **新增終端成品列表端點**，作為產業地圖的進入點：分頁、依名稱模糊搜尋、
  可選擇是否納入草稿；已駁回的節點任何情況都不外露（沿用既有 `ReviewScopes` 規則）
- **新增品類節點修正端點**，可修正 `displayName`、`endProduct`、`parentCategoryId`
  - 採**全量替換**語意（PUT），因為 `endProduct` 是布林值，部分更新無法區分
    「沒送這個欄位」與「送了 false」
  - **任一欄位實際變更後，審核狀態一律退回 `DRAFT`**，需重新審核才對外可見
  - 值與現況完全相同的重送視為無變更，不改狀態
- **改名沿用既有的跨表衝突檢查**：新名稱不得撞到其他節點的正規化名稱或任何已登記別名。
  檢查邏輯抽成共用，`create` / `addAlias` / 改名共用同一份，避免三處規則各自漂移
- **`parentCategoryId` 需擋自我指向與 is-a 循環**（既有的循環偵測在 `ItemCompositionService`，
  守的是 part-of，這裡是另一條關係，需各自成立）

本次不含：公司的列表與修正端點（公司目前沒有「改不掉」的實際卡點）；
覆蓋度過濾（無供應角色／無組成關係的反查，第八節 G5）；刪除節點；前端；權限控管。

## Capabilities

### Modified Capabilities
- `industry-map-model`: 品類節點新增「可列出終端成品」與「可修正既有節點」兩項能力，
  並明確改名與 is-a 上層變更所受的既有約束
- `data-provenance`: 明確「內容變更後審核狀態退回草稿」的規則，讓已驗證資料無法被靜默改寫

## Impact

- **API 契約**：新增 `GET /api/products`（列表）與 `PUT /api/items/{id}`（修正）。
  既有 `GET /api/items?name=` 的單筆解析行為**不變**——手冊紀律 1 的去重指令與既有測試不受影響
- **新增程式**：`payloads/item` 的列表查詢與修正請求 payload、分頁回應 payload；
  `ItemRepository` 的列表查詢（SQL 寫在 repository，手寫查詢用 `nativeQuery`，enum 以字串傳入）
- **既有程式**：`ItemService` 擴充（修正、名稱衝突檢查抽共用）、`ItemController` 與
  `ProductController` 新增端點。依 `.claude/rules/testing.md`，改動 `ItemService` 後
  必須同步所有引用它的既有測試檔
- **資料庫**：無 schema 變更，不需 Flyway migration（tasks 中明確確認一次）
- **文件**：`.claude/rules/api-design.md` 的 Base Path 表需補上列表端點；
  `docs/data-loading-playbook.md` 第八節 G1 在本 change 完成後只解掉一半（見 design Non-Goals），
  需改寫該節說明剩餘範圍
