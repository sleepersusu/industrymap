# 工作流規範（Workflow Guidelines）

## 任務分級（先分級，再選流程）

| 級別 | 判斷標準 | 流程 |
|---|---|---|
| **S（trivial）** | typo、log 訊息、註解、純設定值——diff 一句話能描述 | 直接修改，完成後跑相關測試（對齊憲章 trivial 豁免） |
| **M（單模組）** | 單模組小功能、修 bug、無 schema / 對外 API 變更 | TDD → simplify → verification → adversarial review；**跳過** brainstorming、OpenSpec、writing-plans |
| **L（跨模組）** | 跨模組連動、新機制、schema 變更、對外 API 變更 | 走下方完整流程鏈 |

不確定分級時往上取一級。

## 開發流程（L 級適用）

使用 **Superpowers brainstorming + OpenSpec + TDD 紀律**：

```text
[影響分析] 列出受影響的檔案、模組、下游依賴
  → brainstorming（設計探索、釐清需求與架構約束）
    → [設計複雜/需留存] superpowers 產出 design.md → opsx:propose（基於 design.md）
    → [設計簡單/上下文充足] opsx:propose（直接用當前 context）
      → [選擇性] writing-plans 展開 tasks.md 中的複雜任務（見下方說明）
        → opsx:apply + test-driven-development（每個 task 先測試再實作）
          → simplify（實作完成後審查品質、消除冗餘）
            → verification-before-completion（必須包含回歸測試）
              → adversarial review（非 trivial 實作必跑 /code-review，fresh-context 審 diff；
                 只修正確性 findings，不追風格與過度防禦建議，避免 over-engineering）
                → opsx:archive（歸檔並同步 delta specs）
                  → 更新 MD 文件（依本次實作，修訂 .claude/rules/ 下受影響的規範文件）
                    → 更新 docs/CHANGELOG.md（有使用者可感知行為變化時；hash 欄寫 (pending)，升版時回填；規則見 CLAUDE.md「Changelog 規則」）
                      → commit（changelog 與程式同一筆 commit；僅在使用者明確要求不要 commit 或本次僅為分析時跳過）
```

### Context 衛生（Subagent 使用規則）

- 大範圍調查（預估需讀超過 10 個檔案、或跨多個模組的探索）必須委派 subagent（Explore），只取回摘要，禁止在主對話逐檔讀入。
- 實作完成後的 diff review 交給 fresh-context subagent（`/code-review`），不由寫 code 的同一 context 自評。
- 主 context 只保留決策、計畫與實作必要資訊。

### 既有測試同步規則（修改邏輯類別時）

修改任何既有 Service / Consumer / Helper / Repository 後，必須先搜尋並更新所有引用該 class 的現有測試檔案，再繼續 TDD 流程，否則 `test-compile` 會失敗導致 build 中斷。詳見 `.claude/rules/testing.md`。

### 重構時的守衛判定（`dev-mistake-digest` 升格，2026-07-28）

刪改任何既有守衛（提前 return、額外的條件判斷、看似只為效能的檢查）前，先判定它是
**成本最佳化**還是**語意界定**：寫一個測試證明拿掉後行為不變，**證不出來就是語意守衛，不得刪改**。

註解只描述成本時尤其需要確認——實際案例：`resolveByIdentifier` 的
`exposableCompaniesOnly && distinctCompanyIds(...).size() > 1` 守衛，原註解只寫「只在真的可能歧義時
才多這一次查詢」，看起來純粹是省一次 DB 查詢；但它同時界定了語意（單一命中的審核狀態必須留給呼叫端
判斷）。重構時放寬成無條件執行，導致唯一命中且該公司已駁回時靜默回傳一家無關公司而非 404。

確認為語意守衛後，把註解改寫成語意理由而非成本理由，避免下一個人重蹈覆轍。

### DB Schema 變更規則

任何 schema 變更必須同步評估並新增 Flyway migration，不可只改 entity；規則見 `.claude/rules/flyway.md`。

### 複雜任務展開（選擇性）

`tasks.md` 單一任務涉及多檔連動、實作模式尚未建立、或需跨 session 執行時，可用 `writing-plans` 展開成 `plan.md`（僅針對該任務），並以 `superpowers:executing-plans` 搭配執行，避免重讀 codebase。
