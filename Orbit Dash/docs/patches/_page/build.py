#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Сторінка-патч: markdown-пояснення + кроки «було / стало» + кнопки
# ВСТАВИВ САМ / ВСТАВИТИ АВТОМАТИЧНО. Публікується як Artifact на claude.ai.
#
#   python3 build.py spec.json out.html
#
# spec.json:
#   {
#     "id": "47", "title": "Геми й очки", "date": "17.09.2026",
#     "order": "1 → 9, компілюється після 9",
#     "summary_md": "шлях до .md або сам текст",
#     "proj": "<корінь проєкту>/", "lab": "<корінь лабораторії>/",
#     "files": [ {"path": "app/...", "note": "необов'язково"}, ... ],
#     "state": {...}        # необов'язково: стан, який уже є на сторінці
#   }
#
# Кроки рахуються різницею proj ↔ lab: файл є лише в lab — НОВИЙ ФАЙЛ,
# лише в proj — ВИДАЛИТИ ФАЙЛ, інакше блоки з 2 рядками контексту.
# Кожна зміна має id «файл.зміна» (1.1, 8.3) — під ним її вибір і результат
# у state.hunks: {"8.3": {"choice": "manual"|"auto", "result": {"ok", "note"}}}.
# ─────────────────────────────────────────────────────────────────────────────
import difflib, html, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


# ── мінімальний markdown → HTML (заголовки, абзаци, списки, таблиці, `код`, **жирний**) ──
def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    return s


def md_to_html(md):
    out, para, lst, table = [], [], [], []

    def flush():
        if para:
            out.append("<p>" + inline(" ".join(para)) + "</p>"); para.clear()
        if lst:
            items, cur = [], None
            for line in lst:
                if line.startswith("- "):
                    if cur is not None: items.append(cur)
                    cur = line[2:]
                else:
                    cur = (cur or "") + " " + line.strip()
            if cur is not None: items.append(cur)
            out.append("<ul>" + "".join("<li>" + inline(i) + "</li>" for i in items) + "</ul>"); lst.clear()
        if table:
            rows = [r for r in table if not re.match(r"^\|\s*-", r)]
            cells = [[c.strip() for c in r.strip().strip("|").split("|")] for r in rows]
            h = "<tr>" + "".join("<th>" + inline(c) + "</th>" for c in cells[0]) + "</tr>"
            b = "".join("<tr>" + "".join("<td>" + inline(c) + "</td>" for c in r) + "</tr>" for r in cells[1:])
            out.append('<div class="tablewrap"><table><thead>' + h + "</thead><tbody>" + b + "</tbody></table></div>"); table.clear()

    for raw in md.splitlines():
        line = raw.rstrip()
        if line.startswith("|"):
            if para or lst: flush()
            table.append(line); continue
        if table: flush()
        if not line.strip() or line.strip() == "---":
            flush(); continue
        m = re.match(r"^(#{2,4})\s+(.*)", line)
        if m:
            flush(); out.append("<h3>" + inline(m.group(2)) + "</h3>"); continue
        if line.startswith("- "):
            if para: flush()
            lst.append(line); continue
        if lst and line.startswith("  "):
            lst.append(line); continue
        if lst: flush()
        para.append(line.strip())
    flush()
    return "\n".join(out)


# ── різниця файлів → кроки ──────────────────────────────────────────────────
def hunks(old_text, new_text, ctx=2):
    a, b = old_text.splitlines(), new_text.splitlines()
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    res = []
    for group in sm.get_grouped_opcodes(ctx):
        i1, i2 = group[0][1], group[-1][2]
        j1, j2 = group[0][3], group[-1][4]
        old_marks = [0] * (i2 - i1)
        new_marks = [0] * (j2 - j1)
        for tag, x1, x2, y1, y2 in group:
            if tag in ("replace", "delete"):
                for k in range(x1, x2): old_marks[k - i1] = 1
            if tag in ("replace", "insert"):
                for k in range(y1, y2): new_marks[k - j1] = 1
        tags = {t for t, *_ in group}
        verb = "add" if tags <= {"equal", "insert"} else "remove" if tags <= {"equal", "delete"} else "replace"
        res.append({
            "verb": verb,
            "where": f"біля рядка {i1 + 1}",
            "old": "\n".join(a[i1:i2]),
            "new": "\n".join(b[j1:j2]),
            "oldMarks": old_marks,
            "newMarks": new_marks,
        })
    return res


LANGS = {".kt": "kotlin", ".kts": "kotlin", ".glsl": "glsl", ".json": "json", ".xml": "xml",
         ".properties": "ini", ".toml": "ini", ".sh": "bash", ".command": "bash", ".md": "markdown",
         ".py": "python", ".html": "xml"}


def file_steps(proj, lab, path, note, fi):
    p_ex, l_ex = os.path.exists(proj + path), os.path.exists(lab + path)
    if l_ex and not p_ex:
        hs = [{"verb": "create", "where": "створити", "old": None, "new": read(lab + path).rstrip("\n")}]
    elif p_ex and not l_ex:
        hs = [{"verb": "delete", "where": "видалити файл", "old": None, "new": None}]
    else:
        hs = hunks(read(proj + path), read(lab + path))
    for hi, h in enumerate(hs, 1):
        h["id"] = f"{fi}.{hi}"
    ext = os.path.splitext(path)[1]
    return {"path": path, "note": note, "lang": LANGS.get(ext), "hunks": hs}


def build(spec):
    summary = spec["summary_md"]
    if os.path.exists(summary):
        summary = read(summary)
    data = {
        "id": spec["id"],
        "title": spec["title"],
        "date": spec.get("date", ""),
        "order": spec.get("order", ""),
        "summaryHtml": md_to_html(summary),
        "files": [file_steps(spec["proj"], spec["lab"], f["path"], f.get("note"), i) for i, f in enumerate(spec["files"], 1)],
        "state": spec.get("state") or {"submitted": None, "hunks": {}, "result": None},
    }
    tpl = read(os.path.join(HERE, "template.html"))
    page_title = f"Патч {spec['id']} · {spec['short']}" if spec.get("short") else f"Патч {spec['id']}"
    js = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
    return tpl.replace("@@TITLE@@", html.escape(page_title)).replace("@@DATA@@", js)


if __name__ == "__main__":
    spec = json.loads(read(sys.argv[1]))
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        f.write(build(spec))
    print("ok", sys.argv[2])
