#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// DROP SHADOW — окремий шар тіні (Figma Drop shadow: x, y, blur, color).
//
//   Малюється ПІД текстом і stroke (layer=-2). Принцип: семплюємо поле
//   відстаней у ЗСУНУТІЙ точці (uv - offset) — де зсунутий силует «є»,
//   там тінь. Blur = розширення smoothstep (безкоштовно в SDF).
//
//   Конвенція осей ЯК У FIGMA: y+ = ВНИЗ (u_offsetUV уже сконвертований).
//   На mtsdf (u_hasSdf=1) тінь бере альфа-канал (справжній SDF) — чиста,
//   без median-артефактів на стиках штрихів.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір тіні (через glyphColor лейбла)
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_distanceRange;
uniform float u_fontScale;
uniform float u_hasSdf;
uniform vec2  u_offsetUV;   // зсув семпла в UV (з дизайн-px на CPU)
uniform float u_blurPx;     // радіус розмиття у px гліфа

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

void main() {
    vec2 uv = v_texCoords - u_offsetUV;
    vec4 t = texture2D(u_texture, uv);
    float d = (mix(median(t.rgb), t.a, u_hasSdf) - 0.5) * u_distanceRange;

    float aa = 0.5 / max(u_fontScale, 0.001);
    float s  = max(u_blurPx, aa);

    // поріг 0 (контур), band ±s → м'яка тінь
    float alpha = smoothstep(-s, s, d);
    if (alpha < 0.004) discard;
    gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
}
