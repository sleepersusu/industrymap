## 1. Schema 與實體（先做：後面每一項都依賴它）

- [x] 1.1 Flyway `V2026_07_29_100100__create_item_image.sql`：`item_image` 表，
      欄位含 `item_id`、`view_label`、`storage_key`、`width_px`／`height_px`（原圖尺寸，供前端估算）
      ＋ provenance 欄位群；`UNIQUE (item_id, view_label)`、`view_label` NOT NULL 非空、
      FK 至 `item` ON DELETE CASCADE。**版號先確認主線 `db/migration` 最新一支為
      `V2026_07_28_100100`**（`.claude/rules/flyway.md`）
- [x] 1.2 Flyway `V2026_07_29_100200__create_item_hotspot.sql`：`item_hotspot` 表，
      欄位含 `item_image_id`、`child_item_id`、`position_label`、`polygon`（JSONB）
      ＋ provenance 欄位群；`UNIQUE (item_image_id, position_label)`（design D3，
      **唯一鍵刻意不含 `child_item_id`**）、`position_label` NOT NULL 非空、
      兩個 FK ON DELETE CASCADE
- [x] 1.3 migration 測試：兩張表建得起來、唯一鍵與非空約束確實生效
      （尤其「同圖同位置標籤重複」被擋、「同圖兩個不同標籤指向同一節點」可寫入）
- [x] 1.4 `model/ItemImage`、`model/ItemHotspot`，皆 extends `ProvenanceEntity`，
      關聯 `FetchType.LAZY`
- [x] 1.5 座標 POJO（`HotspotPoint`）以 JSONB 持久化，
      **`implements Serializable` 且不加 `serialVersionUID`**（`.claude/rules/code-style.md`）。
      先寫一支存檔／讀回的測試證明不拋 `JpaSystemException`——這是本專案已知會踩的地雷，
      不能只靠人工檢查。
      **實作偏離原任務**：`@Type(JsonType.class)` 需要 hypersistence-utils，而本專案 `pom.xml`
      並無此相依（該慣例來自 `ais-backend`）。改用 Hibernate 6 內建的
      `@JdbcTypeCode(SqlTypes.JSON)`，不為單一欄位新增第三方相依；Serializable 仍照補
- [x] 1.6 `.claude/rules/architecture.md` 核心領域模型表補兩列（標為已落地）

## 2. 建立與驗證（TDD：先紅再綠）

- [x] 2.1 `ItemImageRepository`、`ItemHotspotRepository`
- [x] 2.2 `service.item.ItemImageService`：建立圖片，重複的 `(item_id, view_label)` 回 409、
      節點不存在回 404，走既有 `ProvenanceValidator`
- [x] 2.3 建立熱區：同圖位置標籤重複回 409、指向的節點不存在回 404
- [x] 2.4 **座標驗證**：payload 以 `jakarta.validation` 表達（至少三點、每個座標值 `[0,1]`），
      **service 層再擋一次**——日後 AI 拆解流程繞過 HTTP 直接呼叫 service（design D5）
- [x] 2.5 邊界測試：位置標籤為空字串／全空白 → 400；座標 0 與 1 為合法（含端點）；
      兩點 → 400；座標 `-0.01` 與 `1.01` → 400
- [x] 2.6 熱區 `PUT` 全量替換座標與位置標籤，審核狀態依既有規則退回草稿；
      **不做 `DELETE`**（design D7），移除以審核駁回表達
- [x] 2.7 確認 `ItemImageService` 未超過 500 行，超過就依領域拆分（`CLAUDE.md`）

## 3. 讀取端點

- [x] 3.1 `GET /api/items/{id}/images`：回圖片清單，每張巢狀帶其熱區（design D6）
- [x] 3.2 查詢條件以 `@Valid @ModelAttribute` 綁定，不逐個接 `@RequestParam`。
      **實作偏離原任務**：既有的 `ReviewScopeQuery` 欄位與語意完全相同
      （`GET /api/items/{id}/compositions`、`/end-products` 都用它），沿用而不另建
      只有 `includeDrafts` 的 `ItemImageQuery`
