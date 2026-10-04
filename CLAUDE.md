# Orbit Dash — публічний репозиторій сайту: тут лише зібрані файли

Тут лежить **зібраний** сайт гри й веб-версія: https://roshevasternin.github.io/Game-Orbit-Dash/
(GitHub Pages: гілка `main`, корінь).

**Руками тут нічого не правимо.** Усе робить збірка, і наступна публікація це перепише.

**Джерело** — приватний репозиторій **RoShevasternin/Game-Orbit-Dash-PRIVATE**, тека `site/`:
- шаблон сторінки — `site/src/index.html`, поведінка — `site/src/page.js`;
- жива гра в герої (і в блоці на сайті студії) — `play/embed.html`: прототип + режисер `site/src/embed.js`;
- тексти 15 мовами — `site/tools/strings_src.py` → `site/src/strings.json`;
- налаштування — `site/src/site.json`; політика — `site/src/privacy.html`;
- обкладинки, ролики, постер — `site/media.cjs` (знімає сам прототип);
- збірка — `site/build.py`, перевірка — `site/test.cjs`.

Повна інструкція — там, у `site/README.md`. Правила проєкту — у `Orbit Dash/CLAUDE.md`.
З власником спілкуйся українською, дружньо й просто.

## Як змінити сайт
Відкрий сесію Claude Code на **Game-Orbit-Dash-PRIVATE** (саме на ньому, а не на цьому репозиторії),
скажи, що змінити, і далі за `site/README.md`:
`python3 site/build.py --strict` → `node site/test.cjs` → публікація в цей репозиторій.
Після злиття в `main` приватного репозиторію це робить сама дія `.github/workflows/site.yml` (якщо там є секрет `SITE_TOKEN`).

**Веб-версія гри** (`play/`) збирається з прототипу (`prototype/`) у публічному режимі — тож вона завжди така,
як прототип у `main`.
Нове в грі робимо спершу в прототипі — див. https://roshevasternin.github.io/brand/#work
