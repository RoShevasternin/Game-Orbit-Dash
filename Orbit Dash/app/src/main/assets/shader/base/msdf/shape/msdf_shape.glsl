#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// MSDF-ФІГУРА — різка заливка з поля відстаней на будь-якому розмірі квада.
//
//   RGB = median → гострі кути (MSDF). A = справжній SDF (mtsdf) — тут лише як
//   глушник median-шуму в глибокій порожнечі; для ефектів — пізніше.
//
//   Юніформи ЛИШЕ на атлас (u_unitRange) — тому будь-яка кількість квадів
//   різного розміру з одного атласу може йти одним батчем. Масштаб береться
//   з fwidth(uv) щофрагмента (msdfgen README). Без похідних (рідкість на
//   GLES 2.0) — фолбек u_screenPxRange, як у msdf_fill.
//
//   Правило msdfgen: screenPxRange ніколи < 1; якщо < 2 — антиаліас ламається,
//   треба більший -pxrange. На 64px клітинці з pxrange 8 це стається при
//   зменшенні фігури до ~16 px на екрані.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2  u_unitRange;      // pxRange / vec2(atlasW, atlasH)
uniform float u_screenPxRange;  // фолбек: pxRange * (px екрана на тексель)

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

// Ширина антиаліасу — з похідних САМОЇ відстані, а не UV: тоді край правильний
// і при нерівномірному розтягу (9-patch, кнопка 300×56), і при обертанні.
// Без похідних (рідкість на GLES 2.0) — фолбек через u_unitRange/u_screenPxRange.
float screenPxRange() {
    #if defined(GL_OES_standard_derivatives) || !defined(GL_ES)
    vec2 screenTexSize = vec2(1.0) / fwidth(v_texCoords);
    return max(0.5 * dot(u_unitRange, screenTexSize), 1.0);
    #else
    return max(u_screenPxRange, 1.0);
    #endif
}

void main() {
    vec4 t = texture2D(u_texture, v_texCoords);

    // Далеко за контуром окремі RGB-канали «перемикаються», і median може
    // стрибнути >0.5 — біла точка в порожньому полі. Справжній SDF монотонний:
    // глушимо ним. 0.2 ≈ далі 2.4 текселя від контуру при pxrange 8.
    if (t.a < 0.2) discard;

    float d = median(t.rgb) - 0.5;
    #if defined(GL_OES_standard_derivatives) || !defined(GL_ES)
    // d/|∇d| = відстань до краю в екранних пікселях незалежно від масштабу по осях
    float alpha = clamp(d / max(fwidth(d), 1.0e-4) + 0.5, 0.0, 1.0);
    #else
    float alpha = clamp(d * screenPxRange() + 0.5, 0.0, 1.0);
    #endif
    if (alpha < 0.004) discard;

    gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
}