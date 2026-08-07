#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// ROUND RECT — заокруглений прямокутник: заливка і/або обводка.
//
// Вихід ЗАВЖДИ БІЛИЙ — колір дає тінт актора (v_color). Тому одна й та сама
// кнопка обслуговує будь-яку тему: міняється лише Color, не текстура.
//
// Розміри приходять у world-юнітах, тому радіус кутів не «пливе» при зміні
// розміру кнопки — 16 лишається 16 і на 300×54, і на 90×90.
//
// ВАЖЛИВО ПРО КРАЙ: d = 0 припадає РІВНО на межу квада (перевірено на середині
// сторони). Тому назовні місця під згладжування немає — уся AA-зона й уся
// обводка мусять іти ВСЕРЕДИНУ. Звідси спільна маска shape: заливка не може
// вилізти за обводку за побудовою.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_localUV;

uniform vec2  u_size;         // розмір квада у world-юнітах
uniform float u_radius;       // радіус кутів
uniform float u_aa;           // згладжування
uniform float u_fillAlpha;    // 0 = без заливки
uniform float u_strokeWidth;  // 0 = без обводки
uniform float u_strokeAlpha;

// Знакова відстань до заокругленого прямокутника: <0 усередині, >0 назовні
float roundedBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 p = (v_localUV - 0.5) * u_size;
    float d = roundedBox(p, u_size * 0.5, u_radius);

    // Спільний зовнішній контур: 1 усередині, 0 на краю квада.
    // Той самий для заливки й обводки — тому кант заливки неможливий.
    float shape = 1.0 - smoothstep(-u_aa, 0.0, d);

    float fill = shape * u_fillAlpha;

    float stroke = 0.0;
    if (u_strokeWidth > 0.0001) {
        // Внутрішня межа смуги: та сама фігура, зменшена на u_strokeWidth
        float inner = 1.0 - smoothstep(-u_strokeWidth - u_aa, -u_strokeWidth, d);
        stroke = clamp(shape - inner, 0.0, 1.0) * u_strokeAlpha;
    }

    // Обводка НАД заливкою (alpha over), а не max: інакше рамка з меншою
    // альфою за заливку просто зникає замість того, щоб її підсвітити.
    float a = fill + stroke * (1.0 - fill);

    gl_FragColor = vec4(1.0, 1.0, 1.0, a) * v_color;
}