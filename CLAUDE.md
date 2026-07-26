# AI Agent Charter（AI 代理憲章）

## ⚠️ 關鍵指令

**必須使用繁體中文（Traditional Chinese）與使用者溝通。**

**規則優先序：使用者當下指令 > 專案規則（`CLAUDE.md`、`.claude/rules/`）> 全域 `~/.claude/CLAUDE.md` > harness / skill 內建預設（含 commit 署名等）。衝突時依此取捨。**

**實作與修 Bug 的強制流程（無例外）：**

1. 修 Bug：先寫「重現該 bug 的失敗測試」，確認 fail，再修，確認 pass。
   禁止跳過重現直接改 code。
2. 新功能（含邏輯行為）：先寫測試（紅）→ 最小實作（綠）→ 重構。
   禁止為遷就實作回頭改測試語意。
3. Trivial 改動（typo、log 訊息、註解、純設定值）：可直接修改，
   但完成後必須執行相關測試驗證未破壞既有行為。
4. 宣告完成前必須附驗證證據（測試輸出、build 結果），禁止口頭宣稱。
5. `superpowers:test-driven-development` 與 `superpowers:systematic-debugging`
   可用時必須呼叫，作為以上流程的執行強化；不可用時，以上文字規則仍完整生效。

---

## 行為規範

**意圖判斷**
- 若使用者問題以「建議」、「分析」、「評估」、「怎麼做」等方向為主，先提供文字建議，禁止直接修改檔案，除非使用者明確表示「請實作」或「幫我改」。
- 若不確定使用者要的是分析還是實作，先釐清再動手。

**使用既有程式碼**：實作前先搜尋 codebase 既有 utility / helper / service / repository / pattern，有則沿用，禁止無故重造輪子。

**建立新檔案前必須確認**
- 建立任何新檔案前，原則上必須先告知使用者並取得確認。
- 例外（可直接建立）：TDD 測試檔、規則明定的產出物（error-log、`docs/CHANGELOG.md`、release notes、OpenSpec `openspec/changes/**` 產出）。
- 若使用者已明確要求建立特定文件或 reference，視為已授權。

**誠實回答行為問題**：被問「是否真的遵守某規則」時，直接說明實際行為；禁止理想化、補敘式、與實際執行不符的回答。

---

## 專案背景

**industrymap（產業地圖）**：以特定產品（例如一台腳踏車）為起點，往下拆解到零組件層級，
再把每個零組件對應到實際供應商 / 製造商，並彙整該公司的基本資料與動態情資：

- 公司代號（股票代號 / 統一編號）與基本資料
- 專利（該公司持有、與對應零組件相關的專利）
- 股價（現價與歷史走勢）
- 最近新聞

技術棧預計比照 `ais-backend`：Java 21 + Spring Boot + JPA（PostgreSQL）+ Maven，
外部資料（股價、新聞、專利）走非同步 job / queue 抓取與同步，避免同步阻塞 HTTP 請求。
實際 groupId / artifactId 待第一次 `mvn` 專案初始化時確定，本文件與 `.claude/rules/` 先以慣例
描述架構，實作時再對齊真實 package 名稱。

---

## 錯誤記錄與規則進化（記錄 → 消化 → 升格）

### 1. 記錄：發現已確認錯誤必寫檔

開發過程中**發現任何已確認的錯誤**（logic bug、wrong condition、edge case 處理錯誤、根因已確認的 bug）時，必須寫入固定檔案：

```text
~/.claude/dev-errors/error-log.md
```

- 僅記錄**已確認**的錯誤；推測、尚未驗證、誤報者不寫入。
- **append-only**：只新增，不覆寫、不刪除既有紀錄。資料夾或檔案不存在時自行建立。
- 每筆一個區塊，欄位順序固定（`dev-mistake-digest` 依此解析，不可變更格式）：

```markdown
## 2026-07-26 — <功能 / 模組名稱>
- category: <wrong-condition | off-by-one | null-undefined | boundary | wrong-variable | missing-return | async-await | state-order | wrong-default | error-handling>
- severity: <high | medium | low>
- file: <path:line>
- error: <錯誤行為一句話>
- trigger: <觸發的輸入>
- root-cause: <根因>
- fix-direction: <修正方向>
- project: industrymap
```

- `category` 優先沿用上方標籤，無對應時才新增簡短可複用標籤。
- 寫入後告知使用者：記錄幾筆、檔案路徑。

### 2. 消化與升格

使用者要求整理錯誤時執行 `dev-mistake-digest`；**出現 ≥ 3 次的錯誤模式依該 skill 內建升格流程提案轉成 hook 或規則**（經使用者確認才寫入）。

---

## Changelog 規則（每次改動必記錄）

**日常記錄**：每次完成 feat/fix/refactor 且有使用者可感知的行為變化時，必須在
`docs/CHANGELOG.md` 的 `[Unreleased]` 區塊記錄一筆，**與程式改動併入同一筆 commit**：

- 格式：`- (scope) 一句話描述行為變化 — YYYY-MM-DD · <author> · (pending)`
  - 日期：記錄當下日期（YYYY-MM-DD）。
  - `<author>`：`git config user.name` 的值。
  - hash 欄先寫 `(pending)`，升版時統一回填實際 commit hash。
  - 合併同主題項目時，日期更新為最後一次改動日、`<author>` 保留最初作者（跨人時併列）。
- 分類：`### 新增` / `### 修正` / `### 變更` / `### 移除`
- 純內部改動（test-only、CI、無行為變化的重構）不記錄。
- **追加前必須先掃 `[Unreleased]` 既有項目**：同 scope 同主題 → 合併改寫成一筆，
  不留流水帳；後續改動推翻先前項目 → 直接改寫原項目（僅限本分支寫入的項目）。
- 描述寫給人看的行為變化，不寫實作細節。
- `docs/CHANGELOG.md` 尚未建立時，第一次需要記錄的當下直接建立此檔案即可，不需另外詢問。

---

## Definition of Done（宣告完成前逐項自查）

宣告任務完成前，必須逐項檢查以下清單並回報結果；任一項不成立即不得宣告完成：

1. 本次任務相關測試通過並附實際輸出；任務收尾執行一次 `./mvnw clean verify` 確認整體通過（禁止口頭宣稱）。S 級（trivial）改動跑相關測試即可，免全量 verify。
2. 修改過邏輯類別時，已搜尋並同步所有引用該類別的測試檔，`./mvnw -DskipTests test-compile` 通過。
3. 涉及外部資料來源（股價 / 新聞 / 專利）呼叫失敗、逾時、資料缺漏的邊界情境已有對應測試或明確降級行為。
4. Schema 變更已評估並補上對應 Flyway migration（或明確確認不需要）。
5. 有使用者可感知行為變化時，`docs/CHANGELOG.md` 已記錄（含合併同主題既有項目）。
6. Commit 內容僅含本次任務範圍的後端程式與文件。

---

## 禁止事項

- 禁止將密碼、API keys、token 寫入程式碼、設定檔或 log。
- 禁止在尚未釐清根因前直接修改 bug 相關程式碼。
- 禁止發現已確認錯誤後不寫入 `~/.claude/dev-errors/error-log.md`。

---

## 對話規範

完成後必須以繁體中文摘要：做了什麼（What）／為什麼這樣設計（Why）／需確認的問題（若有）／已知限制與後續建議（若有）。
