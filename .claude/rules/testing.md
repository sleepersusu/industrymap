# 測試規範（Testing Guidelines）

## 強制 TDD 規則

TDD 強制流程（含修 Bug 先寫失敗測試、trivial 豁免、完成須附證據）定義於 `CLAUDE.md` 憲章「實作與修 Bug 的強制流程」，以該處為唯一準則。

補充：`superpowers:test-driven-development` 可用時必須呼叫，作為 Red-Green-Refactor 紀律的執行強化；不可用時憲章文字規則仍完整生效。

## 寫測試規範

**結構**
- 每個測試依 Given-When-Then（Arrange-Act-Assert）三段組織；複雜 setup 抽私有 helper。
- 一個測試只驗證一個行為；多個 assert 允許，但必須屬於同一邏輯結果。
- 測試之間互相獨立，不依賴執行順序、不共享可變狀態。

**測行為，不測實作**
- 優先 assert 回傳值與狀態變化。
- `verify()` 只用於「該呼叫本身就是行為」的外部副作用（送 queue、呼叫外部股價/新聞/專利 client）。
- 禁止逐一 verify 內部方法呼叫——重構未改行為時測試不應該壞。

**測試類型選擇**
- 預設 `@ExtendWith(MockitoExtension.class)` 單元測試，mock repository / client / producer。
- `@SpringBootTest` 僅限驗證 context 組裝（config、security、wiring），禁止當一般單元測試用。
- 斷言沿用 JUnit `Assertions`，不引入新斷言框架。

**覆蓋內容**
- 邊界必測：null / 空集合、找不到公司代號、外部資料來源逾時或回傳異常、狀態衝突（409 情境）。
- Bug fix 的重現測試必須覆蓋實際觸發輸入。
- 禁止為湊覆蓋率寫無 assert 或 assert 恆真的測試。

**測試資料**
- 用 builder 組最小必要物件；魔法值取有名變數；不貼大段 JSON 當 fixture。

## 新增對外查詢端點必須登記進可見性矩陣

`ReviewVisibilityMatrixTest`（`src/test/.../controller/`）是「已駁回資料不外露」這條規則的唯一執行點：
它掃出 `com.profetai.industrymap.controller` 底下所有會回應 GET 的路徑，逐一比對矩陣，
**未登記的端點直接 fail**。這道守衛存在的理由是同一個模式已經漏過三次，且三次的層次都不同
（主查詢表、子查詢的表、關係指向的實體）——提醒型的文件擋不住第四次。

新增或修改對外 GET 端點時：

1. 在矩陣中登記該端點可觸及的每一張內容表，逐格標明層次（`MAIN_TABLE` / `JOINED_TABLE` /
   `REFERENCED_ENTITY`），並以 repository 的實際 SQL 與 service 的組裝流程為準，不憑端點名稱推測。
2. 每一格都要有對應的斷言。斷言透過 `failure(端點, 表, 層次)` 取失敗訊息，該方法會反查矩陣，
   沒登記的格子連測試都寫不出來——矩陣與斷言互相綁定，不會退化成沒人看的裝飾。
3. 確實不觸及任何內容表時，登記為 `EndpointCoverage.none("理由")`。**可以宣告「這裡不需要過濾」，
   不可以省略登記**：守衛強制得了「有登記」，強制不了「登記正確」，把遺漏從靜默變成必須主動說謊
   是刻意的取捨。
4. 只斷言「該筆已駁回資料及其影響不出現」，不順便斷言回應的其他內容；也不驗證草稿語意
   （各端點的 `includeDrafts` 取捨不同，由各端點自己的測試守）。

規則本身見 `.claude/rules/architecture.md`「審核狀態過濾是查詢的預設義務」。

## 對外查詢必須納入查詢筆數守衛

`QueryFanoutTest`（`src/test/.../support/`）守的是「資料庫往返次數與結果筆數脫鉤」。
N+1 是靜默劣化：資料量小時完全看不出來，等看得出來時已經是線上問題。

- **斷言寫法固定為「兩種扇出的 SQL 筆數相同」，不得寫成「不得超過 N 筆」**。
  後者綁的是某個當下的實作細節，任何無關的調整都會讓它紅；反覆誤報的守衛會被停用，
  於是真的長回線性成長時反而沒人發現。
- **大扇出必須大於 `default_batch_fetch_size`（目前 50）**。這一點決定守衛有沒有用：
  兩種扇出都小於批次值時，批次抓取會把兩者都壓成一次 `IN` 查詢，於是「筆數相同」在
  **有沒有 `@EntityGraph` 都成立**——守衛全綠卻什麼也沒守（此洞由 code review 實測抓到）。
  扇出跨過批次值後，只有真的 join fetch 才恆定，靠批次兜底的會退回 `ceil(n/50)` 筆而紅。
- 無法 join fetch 的查詢（native query）是唯一例外：它的筆數本來就只能是 `ceil(n/批次)`，
  該格的大扇出留在批次值以下，並在該處寫明「守得住逐筆 N+1、守不住依賴批次大小」。
- 新增會回傳多筆資料的對外查詢時，一併加一格。
- 量測用 Hibernate `Statistics`，於測試執行期 `setStatisticsEnabled(true)` 開啟，
  不要改 `application.properties`（那會讓該測試獨佔一個 Spring context）。
  每次量測前 `entityManager.clear()`，否則第一級快取會讓第二次呼叫看起來不需要查詢。

### N+1 的三種對策與適用範圍

`Item` / `Company` 的 `@Id` 標在**欄位**上，Hibernate 無法在代理上攔截 identifier getter——
**連讀主鍵都會觸發初始化**。因此任何碰到 LAZY 關聯的地方預設就是 N+1。

