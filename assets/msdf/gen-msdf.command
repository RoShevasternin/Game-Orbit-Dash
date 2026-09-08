#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# ОДИН КЛІК: SVG → MSDF-поля → libGDX-атлас. Як msdf-gen.command у шрифтів.
#
#   1. Кинь SVG у svg/ (один сплющений <path>, білий fill)
#   2. Подвійний клік
#   3. Атлас уже в OUT_DIR — далі збірка проєкту
#
# ІНШИЙ ПРОЄКТ = скопіювати всю папку msdf/ і поміняти КОНФІГ нижче (2 рядки).
# TexturePacker не потрібен: його CLI — платна фіча, що фарбує спрайти
# червоним; pack-msdf.py пакує байт-у-байт.
# ─────────────────────────────────────────────────────────────────────────────
cd "$(dirname "$0")" || exit 1

# ── КОНФІГ ───────────────────────────────────────────────────────────────────
ATLAS_NAME="msdf"                                       # ім'я: <name>.atlas + <name>.png
OUT_DIR="/Users/admin/Apps/Game Orbit Dash/Orbit Dash/app/src/main/assets/atlas"    # куди класти (відносно цієї папки або абсолютний)
PXRANGE=8                                               # ширина діапазону поля, текселів (± половина)
DIM=64                                                  # клітинка, px
# ─────────────────────────────────────────────────────────────────────────────

export MSDF_ATLAS_NAME="$ATLAS_NAME" MSDF_OUT_DIR="$OUT_DIR" PXRANGE DIM

echo "══ 1/2: msdfgen (PXRANGE=$PXRANGE, DIM=$DIM) ══"
sh gen-msdf.sh || { echo "✗ генерація впала"; read -r _; exit 1; }
echo
echo "══ 2/2: пакування → $OUT_DIR/$ATLAS_NAME.atlas ══"
python3 pack-msdf.py || { echo "✗ пакування впало"; read -r _; exit 1; }
echo
echo "Готово. Тисни Enter."
read -r _
