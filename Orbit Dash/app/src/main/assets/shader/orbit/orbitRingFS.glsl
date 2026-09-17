// highp: координата вздовж дуги доходить до ±u_dashCount/2 (~23 періоди). У
// mediump (Mali — справжні 16 біт) це крок 1/64 періоду, і торці штрихів у русі
// тремтять на піксель.
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// ORBIT RING — кільце орбіти, один прохід.
//
// Неактивне: одна обводка u_color. Активне (u_active = 1) — чотири концентричні
// шари, як у Figma ring-outer-active, знизу вгору:
//   glow2  кольору м'яча, ширша,  прозоріша
//   glow1  кольору м'яча, вужча,  щільніша
//   orbit  u_color (яскравіше кільце теми)
//   dash   пунктир кольору м'яча на u_dashR, обертається (u_dashPhase)
//
// Шари компонуються як «over» зі straight-альфою — те саме, що робить Figma з
// накладеними обводками. Усі довжини — у частках ширини квада, тому картинка
// не залежить від розміру групи.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір/альфа актора (фейди груп працюють самі)
varying vec2 v_localUV;    // 0..1 у межах регіону (дає BATCH_VERT)

uniform float u_hOverW;    // height/width квада — аспект-корекція
uniform float u_radius;    // радіус орбіти
uniform float u_width;     // товщина орбіти
uniform float u_aa;        // згладжування країв
uniform vec3  u_color;     // колір орбіти

uniform float u_active;    // 0 — лише орбіта; 1 — glow і пунктир
uniform vec3  u_player;    // колір м'яча: glow і пунктир
uniform vec2  u_glowW;     // товщини glow1, glow2
uniform vec2  u_glowA;     // їхні альфи
uniform float u_dashR;     // радіус пунктиру
uniform float u_dashW;     // товщина пунктиру
uniform float u_dashA;     // альфа пунктиру
uniform float u_dashCount; // штрихів по колу — ЦІЛЕ, інакше шов на 180°
uniform float u_dashFill;  // частка періоду під штрихом
uniform float u_dashPhase; // зсув візерунка, у періодах, 0..1

const float TWO_PI = 6.2831853;

/** Покриття обводки товщиною w на відстані d від її осі: фейд симетричний,
 *  половина всередину, половина назовні — товщина лишається рівно w. */
float stroke(float d, float w) {
    float halfW = w * 0.5;
    return 1.0 - smoothstep(halfW - u_aa * 0.5, halfW + u_aa * 0.5, d);
}

void main() {
    vec2 p = v_localUV - vec2(0.5);
    p.y *= u_hOverW;                       // відстань стає пропорційною світу

    float r = length(p);
    float d = abs(r - u_radius);           // відстань до осі орбіти

    float aOrbit = stroke(d, u_width);
    vec3  col = u_color * aOrbit;          // premultiplied, поки компонуємо
    float a   = aOrbit;

    if (u_active > 0.5) {
        // glow ПІД орбітою: два шари одного кольору, «over» дає у перетині
        // a1 + a2·(1−a1) — рівно як накладені обводки у Figma
        float g2 = stroke(d, u_glowW.y) * u_glowA.y;
        float g1 = stroke(d, u_glowW.x) * u_glowA.x;
        float ga = g1 + g2 * (1.0 - g1);
        col = u_color * aOrbit + u_player * ga * (1.0 - aOrbit);
        a   = aOrbit + ga * (1.0 - aOrbit);

        // пунктир ЗВЕРХУ. Координата вздовж дуги — у періодах штриха; AA по дузі
        // переводимо з u_aa через довжину періоду, щоб торці штрихів були
        // такі ж м'які, як краї кільця
        float ring   = stroke(abs(r - u_dashR), u_dashW);
        // v_localUV — це UV текстури, Y у нього ВНИЗ. Мінус повертає
        // математичний кут (проти годинникової), як в AOrbitField.positionAt
        float turns  = atan(-p.y, p.x) / TWO_PI;             // −0.5..0.5 оберту
        float s      = fract(turns * u_dashCount - u_dashPhase);
        float perLen = TWO_PI * u_dashR / u_dashCount;
        float e      = u_aa / perLen;
        float dash   = smoothstep(0.0, e, s) * (1.0 - smoothstep(u_dashFill - e, u_dashFill, s));
        float da     = ring * dash * u_dashA;
        col = u_player * da + col * (1.0 - da);
        a   = da + a * (1.0 - da);
    }

    // назад у straight-альфу — так чекає SpriteBatch
    gl_FragColor = vec4(col / max(a, 1e-4), a) * v_color;
}