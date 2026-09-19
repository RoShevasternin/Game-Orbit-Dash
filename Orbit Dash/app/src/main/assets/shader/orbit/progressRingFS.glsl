#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// PROGRESS RING — кільце із заповненням, один прохід (таймер комбо, щит).
//
// Доріжка (u_trackA) по всьому колу і заповнена дуга від 12-ї години за
// годинниковою на u_frac оберту; u_frac = 1 — суцільне кільце. Колір один, тож
// компонувати шари не треба: альфа — або доріжка, або повна. Усі довжини — у частках
// ширини квада, як в orbitRingFS.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір/альфа актора (фейди груп працюють самі)
varying vec2 v_localUV;    // 0..1 у межах регіону (дає BATCH_VERT)

uniform float u_hOverW;    // height/width квада — аспект-корекція
uniform float u_radius;    // радіус осі кільця
uniform float u_width;     // товщина кільця
uniform float u_aa;        // згладжування країв
uniform vec3  u_color;     // колір кільця
uniform float u_frac;      // заповнення 0..1 від 12-ї години за годинниковою
uniform float u_trackA;    // альфа незаповненої частини

const float TWO_PI = 6.2831853;

/** Покриття обводки товщиною w на відстані d від її осі (як в orbitRingFS). */
float stroke(float d, float w) {
    float halfW = w * 0.5;
    return 1.0 - smoothstep(halfW - u_aa * 0.5, halfW + u_aa * 0.5, d);
}

void main() {
    vec2 p = v_localUV - vec2(0.5);
    p.y *= u_hOverW;

    float ring = stroke(abs(length(p) - u_radius), u_width);

    // Частка оберту від 12-ї години ЗА годинниковою. v_localUV — UV текстури,
    // Y у нього вниз: (0,−1) — верх. atan(x, −y): верх → 0, правий бік → +¼.
    float t = fract(atan(p.x, -p.y) / TWO_PI);

    // Кінець дуги згладжений по дузі тією ж шириною u_aa, що й краї кільця
    float e    = u_aa / (TWO_PI * max(u_radius, 1e-4));
    float fill = 1.0 - smoothstep(u_frac - e, u_frac + e, t);
    fill = max(fill, step(1.0, u_frac));   // повне — без шва на 12-й
    fill *= step(1e-4, u_frac);            // нуль — сама доріжка

    float a = ring * mix(u_trackA, 1.0, fill);
    gl_FragColor = vec4(u_color, a) * v_color;
}