| 手段 | 用在哪 | 治不到 |
|---|---|---|
| `@EntityGraph` | 衍生查詢；永遠一次 join fetch，且相依顯性寫在查詢上 | native query（Hibernate 不套用） |
| `default_batch_fetch_size`（全域，50） | 兜底；**native query 的唯一解** | 迴圈本身送出的查詢 |
| 遞迴 CTE | 圖走訪 | — |

熱路徑一律加 `@EntityGraph`，不要只靠全域設定：後者是隱性的，讀單一查詢看不出關聯會不會被載入，
且扇出大於批次值時又會退回多筆。**若把衍生查詢改寫成 native，`@EntityGraph` 會靜默失效**，
這時只剩查詢筆數守衛抓得到。

### 邏輯移進 SQL 時測試要跟著搬

以 mock repository 驗證的走訪／組裝邏輯，一旦移進 SQL 就再也測不到任何東西——mock 只會回它自己被設定的值。
這類測試必須改寫為對真實 PostgreSQL 的整合測試，**且要在換實作之前先改寫並確認在舊實作下通過**，
否則沒有回歸基準。用 `verify(..., never())` 這種驗實作互動的斷言尤其會擋住重構，改以結果表達。

## 既有測試同步規則

**修改任何邏輯類別（Service、Consumer、Repository、Helper 等）後，必須同步更新所有引用該類別的現有測試檔案。**

1. 先搜尋所有引用該 class 的測試檔案：`grep -r "ClassName" src/test/`
2. 逐檔確認是否需同步更新（建構子、mock 設定、方法呼叫等）
3. 更新後執行 `./mvnw -DskipTests test-compile` 確認測試層可編譯
4. 收尾再跑一次全量（依下方「驗證節奏」，用兩段式 test-compile → surefire:test，非反覆 clean verify）

## 測試分軌（快 / 慢，surefire ⇄ failsafe）

慢測試以 `@Tag("integration")` 標記：

- **慢測試（integration）**：`@SpringBootTest` / `@WebMvcTest`（啟動 Spring context）、Testcontainer、直接跑 Flyway 的 migration 測試。
- **快測試（unit）**：`@ExtendWith(MockitoExtension.class)` 或純 Mockito，無 context / DB。

Maven 綁定（`pom.xml`，建立專案時比照 `ais-backend` 設定）：
- `surefire` 設 `<excludedGroups>integration</excludedGroups>` → `./mvnw test`、`./mvnw surefire:test` **只跑快測**，不啟動任何 container。
- `failsafe` 設 `<groups>integration</groups>` + `include **/*Test.java`，綁 `integration-test` / `verify` → `./mvnw verify` 跑 surefire（快）＋ failsafe（慢）＝ **全部**。

| 目的 | 指令 |
|---|---|
| 日常快測（TDD 綠燈、review） | `./mvnw test` 或 `./mvnw surefire:test -Dtest=<Class>` |
| 全量（含整合，發布前 / CI） | `./mvnw verify` |
| 只跑某一支整合測試 | `./mvnw failsafe:integration-test -Dit.test=<Class>` |

**分軌義務**：新寫測試若啟動 Spring context（`@SpringBootTest` / `@WebMvcTest`）、用 Testcontainer 或直接跑 Flyway，**必須加 `@Tag("integration")`**，否則混進快測拖慢日常迴圈。

### 排程與外部呼叫的測試隔離

任何 `@Scheduled`（例如定期同步股價 / 新聞的排程）建議搭配 `@Profile("!test")`，避免整合測試期間持續觸發真實外部呼叫；外部股價 / 新聞 / 專利來源在測試環境一律 mock，不打真實 API。

## 驗證節奏（避免反覆全量）

開發期間依下列節奏：

1. **TDD 紅綠、review 修正、逐步除錯階段**：只跑相關測試
   `./mvnw -q surefire:test -Dtest=<TestClass>`（需先 `-DskipTests test-compile` 讓新測試進 target）。
   禁止每改一次就跑全量。
2. **任務收尾才跑一次全量**：兩段式 `clean test-compile` → `surefire:test`（快測）
   → `failsafe:integration-test`（整合測試），或直接 `./mvnw verify`（surefire+failsafe 一次到位）。
3. 需要 JaCoCo 報告時才另跑 `clean verify`；日常驗證不需要。
4. 長時間 build 一律背景執行，**靠 harness 的 `<task-notification>` 完成通知**取回結果——背景任務完成時 harness 會自動 re-invoke 並附 output 檔路徑，不需自己等。
   - **禁止**為了等背景 build/test 而寫 `until/while … grep … sleep` 輪詢迴圈。harness 已會通知，輪詢是純浪費，且哨兵一旦抓不到就無限 `sleep` 卡死。
   - 收到通知後**只讀一次** output 檔取結果，不邊跑邊 tail、不重複觸發。
   - 若真需判斷成敗，以 **exit code** 或 surefire 的 `Tests run:` 行為依據；**禁止**用 `mvn -q` 會抑制掉的 `BUILD SUCCESS` / `BUILD FAILURE` 當迴圈哨兵（`-q` 下該行不會印出，條件永不成立 → 無限迴圈）。

## 測試方法命名規則

**測試方法名稱一律使用英文，禁止使用中文識別子。**

- method name：英文 camelCase，格式建議 `methodName_scenario_expectedBehavior`
- 中文描述放在 `@DisplayName` annotation 中

```java
@Test
@DisplayName("查無對應公司代號時應拋出 404 ServerException")
void getCompanyByCode_notFound_shouldThrowServerException() throws Exception {
    ...
}
```

## 覆蓋率要求

- Line / Branch coverage 目標 ≥ 80%，作為自查基準；JaCoCo 導入時先設為 report only，不因未達標而 fail build，待專案穩定後再視情況加上 check gate。
