# Flyway Migration 觸發規則

完整規範（命名、SQL 內容、協作流程、PR 清單）建議另立 `docs/database-migration.md` 為唯一準則
（比照 `ais-backend`）；**尚未建立前，動 migration 先確認團隊當下慣例，避免各自為政**。

此處僅保留觸發判斷：

- 遇到資料表、欄位、index、constraint、extension 變更時（例如新增 `Company` / `Component` / `Product` 表、之後擴充 `Patent` / `StockPrice` / `NewsItem` 表），必須同步評估並新增 Flyway migration；不可只改 Java entity / repository / service。
- 新 migration 命名為日期版號 `VYYYY_MM_DD_HHMMSS__<description>.sql`；版號不可憑空假設，先查主線 `db/migration` 最新一支。
- 已發布的 migration 不可改檔名、version 或內容；缺漏用新 migration 補正。
- migration SQL 引用任何 table / column 前，必須以「該版序當下的 Flyway 鏈實際物理名稱」為準（**非** JPA `@Table` / `@Column` 名，**非**本機 ddl-auto 產物）：先 grep 先前 migration 確認該物件已 `CREATE` 且未被更晚的 migration `RENAME` / `DROP`。
- feature branch 禁止順手修改 full schema 的整合檔（如有）。
