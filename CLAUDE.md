# Orbit Dash — публічний репозиторій сайту: тут лише зібрані файли

Тут лежить **зібраний** сайт гри й веб-версія: https://roshevasternin.github.io/Game-Orbit-Dash/
(GitHub Pages: гілка `main`, корінь).

**Руками тут нічого не правимо.** Усе робить збірка, і наступна публікація це перепише.

**Джерело** — приватний репозиторій **RoShevasternin/Game-Orbit-Dash-PRIVATE**, тека `site/`:
- шаблон сторінки — `site/src/index.html`, поведінка — `site/src/page.js`;
- тексти 15 мовами — `site/tools/strings_src.py` → `site/src/strings.json`;
- налаштування — `site/src/site.json`; політика — `site/src/privacy.html`;
- збірка — `site/build.py`, перевірка — `site/test.py`.

Повна інструкція — там, у `site/README.md`. Правила проєкту — у `Orbit Dash/CLAUDE.md`.
З власником спілкуйся українською, дружньо й просто.

## Як змінити сайт
Відкрий сесію Claude Code на **Game-Orbit-Dash-PRIVATE** (саме на ньому, а не на цьому репозиторії),
скажи, що змінити, і далі за `site/README.md`:
`python3 site/build.py --strict` → `python3 site/test.py` → публікація в цей репозиторій.

**Веб-версія гри** (`play/`) збирається з прототипу (`prototype/`) у публічному режимі.
Нове в грі робимо спершу в прототипі — див. https://roshevasternin.github.io/brand/#work
