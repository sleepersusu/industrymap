#!/usr/bin/env bash
# PreToolUse(Bash|PowerShell) 守門：擋下「背景 build/test 的輪詢迴圈」。
#
# 為什麼：背景任務完成時 harness 會自動以 <task-notification> 通知並附 output 檔路徑，
#         自己寫 until/while + sleep 輪詢是純浪費，且哨兵一旦抓不到就無限 sleep 卡死
#         （尤其 mvn -q 會抑制掉 BUILD SUCCESS/FAILURE → 條件永不成立 → 卡數小時）。
# 規範：.claude/rules/testing.md 驗證節奏 #4。
#
# 觸發條件：需同時命中「build/output 哨兵」＋「真正的迴圈結構」，避免只是 commit
# 訊息／echo 內文提到這些字就被誤擋。迴圈結構要求：
#   - Bash：until/while + sleep + 迴圈終結 done
#   - PowerShell：Start-Sleep + while/do/for/foreach
# stdin 為 hook 的 JSON（含 .tool_input.command）；直接對原文 grep，免依賴 jq。

c=$(cat)

has_sentinel()   { printf '%s' "$c" | grep -qiE 'BUILD|Tests run|surefire|mvnw|\.output'; }
is_bash_loop()   { printf '%s' "$c" | grep -qiwE 'until|while' \
                   && printf '%s' "$c" | grep -qiwE 'sleep' \
                   && printf '%s' "$c" | grep -qiwE 'done'; }
is_pwsh_loop()   { printf '%s' "$c" | grep -qiE 'Start-Sleep' \
                   && printf '%s' "$c" | grep -qiwE 'while|do|for|foreach'; }

if has_sentinel && { is_bash_loop || is_pwsh_loop; }; then
  printf '%s' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"偵測到背景 build/test 的輪詢迴圈（loop + sleep + build/output 哨兵）。禁止輪詢：長時間 build 一律背景執行後等 harness 的 <task-notification>，完成後只讀一次 output 檔取結果；要判斷成敗用 exit code 或 surefire「Tests run:」行，勿用 mvn -q 會抑制掉的 BUILD SUCCESS/FAILURE 當迴圈哨兵（會無限 sleep）。若真需等外部無法通知的狀態，改用 ScheduleWakeup / Monitor。見 .claude/rules/testing.md 驗證節奏 #4。"}}'
fi

exit 0
