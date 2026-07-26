# 專案目標（Goal）

## 一句話

以特定產品為起點，拆解到零組件層級，畫出對應的供應商 / 製造商產業地圖；
情資層（股價、新聞、專利、公司合作關係）之後再逐步接上。

## 動機 / 要解決的問題

給一個產品（例如一台腳踏車），想知道：

- 它的各個零件（變速器、車架、煞車…）分別是哪家公司做的？
- 這家公司的代號是什麼（股票代號 / 統一編號）？
- 這家公司有什麼專利？
- 股價多少？
- 最近有什麼新聞？

目前這些資訊分散各處，沒有一個以「產品 → 零組件 → 公司」為主軸串起來的地圖。

## 開發主軸（重要，決定優先順序）

**主軸是產業地圖本身**：`Product` → `Component` → `Company` 的拆解與對應關係。
股價、新聞、專利、公司合作對象屬於後期才逐步接上的情資層，不是第一階段的重點。

### 第一階段（先做）

- 資料模型：`Product`（產品）、`Component`（零組件，可巢狀 / BOM 樹狀結構）、`Company`（公司，含代號與基本資料）。
- 核心關聯：一個產品可以拆解成多層零組件；每個零組件可以對應到一到多家供應商公司。
- 基本 CRUD 與查詢 API：輸入一個產品，能查出完整的零組件 → 公司對應清單。
- 目標成果：能回答「這台腳踏車的零件分別來自哪些公司」。

### 後期階段（之後再做）

- `Patent`（專利）：公司持有、與特定零組件相關的專利。
- `StockPrice`（股價）：公司股價快照與歷史走勢，需接外部行情來源，走非同步 job 定期同步。
- `NewsItem`（新聞）：與公司相關的最近新聞，需接外部新聞來源，走非同步 job 定期抓取。
- `CompanyRelation`（公司合作關係）：公司之間的供應鏈 / 合作 / 代工關係。

這些都需要串接外部資料來源，且不影響核心地圖查詢的即時性，所以規劃上刻意排在地圖本身穩定之後。

## 技術棧

比照 `ais-backend`：Java 21 + Spring Boot + JPA（PostgreSQL）+ Maven；
架構、程式風格、測試與 git 規範細節見 `.claude/rules/`（`architecture.md`、`api-design.md`、
`code-style.md`、`testing.md`、`git.md`、`flyway.md`），總則見根目錄 `CLAUDE.md`。

## 現況

Maven 專案骨架已建立：`pom.xml`（Spring Boot 3.5.16 + Java 21，groupId
`com.profetai.industrymap`）、`mvnw` wrapper、`architecture.md` 規劃的 package 空目錄
（controller / service / repository / model / payloads / clients / job / scheduler / config /
events / advice / exceptions / enums / helper / util）、共用的 `ServerResponse<T>` /
`ServerException` / `GlobalExceptionHandler`、`application.properties`（PostgreSQL + Flyway）。
`./mvnw compile` 與 `./mvnw test` 已驗證可成功建置（尚無實際資料模型與業務邏輯）。

`.claude/` 治理設定與 OpenSpec（`openspec/`）已就緒，下一步是用 `/opsx:propose` 開第一個
change（建議：先建 `Product` / `Component` / `Company` 的資料模型、Flyway migration 與基本
CRUD API）。