- [x] 3.3 **四層審核過濾**（design D8）：路徑節點走 `exposableStatusNames`、
      圖片與熱區走 `visibleStatusNames`、**熱區指向的節點走 `exposableStatusNames`**
      （第四格是規則明載「最常漏」的一格，本專案已漏過三次）
- [x] 3.4 repository 查詢加 `@EntityGraph(attributePaths = "childItem")`（design D9）
- [x] 3.5 回應排序固定（圖片依 `view_label` 再 `id`；熱區依 `position_label` 再 `id`），
      避免任一筆被審核後清單無故重排
- [x] 3.6 邊界：節點無圖片回空清單而非 404、節點不存在回 404、已駁回節點回 404、
      圖片被駁回時其熱區一併不出現

## 4. 審核與批次建立（與既有機制接上，不長第二套）

- [x] 4.1 `ReviewTargetType` 新增 `ITEM_IMAGE`、`ITEM_HOTSPOT`
- [x] 4.2 `ReviewLookupService` 註冊兩個 repository
- [x] 4.3 `NaturalKeyResolver` 註冊兩個解析器：圖片＝節點 + 視角標籤、
      熱區＝節點 + 視角標籤 + 位置標籤（design D3）；欄位不足回 400 且訊息指名缺哪個維度
- [x] 4.4 `BulkAuthoringService` 加兩種批次類型，回應帶上述自然鍵，
      使「建立 → 審核」兩次呼叫走得完
- [x] 4.5 批次邊界：批次內位置標籤自我重複 → 第二筆 409、單筆座標不合法 → 該筆 400 其餘照建

## 5. 兩道既有守衛

- [x] 5.1 **先實測確認守衛會擋**：端點寫好但不動矩陣時，`ReviewVisibilityMatrixTest`
      應 fail 並逐字指名 `GET /api/items/{id}/images` 未登記
- [x] 5.2 矩陣登記四格並各配一支斷言：`item`（主查詢）／`item_image`（主查詢）／
      `item_hotspot`（join）／`item`（關係指向的實體）
- [x] 5.3 `QueryFanoutTest` 加一格，**大扇出用 60**（批次值 50 之上，design D9）。
      加完後以變異測試確認守衛有效：拿掉 `@EntityGraph` 該格應變紅
- [x] 5.4 搜尋並同步所有引用被修改類別的既有測試
      （`grep -r "ReviewTargetType\|NaturalKeyResolver\|BulkAuthoringService" src/test/`），
      `./mvnw -DskipTests test-compile` 通過

## 6. 收尾

- [x] 6.1 `./mvnw clean verify` 全量通過（surefire 235 + failsafe 203，皆 0 失敗）
- [x] 6.2 `.claude/rules/api-design.md` 路徑表補 `GET /api/items/{id}/images` 與批次路徑
- [x] 6.3 `docs/CHANGELOG.md` 記錄（先掃 `[Unreleased]` 既有項目，同 scope 同主題則合併）
- [x] 6.4 以 fresh-context diff review 審查（`/code-review`），只修正確性 findings。
      兩則皆已修並附回歸測試：
      (1) **PUT /api/item-hotspots/{id} 必然 500**——`amendHotspot` 以 `findById` 取熱區，
      回應在交易外組裝時碰到 LAZY 的 `childItem` 而拋 `LazyInitializationException`；
      改以 `findWithAssociationsById`（`@EntityGraph`）載入，並新增**不帶 `@Transactional`** 的
      `ItemHotspotAmendIntegrationTest`——原本的端點層基底帶交易，這類錯誤照不出來。
      已記入 `~/.claude/dev-errors/error-log.md`。
      (2) service 層補上標籤長度與原圖尺寸驗證，非 HTTP 進入點不再撞 DB constraint 變成 500
- [x] 6.5 情境實測：建立一張圖 + 兩個指向同一節點但位置標籤不同的熱區，
      確認回應可區分、審核駁回其一後另一筆仍在。
      **改以整合測試落地**（`ItemImageryScenarioTest`，一路走 HTTP：建立 → 審核 → 查詢 →
      駁回其一 → 再查詢），而非一次性的手動操作——同樣的證據，且日後會持續守著
- [x] 6.6 commit（程式、測試、文件、changelog 同一筆）：`b000ff2`
