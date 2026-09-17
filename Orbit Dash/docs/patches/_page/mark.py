#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Змінити лише STATE у вже зібраній сторінці-патчі, кроки не чіпаючи.
# Після вставки proj = lab, і build.py дав би порожні кроки — тому результат
# позначаємо тут.
#
#   python3 mark.py pages/47.html '{"choice":"auto","at":"…","result":{"ok":true,"by":"claude","at":"…","note":"збірка пройшла"}}'
# ─────────────────────────────────────────────────────────────────────────────
import json, re, sys

path, state = sys.argv[1], json.loads(sys.argv[2])
src = open(path, encoding="utf-8").read()

pat = re.compile(r'(<script id="patch-data" type="application/json">)(.*?)(</script>)', re.S)
m = pat.search(src)
if not m:
    sys.exit("patch-data не знайдено")

data = json.loads(m.group(2))
data["state"] = state
js = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
out = src[:m.start(2)] + js + src[m.end(2):]

with open(path, "w", encoding="utf-8") as f:
    f.write(out)
print("ok", path, state.get("choice"), (state.get("result") or {}).get("ok"))
