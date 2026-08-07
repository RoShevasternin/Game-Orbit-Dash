package com.lewydo.orbitdash.game.utils.font.msdf

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// MsdfFont — обгортка BitmapFont для MSDF/MTSDF атласів.
//
//   ДВА формати JSON (автовизначення):
//     1) msdf-atlas-gen (Chlumsky): atlas/metrics/glyphs, em, yOrigin=top.
//     2) msdf-bmfont (сайт donmccurdy): chars/common/info, px.
//
//   ═══ КОНВЕНЦІЯ YOFFSET — З ОФІЦІЙНОГО ЛОАДЕРА libGDX ═══
//   BitmapFontData.load() робить (для непереверненого шрифта):
//       glyph.yoffset = -(glyph.height + bmYoffset)
//   де bmYoffset = відстань від ВЕРХУ РЯДКА вниз до верху квада гліфа.
//   Тобто внутрішній yoffset ВІД'ЄМНИЙ і вказує на НИЗ квада відносно
//   верху рядка (у y-вгору координатах). Додатний yoffset (як у BMFont
//   .fnt-файлі) підняв би гліфи НАД рядком — саме так виглядав баг
//   «символи вилітають вверх за debug-рамку».
//
//   ═══ УСІ ВЕЛИЧИНИ — З ДАНИХ JSON (S=size, R=distanceRange) ═══
//   Жодних магічних чисел. Працює для R=8, 16, 32... автоматично:
//     pad        = R/2                    (SDF-роздуття квада з кожного боку)
//     base       = |ascender|×S           (верх рядка → baseline)
//     bmYoffset  = base + planeTop×S      (верх рядка → верх квада)
//     yoffset    = -(bmYoffset + height)  (gdx-конвенція, від'ємний)
//     capHeight  = квад('X') − 2·pad      (ВИДИМА висота капіталей)
//     ascent     = base − capHeight       (верх рядка → верх капіталей)
//     descent    = yoffset('g')+base+pad  (видимий хвіст, від'ємний)
//
//   З цими метриками РІДНИЙ Label дає стандартну типографіку:
//     prefHeight = capHeight − 2·descent → хвости точно до низу рамки,
//     зверху відступ = |descent| — як у звичайних Label гри.
// ─────────────────────────────────────────────────────────────────────────────

