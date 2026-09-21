#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// MASK PROGRESS — універсальний прогрес: форму задає МАСКА-текстура.
//
// BarProgressEffect малює капсулу математикою і тому обмежений капсулою. Тут
// форма приходить картинкою: альфа маски каже, ДЕ взагалі є пікселі. Дірка в
// масці (α = 0) лишиться діркою, навіть якщо заповнення туди дійшло; α = 0.5 дає
// напівпрозорий піксель. Рівно поведінка «намалювати крізь маску», але без FBO.
//
// ТРИ КАРТИНКИ, усі необов'язкові:
//   u_texture   — МАСКА, вона ж drawable актора: керує альфою всього;
//   u_fillTex   — чим заповнюється (немає → u_fillColor);
//   u_trackTex  — фон під заповненням (немає → u_trackColor + u_trackA).
// Кольори лишаються тінтом і в текстурному режимі: білий = картинка як є.
//
// НАПРЯМОК вільний: u_axis (0 — уздовж X, 1 — уздовж Y знизу вгору) і
// u_reversed (від протилежного краю). Рахунок ведеться в «просторі прогресу»,
// тому зсув картинки (u_slide) працює однаково для всіх чотирьох напрямків.
//
// u_slide: 0 — картинка прибита, край її ПРОЯВЛЯЄ; 1 — картинка ЇДЕ так, що її
// кінець тримається краю заповнення (те саме, що посунути її під маскою).
//
// u_soft — м'якість краю заповнення у частках довжини. 0.002 ≈ пів пікселя на
// смузі 200 юнітів; 0 дає різкий край зі сходинками на похилих масках.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_localUV;      // 0..1 у межах регіону маски
varying vec2 v_texCoords;    // координати маски в атласі

uniform sampler2D u_texture;    // маска
uniform sampler2D u_fillTex;
uniform sampler2D u_trackTex;

uniform vec4  u_fillUv;         // xy = min, zw = max (межі регіону заповнення)
uniform vec4  u_trackUv;
uniform float u_useFillTex;     // 1 — заповнення картинкою
uniform float u_useTrackTex;    // 1 — фон картинкою

uniform vec3  u_fillColor;
uniform vec3  u_trackColor;
uniform float u_fillA;          // альфа заповнення
uniform float u_trackA;         // альфа фону; 0 — фону немає
uniform float u_frac;           // 0..1
uniform float u_axis;           // 0 — X, 1 — Y (знизу вгору)
uniform float u_reversed;       // 1 — від протилежного краю
uniform float u_slide;          // 1 — картинка їде за краєм
uniform float u_soft;           // м'якість краю заповнення

void main() {
    // Маска — перша й головна: її альфа множить усе наприкінці
    float m = texture2D(u_texture, v_texCoords).a;

    // У «простір прогресу»: q.x — уздовж напрямку, q.y — поперек.
    // v_localUV.y дивиться вниз, тому для осі Y беремо 1 - y (знизу вгору).
    vec2 q = (u_axis < 0.5) ? v_localUV : vec2(1.0 - v_localUV.y, v_localUV.x);
    q.x = mix(q.x, 1.0 - q.x, u_reversed);

    float f    = clamp(u_frac, 0.0, 1.0);
    float soft = max(u_soft, 0.0001);

    // 1 усередині заповнення, 0 за ним
    float fillMask = 1.0 - smoothstep(f - soft, f + soft, q.x);
    fillMask *= step(0.0001, f);          // нуль — лишається сам фон

    // Зсув картинки заповнення рахуємо в тому ж просторі, потім назад у UV
    vec2 qs = vec2(clamp(q.x + u_slide * (1.0 - f), 0.0, 1.0), q.y);
    qs.x    = mix(qs.x, 1.0 - qs.x, u_reversed);
    vec2 luv = (u_axis < 0.5) ? qs : vec2(qs.y, 1.0 - qs.x);

    vec4 fillTex  = texture2D(u_fillTex,  u_fillUv.xy  + luv       * (u_fillUv.zw  - u_fillUv.xy));
    vec4 trackTex = texture2D(u_trackTex, u_trackUv.xy + v_localUV * (u_trackUv.zw - u_trackUv.xy));

    vec3  fRGB = u_fillColor  * mix(vec3(1.0), fillTex.rgb,  u_useFillTex);
    float fA   = fillMask * u_fillA * mix(1.0, fillTex.a,    u_useFillTex);

    vec3  tRGB = u_trackColor * mix(vec3(1.0), trackTex.rgb, u_useTrackTex);
    float tA   = u_trackA     * mix(1.0,       trackTex.a,   u_useTrackTex);

    // Заповнення НАД фоном (alpha over) — у премультиплікованому просторі,
    // як у roundRectFS: інакше на межі був би кант не того відтінку
    float over = tA * (1.0 - fA);
    float a    = fA + over;

    vec3 rgb = (fRGB * fA + tRGB * over) / max(a, 0.0001);

    gl_FragColor = vec4(rgb, a * m) * v_color;
}
