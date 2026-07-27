## 1. 前置確認

- [x] 1.1 確認本 change 無 schema 變更、不需 Flyway migration（比對 design Migration Plan 第 1 點後明確記錄結論，不以「應該不用」帶過）
      **結論：不需要新增 Flyway migration。** 逐項比對 `V2026_07_26_100100__create_item.sql`：
      列表端點讀取的 `is_end_product`、`review_status`、`display_name`、`normalized_name` 與
      修正端點寫入的 `display_name`、`normalized_name`、`is_end_product`、`parent_category_id`、
      `review_status`、`reviewed_by`、`reviewed_at` 全部已存在，型別與可空性皆符合需求；
      不新增資料表、欄位、約束，也依 design Risks 刻意不為 contains 查詢新增索引。
- [x] 1.2 搜尋所有引用 `ItemService` 的既有測試檔（`grep -r "ItemService" src/test/`），列出清單；第 4 節動到該類別後需逐檔同步
      **清單（3 檔）**：`service/item/ItemServiceTest.java`（直接測本類別，受名稱檢查抽共用影響，需改 mock 樁）、
      `controller/ItemControllerTest.java`（`@MockitoBean` 注入，新增端點需補測試）、
      `service/bulk/BulkAuthoringServiceTest.java`（`@Mock` 整支 `ItemService`，不受內部改動影響）。

## 2. 終端成品列表端點（design D1、D6、D7）

- [x] 2.1 建立列表查詢 payload（分頁、名稱關鍵字、是否納入草稿），以 `@Valid @ModelAttribute` 綁定；分頁參數加上邊界驗證（頁碼非負、每頁筆數上下限）
- [x] 2.2 建立分頁回應 payload（content、頁碼、每頁筆數、總筆數、總頁數）
- [x] 2.3 先寫失敗測試：列表應只回終端成品，不含零件節點；再於 `ItemRepository` 補查詢（SQL 寫在 repository，手寫查詢用 `nativeQuery`，enum 以字串傳入）
- [x] 2.4 先寫失敗測試：預設只回 `VERIFIED`；明確指定納入草稿時才含 `DRAFT`
- [x] 2.5 先寫失敗測試：任何條件下都不得回傳 `REJECTED`，含明確指定納入草稿的情況
- [x] 2.6 先寫失敗測試：名稱關鍵字應只回名稱包含該關鍵字者
- [x] 2.7 先寫失敗測試：分頁應回傳正確的總筆數與總頁數
- [x] 2.8 先寫失敗測試：以相同條件取第一頁與第二頁，兩頁 MUST NOT 重疊且合併後不遺漏（排序需含 `id` 作為 tie-break）
- [x] 2.9 先寫失敗測試：無符合資料時回空清單與總筆數 0，非 404
- [x] 2.10 於 `ProductController` 新增 `GET /api/products`，補 `@Tag` / `@Operation` / `@ApiResponses` 與 payload 的 `@Schema`
- [x] 2.11 記錄已知限制：名稱模糊搜尋為 contains 查詢，用不到 `normalized_name` 的唯一索引，本 change 刻意不加索引（design Risks）

## 3. 名稱衝突檢查抽共用（design D4）

- [x] 3.1 先寫失敗測試：以「正規化名稱 + 排除的節點 id」呼叫共用檢查，撞到其他節點名稱時應回報衝突
- [x] 3.2 先寫失敗測試：撞到任何已登記別名時應回報衝突（含該節點自己的別名）
- [x] 3.3 先寫失敗測試：排除自身後，與自己現有名稱相同不應被視為衝突
- [x] 3.4 抽出共用檢查方法，`ItemService.create` 與 `ItemService.addAlias` 改為呼叫它；確認既有測試全綠（行為 MUST NOT 改變）

## 4. 品類節點修正端點（design D2、D3、D5）