class MsdfFont(
    jsonPath : String,
    pngPath  : String,
) : Disposable {

    val texture      : Texture
    val bitmapFont   : BitmapFont
    val texWidth     : Float
    val texHeight    : Float
    val glyphSize    : Float      // S
    val distanceRange: Float      // R
    val hasTrueSdf   : Boolean    // mtsdf → alpha = справжній SDF (для ефектів)
    /** Верх рядка → baseline (px гліфа). Потрібен для Figma-точного розміщення. */
    var basePx = 0f; private set

    init {
        val json = JsonReader().parse(Gdx.files.internal(jsonPath))

        // Без mipmap! Mip-рівні змішують сусідні гліфи атласу → тонкі
        // лінії-привиди по краях. Чіткість дає SDF-поле, mip-и тут шкодять.
        texture = Texture(Gdx.files.internal(pngPath), false).apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
        texWidth  = texture.width.toFloat()
        texHeight = texture.height.toFloat()

        val region = TextureRegion(texture)
        val data: BitmapFont.BitmapFontData

        if (json.has("atlas") && json.has("glyphs")) {
            val atlas = json.get("atlas")
            glyphSize     = atlas.getFloat("size")
            distanceRange = atlas.getFloat("distanceRange")
            hasTrueSdf    = atlas.getString("type", "msdf") == "mtsdf"
            data = buildAtlasGen(json)
        } else {
            glyphSize     = json.get("info").getFloat("size")
            distanceRange = json.get("distanceField").getFloat("distanceRange")
            hasTrueSdf    = false
            data = buildBmfont(json)
        }

        bitmapFont = BitmapFont(data, region, false).apply { setUseIntegerPositions(false) }
    }

    /** Увімкнути кольорову розмітку в текстах ЦЬОГО шрифта:
     *      "[#FF5500]слово[] далі звичайним"
     *  УВАГА: діє на ВСІ лейбли цього шрифта; літеральна '[' пишеться як "[[".
     *  Markup фарбує й шари ефектів (stroke стане різнокольоровим) — для
     *  markup-текстів краще без ефектів або збирай через MsdfTextRow. */
    fun enableColorMarkup() { bitmapFont.data.markupEnabled = true }

    /** Максимальний stroke НАЗОВНІ (дизайн-px) — фізичний ліміт поля R/2. */
    fun maxStrokeOutside(worldSize: Float): Float =
        (distanceRange * 0.5f - 0.6f) * worldSize / glyphSize

    // ════════════════════════════════════════════════════════════════════════
    // Формат 1: msdf-atlas-gen (em, yOrigin=top → planeTop<0 над baseline)
    // ════════════════════════════════════════════════════════════════════════

    private fun buildAtlasGen(json: JsonValue): BitmapFont.BitmapFontData {
        val s   = glyphSize
        val pad = distanceRange / 2f
        val m   = json.get("metrics")

        // base = верх рядка → baseline (ascender від'ємний при yOrigin=top)
        val base = abs(m.getFloat("ascender")) * s
        val descenderPx = abs(m.getFloat("descender")) * s   // fallback для descent

        val data = BitmapFont.BitmapFontData()
        data.setScale(1f)
        data.lineHeight = m.getFloat("lineHeight") * s
        data.down       = -data.lineHeight

        var g = json.get("glyphs").child
        while (g != null) {
            val id = g.getInt("unicode", -1)
            if (id in 0..65535) {
                val glyph = BitmapFont.Glyph()
                glyph.id = id
                glyph.xadvance = (g.getFloat("advance") * s).roundToInt()

                val pb = g.get("planeBounds")
                val ab = g.get("atlasBounds")
                if (pb != null && ab != null) {
                    val pl = pb.getFloat("left");  val pt = pb.getFloat("top")
                    val pr = pb.getFloat("right"); val pbot = pb.getFloat("bottom")
                    val hPx = (pbot - pt) * s

                    glyph.width   = ((pr - pl) * s).roundToInt()
                    glyph.height  = hPx.roundToInt()
                    glyph.xoffset = (pl * s).roundToInt()

                    // bmYoffset = верх рядка → верх квада; gdx: -(bmYoff + h)
                    val bmYoff = base + pt * s
                    glyph.yoffset = (-(bmYoff + hPx)).roundToInt()

                    val al = ab.getFloat("left");  val at = ab.getFloat("top")
                    val ar = ab.getFloat("right"); val abot = ab.getFloat("bottom")
                    glyph.srcX = al.roundToInt(); glyph.srcY = at.roundToInt()
                    // UV з відступом ПІВ-ТЕКСЕЛЯ всередину: край квада ніколи
                    // не семплить межовий тексель клітинки (білінійна фільтрація
                    // на межі підмішує сусідню клітинку → білі смужки на краях).
                    // Втрачаємо 0.5px з ~16px порожнього padding — невидимо.
                    glyph.u  = (al + 0.5f) / texWidth;  glyph.v  = (at + 0.5f)   / texHeight
                    glyph.u2 = (ar - 0.5f) / texWidth;  glyph.v2 = (abot - 0.5f) / texHeight
                }
                data.setGlyph(id, glyph)
            }
            g = g.next
        }

        var k = json.get("kerning")?.child
        while (k != null) {
            data.getGlyph(k.getInt("unicode1").toChar())
                ?.setKerning(k.getInt("unicode2"), (k.getFloat("advance") * s).roundToInt())
            k = k.next
        }

        finish(data, base, descenderPx, pad)
        return data
    }

    // ════════════════════════════════════════════════════════════════════════
    // Формат 2: msdf-bmfont (сайт). px, BMFont-конвенція (yoffset від верху рядка)
    // ════════════════════════════════════════════════════════════════════════

    private fun buildBmfont(json: JsonValue): BitmapFont.BitmapFontData {
        val pad = distanceRange / 2f
        val common = json.get("common")
        val base = common.getFloat("base")

        val data = BitmapFont.BitmapFontData()
        data.setScale(1f)
        data.lineHeight = common.getFloat("lineHeight")
        data.down       = -data.lineHeight

        var c = json.get("chars").child
        while (c != null) {
            val id = c.getInt("id")
            if (id in 0..65535) {
                val glyph = BitmapFont.Glyph()
                glyph.id      = id
                glyph.srcX    = c.getInt("x");     glyph.srcY   = c.getInt("y")
                glyph.width   = c.getInt("width"); glyph.height = c.getInt("height")
                glyph.xoffset = c.getInt("xoffset")
                // BMFont yoffset = від верху рядка; gdx: -(bmYoff + h)
                glyph.yoffset = -(c.getInt("yoffset") + glyph.height)
                glyph.xadvance = c.getInt("xadvance")
                glyph.u  = (glyph.srcX + 0.5f) / texWidth
                glyph.v  = (glyph.srcY + 0.5f) / texHeight
                glyph.u2 = (glyph.srcX + glyph.width  - 0.5f) / texWidth
                glyph.v2 = (glyph.srcY + glyph.height - 0.5f) / texHeight
                data.setGlyph(id, glyph)
            }
            c = c.next
        }

        var k = json.get("kernings")?.child
        while (k != null) {
            data.getGlyph(k.getInt("first").toChar())
                ?.setKerning(k.getInt("second"), k.getInt("amount"))
            k = k.next
        }

        // fallback descent зі співвідношення рядка (як типографічний)
        finish(data, base, data.lineHeight - base, pad)
        return data
    }

    // ── Спільний фінал: пробіл + метрики центрування (усе з даних) ──────────

    private fun finish(
        data: BitmapFont.BitmapFontData,
        base: Float,          // верх рядка → baseline
        descenderPx: Float,   // fallback, якщо немає гліфа з хвостом
        pad: Float,           // R/2
    ) {
        basePx = base

        // ── ГОРИЗОНТАЛЬНА компенсація SDF-padding + Figma side bearing ──
        // Квад гліфа ширший за літеру на pad з кожного боку; GlyphLayout
        // віднімає padLeft/padRight на краях рядка (вбудований механізм для
        // distance-field шрифтів). АЛЕ віднімаємо не весь pad, а pad МІНУС
        // природний бічний відступ шрифта (side bearing) — той самий
        // «малюсінький пробіл» по боках, що видно у Figma. Bearing береться
        // З ДАНИХ атласу (еталонний гліф 'H' з рівними боками): у Nunito це
        // ~3px гліфа → на тексті 90px ~5px з боку. Масштабується з fontScale
        // і однаковий на всіх екранах (world-координати).
        // padTop/padBottom не чіпаємо — вертикаль скомпенсована через
        // capHeight/descent.
        val bearingRef = data.getGlyph('H') ?: data.getGlyph('N')
        ?: data.getGlyph('n') ?: data.getGlyph('0')
        val lsb = if (bearingRef != null)
            (bearingRef.xoffset + pad).coerceAtLeast(0f)
        else glyphSize * 0.05f
        val rsb = if (bearingRef != null)
            (bearingRef.xadvance - (bearingRef.xoffset + bearingRef.width) + pad)
                .coerceAtLeast(0f)
        else lsb
        data.padLeft  = pad - lsb
        data.padRight = pad - rsb
        // Пробіл-fallback
        var space = data.getGlyph(' ')
        if (space == null) {
            space = BitmapFont.Glyph()
            space.id = 32
            val ref = data.getGlyph('l') ?: data.getGlyph('n') ?: data.getGlyph('1')
            space.xadvance = ref?.xadvance ?: (glyphSize * 0.28f).toInt()
            data.setGlyph(32, space)
        }
        data.spaceXadvance = (space.xadvance + space.width).toFloat()

        // capHeight = ВИДИМА висота капіталей (квад роздутий на pad з обох боків)
        val capG = data.getGlyph('X') ?: data.getGlyph('H') ?: data.getGlyph('A')
        data.capHeight = ((capG?.height?.toFloat() ?: base) - 2 * pad).coerceAtLeast(1f)

        val xG = data.getGlyph('x') ?: data.getGlyph('o')
        data.xHeight = ((xG?.height?.toFloat() ?: data.capHeight) - 2 * pad).coerceAtLeast(1f)

        // ascent = верх рядка → верх видимих капіталей
        data.ascent = base - data.capHeight

        // descent = видимий хвіст під baseline (від'ємний).
        // З gdx-yoffset: низ квада від верху рядка = -yoffset;
        // під baseline = -yoffset - base; видимий = мінус pad;
        // descent = -(−yoffset − base − pad) = yoffset + base + pad.
        val gG = data.getGlyph('g') ?: data.getGlyph('p') ?: data.getGlyph('y')
        data.descent = if (gG != null)
            (gG.yoffset + base + pad).coerceAtMost(0f)
        else
            -(descenderPx - pad).coerceAtLeast(0f)
    }

    override fun dispose() {
        bitmapFont.dispose()
        texture.dispose()
    }
}