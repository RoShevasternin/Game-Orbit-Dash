package com.lewydo.orbitdash.game.actors.label

import com.badlogic.gdx.scenes.scene2d.Group

// ─────────────────────────────────────────────────────────────────────────────
// MsdfTextRow — «багатий рядок»: кілька MsdfLabel РІЗНИХ шрифтів, розмірів,
// кольорів і ефектів В ОДИН РЯДОК, вирівняні по СПІЛЬНІЙ BASELINE
// (як мішаний текст у Figma — не по низу рамок, а по лінії письма).
//
//   val row = MsdfTextRow(gapPercent = 25f)
//       .add(MsdfLabel(msdf.REWARD, "1500", 90f))       // число велике
//       .add(MsdfLabel(msdf.HINT,   "coins"))           // слово менше, інший шрифт
//   row.setPosition(x, y)
//
//   ЗАЗОР ПРОПОРЦІЙНИЙ РОЗМІРУ (як em у Figma, як letterSpacing): gapPercent —
//   % від розміру ЛІВОЇ частини пари. Тому один і той самий gapPercent виглядає
//   правильно біля великого й дрібного тексту, і не треба підбирати число під
//   кожен розмір. Приклад: 25% біля тексту 80px = 20px, біля 40px = 10px.
//
//   (Абсолютний зазор у world-px теж можна — gapAbsolute; додається до
//    пропорційного. За замовчуванням 0.)
//
//   world-px стабільні між екранами (ExtendViewport масштабує сцену однаково),
//   тож на всіх пристроях виглядає ідентично.
//
//   МЕЖІ: це ОДИН рядок — переносу (wrap) між частинами немає.
// ─────────────────────────────────────────────────────────────────────────────

class AMsdfTextRow(
    /** Зазор у % від розміру лівої частини пари (Figma-стиль, масштабується). */
    var gapPercent: Float = 25f,
    /** Додатковий фіксований зазор у world-px (додається до пропорційного). */
    var gapAbsolute: Float = 0f,
) : Group() {

    private val parts = ArrayList<AMsdfLabel>(4)

    fun add(label: AMsdfLabel): AMsdfTextRow {
        parts.add(label)
        addActor(label)
        layoutRow()
        return this
    }

    /** Кількість частин. */
    val partCount get() = parts.size

    /** Частина за індексом — повний доступ до MsdfLabel (колір, ефекти...). */
    fun part(index: Int): AMsdfLabel = parts[index]

    /** Оновити текст частини. Ряд перекладається одразу. */
    fun setText(index: Int, text: CharSequence): AMsdfTextRow {
        parts[index].setText(text)
        layoutRow()
        return this
    }

    fun clearParts(): AMsdfTextRow {
        for (p in parts) p.remove()
        parts.clear()
        setSize(0f, 0f)
        return this
    }

    // ── Авто-релейаут ────────────────────────────────────────────────────────
    // Якщо будь-яка частина змінила розмір (setText на самій частині, зміна
    // worldSize/spacing) — помічаємо це в act() і перекладаємо ряд ще ДО
    // малювання кадру. Тож руками layoutRow() кликати не обов'язково.
    private var sizeStamp = -1f

    override fun act(delta: Float) {
        super.act(delta)
        var stamp = 0f
        for (p in parts) stamp += p.width * 31f + p.height
        if (stamp != sizeStamp) { sizeStamp = stamp; layoutRow() }
    }

    /** Перекласти частини: X — послідовно з ПРОПОРЦІЙНИМ зазором, Y — по
     *  спільній baseline. Викликати після зміни тексту/розміру частини. */
    fun layoutRow() {
        if (parts.isEmpty()) { setSize(0f, 0f); return }

        // спільна baseline ряду = найглибша серед частин
        var baseline = 0f
        for (p in parts) baseline = maxOf(baseline, baselineFromBottom(p))

        var x = 0f
        var top = 0f
        for (i in parts.indices) {
            val p = parts[i]
            p.setPosition(x, baseline - baselineFromBottom(p))
            x += p.width
            // зазор ПІСЛЯ частини (крім останньої): % від РОЗМІРУ цієї частини
            if (i < parts.size - 1) {
                x += p.worldSize * gapPercent / 100f + gapAbsolute
            }
            top = maxOf(top, p.y + p.height)
        }
        setSize(x, top)
    }

    // Висота baseline від НИЗУ рамки лейбла — залежить від режиму рамки:
    //   figmaBox: рамка = lineHeight, baseline на basePx від ВЕРХУ
    //             → від низу = (lineHeight − basePx) × scale
    //   щільна:   Label центрує капітелі, baseline сидить на |descent| від низу
    private fun baselineFromBottom(l: AMsdfLabel): Float {
        val d = l.font.bitmapFont.data
        return if (l.useFigmaBox)
            (d.lineHeight - l.font.basePx) * l.fontScaleY
        else
            -d.descent * l.fontScaleY
    }
}