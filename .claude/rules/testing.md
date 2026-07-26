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
