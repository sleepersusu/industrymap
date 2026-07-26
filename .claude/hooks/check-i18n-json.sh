#!/usr/bin/env bash
# PostToolUse guard：驗證 i18n locale JSON「語法合法性」+「key parity」。
#
# 觸發：Edit / Write / MultiEdit 到 i18n/*.json 之後。
# 檢查：
#   1. i18n/ 下每個 *.json 皆為合法 JSON（擋缺逗號 / 括號不對等語法錯誤）。
#   2. 以 en-US.json 為 key 的 source of truth，其餘 locale 的 key set 必須完全一致
#      （缺 key / 多 key 皆視為失敗，擋漏翻譯 / 殘留 key）。
# 失敗時以 exit 2 阻擋並把原因回饋給 Claude 修正；找不到 python 時 fail-open（不阻擋）。
# 專案若一開始不使用 i18n，此 hook 在 i18n/ 目錄不存在時不會有任何動作。
set -uo pipefail

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

proj="${CLAUDE_PROJECT_DIR:-.}"
input="$(cat)"

PY="$(command -v python 2>/dev/null || command -v python3 2>/dev/null || true)"
if [ -z "$PY" ]; then
  echo "[i18n-check] 略過驗證：環境找不到 python / python3" >&2
  exit 0
fi

fp="$(printf '%s' "$input" | "$PY" -c 'import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    print(""); sys.exit(0)
ti = d.get("tool_input") or {}
print(ti.get("file_path") or "")' 2>/dev/null || true)"

case "$fp" in
  *i18n*.json) ;;
  *) exit 0 ;;
esac

"$PY" - "$proj/i18n" <<'PYEOF'
import sys, json, glob, os

d = sys.argv[1]
files = sorted(glob.glob(os.path.join(d, "*.json")))
errs = []
loaded = {}
for f in files:
    try:
        with open(f, encoding="utf-8") as fh:
            loaded[f] = json.load(fh)
    except Exception as e:
        errs.append(f"語法錯誤 {os.path.basename(f)}: {e}")

src = next((f for f in loaded if os.path.basename(f) == "en-US.json"), None)
if not loaded:
    pass  # i18n 目錄為空，無可檢查
elif src is None:
    errs.append("找不到 en-US.json（key 的 source of truth），無法做 key parity")
else:
    src_keys = set(loaded[src])
    for f, data in loaded.items():
        if f == src:
            continue
        ks = set(data)
        missing = sorted(src_keys - ks)
        extra = sorted(ks - src_keys)
        if missing:
            errs.append(f"{os.path.basename(f)} 缺 {len(missing)} 個 key（相對 en-US）：{missing[:8]}{' …' if len(missing) > 8 else ''}")
        if extra:
            errs.append(f"{os.path.basename(f)} 多 {len(extra)} 個 key（不在 en-US）：{extra[:8]}{' …' if len(extra) > 8 else ''}")

if errs:
    sys.stderr.write(
        "[i18n-check] i18n locale 檔驗證未通過：\n- "
        + "\n- ".join(errs)
        + "\n（en-US.json 為 key 的 source of truth；請修正 JSON 語法與各語系 key 一致性後再繼續。）\n"
    )
    sys.exit(2)
PYEOF
rc=$?
exit $rc
