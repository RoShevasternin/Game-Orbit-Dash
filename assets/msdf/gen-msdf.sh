#!/bin/sh
# SVG з Figma → MTSDF-поля. Запускати з папки assets/msdf:  sh gen-msdf.sh
#
# PXRANGE — ширина діапазону поля в текселях (від -PXRANGE/2 до +PXRANGE/2).
#   ОДИН на весь атлас: те саме число має стояти в SpriteUtil.Msdf.PX_RANGE.
# DIM     — клітинка в px. -autoframe вписує фігуру з полем PXRANGE/2 з кожного боку,
#   тобто видима фігура займає (DIM - PXRANGE)/DIM клітинки (56/64 = 87.5%).
#
# Без Skia (brew) msdfgen бере лише ОСТАННІЙ <path> і не читає fill-rule —
# тому SVG має бути одним сплющеним шляхом, а evenodd (як пише Figma) кажемо явно.
set -e
# Параметри приходять з gen-msdf.command; дефолти — для прямого запуску sh gen-msdf.sh
PXRANGE=${PXRANGE:-8}
DIM=${DIM:-64}

mkdir -p png
for f in svg/*.svg; do
    n=$(basename "$f" .svg)
    # Клітинка — з viewBox: довша сторона = DIM, коротша пропорційно, плюс
    # PXRANGE поля. 20×64 → 26×64, 200×100 → 64×36. Без цього autoframe
    # вписав би фігуру в квадрат DIM×DIM і половина клітинки пішла б у порожнє.
    dims=$(python3 -c '
import re, sys
svg = open(sys.argv[1]).read(); dim, px = int(sys.argv[2]), int(sys.argv[3])
m = re.search(r"viewBox=\"\s*[-\d.]+\s+[-\d.]+\s+([\d.]+)\s+([\d.]+)", svg)
w, h = (float(m.group(1)), float(m.group(2))) if m else (1.0, 1.0)
k = (dim - px) / max(w, h)
print(round(w * k) + px, round(h * k) + px)' "$f" "$DIM" "$PXRANGE")
    cw=${dims% *}; ch=${dims#* }
    echo "── $n  ${cw}×${ch}"
    msdfgen mtsdf -svg "$f" -dimensions "$cw" "$ch" -pxrange $PXRANGE -autoframe \
            -fillrule evenodd -o "png/$n.png" -printmetrics
done
echo "готово: png/  (PXRANGE=$PXRANGE, DIM=$DIM)"