- [x] 4.1 建立修正請求 payload，`displayName`、`endProduct`、`parentCategoryId` 皆為必填（後者允許 null 值但欄位必須存在）；先寫失敗測試：缺欄位回 400 且訊息指出缺哪些
- [x] 4.2 先寫失敗測試：修正顯示名稱後，`displayName` 與 `normalizedName` 皆應更新
- [x] 4.3 先寫失敗測試：將終端成品改為非終端成品後，該節點不再出現於第 2 節的列表端點
- [x] 4.4 先寫失敗測試：`parentCategoryId` 指定為 null 應清空 is-a 上層
- [x] 4.5 先寫失敗測試：修正不存在的節點回 404
- [x] 4.6 先寫失敗測試：改名撞到其他節點名稱回 409、撞到已登記別名回 409，且該節點不被修改；接上第 3 節的共用檢查
- [x] 4.7 先寫失敗測試：`parentCategoryId` 指向自己回 409
- [x] 4.8 先寫失敗測試：`parentCategoryId` 造成 is-a 循環（B 的上層是 A，將 A 的上層指為 B）回 409，且 A 不被修改；再實作 is-a 鏈上溯檢查
- [x] 4.9 先寫失敗測試：`parentCategoryId` 指向不存在的節點回 404
- [x] 4.10 先寫失敗測試：節點 A 的組成關係含 B 時，仍可將 A 的 is-a 上層指定為 B（part-of 與 is-a 各自獨立）
- [x] 4.11 於 `ItemController` 新增 `PUT /api/items/{id}`，補 OpenAPI 註解
- [x] 4.12 依 1.2 的清單逐檔同步既有測試，執行 `./mvnw -DskipTests test-compile` 確認測試層可編譯

## 5. 修正後退回草稿（design D3）

- [x] 5.1 先寫失敗測試：修正 `VERIFIED` 節點的顯示名稱後，狀態應變為 `DRAFT` 且不再出現於預設查詢
- [x] 5.2 先寫失敗測試：送出與現況完全相同的欄位值時，狀態應維持 `VERIFIED`（正規化後比較名稱）
- [x] 5.3 先寫失敗測試：修正 `DRAFT` 節點後狀態維持 `DRAFT`
- [x] 5.4 先寫失敗測試：修正 `REJECTED` 節點後狀態變為 `DRAFT`
- [x] 5.5 先寫失敗測試：修正因衝突被拒時，審核狀態與所有欄位皆不被變更
- [x] 5.6 先寫失敗測試：修正後重新審核為 `VERIFIED`，該節點應重新出現於預設查詢

## 6. 驗證與收尾

- [x] 6.1 收尾執行一次 `./mvnw clean verify`，附實際輸出（長時間 build 背景執行，靠完成通知取回結果，禁止輪詢迴圈）
- [x] 6.2 對本次 diff 跑 `/code-review`（fresh context），只修正確性 findings，不追風格與過度防禦建議
      **結果**：codex plugin 未安裝，改以 general-purpose subagent 全新 context 審 `7737dcd`。
      4 筆 findings，無 high。已修 3 筆正確性問題（displayName 改寫繞過審核、分頁位移量整數溢位、
      純標點關鍵字回 400）；第 4 筆（列表逐筆初始化 LAZY `parentCategory` 的 N+1，PLAUSIBLE／low，
      終端成品目前多半無 is-a 上層）判定為效能議題，本輪不動。三筆已寫入 `~/.claude/dev-errors/error-log.md`。
- [x] 6.3 更新 `.claude/rules/api-design.md` 的 Base Path 表，補上 `GET /api/products` 列表與 `PUT /api/items/{id}` 修正端點
- [x] 6.4 改寫 `docs/data-loading-playbook.md` 第八節 G1：說明列表端點已解掉「終端成品進入點」，但「列出所有節點（含零件）供灌資料去重」仍需資料庫唯讀查詢，並記下 design D1 的方案 C 遷移路徑
- [x] 6.5 於 `docs/CHANGELOG.md` 的 `[Unreleased]` 記錄行為變化（新增終端成品列表、新增節點修正且修正後退回草稿），hash 欄寫 `(pending)`；追加前先掃既有項目，同 scope 同主題合併
- [x] 6.6 commit（changelog 與程式同一筆），commit message 交代改了哪些 service / controller / 文件、為什麼、補了哪些測試
