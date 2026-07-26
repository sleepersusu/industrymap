# Version Control（版本控制）

## Git Workflow

- 若任務屬於實作或修 Bug，且程式、測試、文件都已完成，預設應提交至少一筆對應 commit。
- 只有在使用者明確要求「不要 commit」、或本次任務僅為分析 / 建議 / 討論時，才可不建立 commit。
- 預設只提交本次任務直接相關的**後端程式**與**文件**。
- 前端依賴、建置產物、暫存檔、`node_modules`、以及非本次需求範圍的工作區內容，不可順手一起提交。
- 若本次任務同時明確包含前端程式修改，才可將對應前端原始碼納入 commit；否則視為不應提交範圍。

## Co-author

- 禁止在 commit message 中加入 `Co-Authored-By: Claude` 或任何 AI 署名。

## Commit Messages

- 格式：`type(scope): 簡短主旨`
- Types：`feat`、`fix`、`refactor`、`test`、`docs`、`chore`
- commit message 預設使用**繁體中文**。
- `subject` 必須簡潔說明這次變更的主題。
- `body` 必須補充具體內容，不可只有一行簡短主旨。
- `body` 應優先描述：
  - 哪些 service / module / 文件被修改
  - 各自改了什麼
  - 為什麼要這樣改
  - 補了哪些測試或回歸驗證
- 若同一筆 commit 同時包含程式與文件調整，`body` 需同時交代程式面與文件面內容。
- `fix` 為標準 type，不使用 `fixed`。

## Commit 撰寫原則

- 禁止籠統主旨（如 `fix bug`、`update docs`）；reviewer 光看 message 就要知道動了哪裡、行為怎麼變、有無測試與文件同步。
- commit 前再次檢查 stage 內容，確保僅含本次任務相關的後端程式與文件。

## Subject 範例

```text
feat(company): 新增依股票代號查詢公司對應零組件清單 API
```
