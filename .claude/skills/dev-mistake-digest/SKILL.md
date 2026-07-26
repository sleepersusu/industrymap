---
name: dev-mistake-digest
description: Use this skill when the user asks Claude Code to read the recorded development mistakes / logged logic errors and summarize the recurring ones into a consolidated "common mistakes" file. It reads the fixed error log produced during development (per CLAUDE.md「錯誤記錄與規則進化」), groups the entries by category, ranks the most frequent and most severe patterns, and writes a digest of common mistakes to a fixed file. It also proposes rule promotions for patterns recurring >= 3 times (hook or CLAUDE.md rule), pending user confirmation. Use it whenever the user says things like "整理常犯的錯誤", "整理開發時候犯的錯誤", "summarize my common dev mistakes", "what bugs do I keep making", "review the error log", "consolidate logged logic errors", or asks for a retrospective of recurring development errors. This skill summarizes and proposes only; it does not fix code.
version: 1.0.0
---

# Dev Mistake Digest Skill

## Purpose

Turn the raw history of confirmed logic errors into an actionable summary of **recurring
mistakes**. This is the read/consolidate counterpart to the logging convention defined in
`CLAUDE.md`「錯誤記錄與規則進化」, which appends each confirmed logic error into a fixed error log.

The output answers: *which mistakes does this developer keep making, how often, and how to
stop making them.*

This skill **reads and summarizes only**. It does not modify source code and does not fix
bugs.

---

# Fixed Files

Input (append-only error log written per `CLAUDE.md`):

```text
~/.claude/dev-errors/error-log.md
```

Output (the consolidated digest, overwritten each run):

```text
~/.claude/dev-errors/common-mistakes.md
```

If the input file does not exist or is empty, **stop** and tell the user there are no
recorded errors yet, and that errors are logged automatically per the workflow in
`CLAUDE.md`. Do not invent data.

---

# Phase 0: Read the Log

1. Read the full error log file.
2. Parse each `## <date> — <feature>` block into a record with its fields
   (`category`, `severity`, `file`, `error`, `trigger`, `root-cause`, `fix-direction`,
   `project`).
3. Tolerate minor format drift: if a field is missing, keep the record but mark the field as
   `unknown`. Do not silently drop records.
4. Count total records and the date range covered. Report these numbers.

---

# Phase 1: Group and Rank

1. **Group by `category`.** Each category is one mistake pattern.
2. For each category compute:
   * count (how many times it occurred)
   * severity mix (how many high / medium / low)
   * which projects it appeared in (this log may be shared across multiple local projects)
3. **Rank** categories by a combination of frequency and severity — a high-severity mistake
   that recurs is more important than a one-off low-severity one. State the ranking rule you
   used.
4. Identify **cross-cutting patterns** that may span categories, e.g. "most errors involve
   unvalidated null/empty input" or "boundary handling is the weak spot". These are the
   insights the raw counts alone miss.

Do not fabricate trends from a tiny sample. If there are only a few records, say the sample is
small and treat conclusions as tentative.

---

# Phase 2: Derive Prevention Guidance

For each recurring category (count ≥ 2, or any single high-severity item), write **specific,
checkable prevention guidance** — not generic advice.

* Bad: "be more careful with conditions".
* Good: "when writing range checks, explicitly test the boundary value; `<` vs `<=` caused
  N off-by-one bugs in this log".

Tie each piece of guidance to the actual logged errors (reference their categories / files)
so it is grounded, not invented.

---

# Phase 3: Rule Promotion Proposals（升格提案，閉環）

對**出現 ≥ 3 次的錯誤模式**，必須提出升格提案，把「重複犯的錯」轉成「規則或攔截」：

1. 分流：
   * 可確定性攔截者 → 提案建立 **hook**（如格式檢查、路徑保護）。
   * 需判斷者 → 提案寫入 **CLAUDE.md 禁止事項**或對應 `.claude/rules/` 檔案。
2. 每個提案必須列出：模式名稱、出現次數、建議規則文字（可直接貼上的措辭）。
3. **僅提案，不寫入**：經使用者明確確認後，才可修改 CLAUDE.md / rules / hooks。
4. 已升格過的模式在提案區標註「已升格」，避免重複提案（比對現有 CLAUDE.md 禁止事項與 rules 內容）。

> 原則：規則文件是活的——同樣的錯犯三次，代表缺的不是提醒，是規則。

---

# Output: Write the Digest File

Overwrite `~/.claude/dev-errors/common-mistakes.md` with this structure
(convert the date to today's actual date):

```markdown
# 常犯開發錯誤彙整 (Common Dev Mistakes Digest)

更新時間：<today's date>
資料來源：error-log.md
紀錄總數：<N>　涵蓋期間：<earliest date> ~ <latest date>

## 1. 最常犯錯誤排行
| 排名 | 類別 (category) | 次數 | 嚴重度分布 | 出現專案 |
|---|---|---|---|---|

## 2. 各類別詳述
### <category> （<count> 次）
- 典型案例：<file:line — error>
- 共同根因：
- 預防方式（具體、可檢查）：

（每個 count ≥ 2 或含 high 嚴重度的類別各一段）

## 3. 跨類別模式
（橫跨多個類別的共通弱點，例如輸入驗證、邊界處理）

## 4. 行動建議
- 優先處理：
- 可加入的測試 / checklist 項目：

## 5. 樣本限制
（紀錄數量、時間範圍、是否足以下結論）

## 6. 升格提案（出現 ≥ 3 次的模式）
| 模式 | 次數 | 分流（hook / 規則） | 建議規則文字 | 狀態（新提案 / 已升格） |
|---|---|---|---|---|
```

After writing, report to the user: the file path, the total records processed, the top 3
recurring mistakes, and any promotion proposals awaiting confirmation.

---

# Behavioral Requirements

* Read-only on source code. This skill never edits code or fixes bugs.
* Only the digest output file is written/overwritten; never modify the input error log.
* Rule promotion is **proposal-only**: never write to CLAUDE.md / rules / hooks without the
  user's explicit confirmation; mark already-promoted patterns to avoid re-proposing.
* If the log is missing or empty, stop and say so — do not invent mistakes or trends.
* Ground every pattern and every piece of guidance in actual logged records; cite them.
* State the ranking rule and flag small-sample uncertainty instead of overclaiming.
* Keep prevention guidance specific and checkable, tied to real logged errors.
