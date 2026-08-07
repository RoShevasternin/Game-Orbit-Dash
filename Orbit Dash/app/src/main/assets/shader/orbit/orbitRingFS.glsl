#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// ORBIT RING — порожнє коло заданої товщини і кольору.
//
// Радіус і товщина приходять у частках ширини квада, тому шейдер не залежить
// від розміру групи: 90×90 і 900×900 дадуть однакову картинку.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір/альфа актора (фейди груп працюють самі)
varying vec2 v_localUV;    // 0..1 у межах регіону (дає BATCH_VERT)

uniform float u_hOverW;    // height/width квада — аспект-корекція
uniform float u_radius;    // радіус кола
uniform float u_width;     // товщина обводки
uniform float u_aa;        // згладжування країв
uniform vec3  u_color;     // колір кільця

void main() {
    vec2 p = v_localUV - vec2(0.5);
    p.y *= u_hOverW;                       // відстань стає пропорційною світу

    float d = abs(length(p) - u_radius);   // відстань до лінії кола

    // Фейд СИМЕТРИЧНИЙ відносно грані: половина всередину, половина назовні.
    // Так товщина лишається рівно u_width, а обидва краї згладжені однаково.
    float halfW = u_width * 0.5;
    float a = 1.0 - smoothstep(halfW - u_aa * 0.5, halfW + u_aa * 0.5, d);

    gl_FragColor = vec4(u_color, a) * v_color;
}
