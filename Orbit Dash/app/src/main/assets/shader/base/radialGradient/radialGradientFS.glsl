#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// RADIAL GRADIENT — радіальний градієнт зі стопами, як у Figma.
//
// Центр і радіус — у UV квада (0.5/0.5 і 0.5 = вписане коло, як Figma без
// трансформації). Стопи — до чотирьох, кожен: позиція 0..1 і колір з альфою.
// До першого стопа тримається його значення, після останнього — останнього,
// між ними лінійно — рівно так, як малює Figma.
//
// Базовий: жодного знання про гру. Запікається у VfxTexture (тоді ціна нуль)
// або живе у VfxImage, якщо стопи треба анімувати.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_localUV;

uniform vec2  u_center;      // центр у UV
uniform float u_radius;      // радіус у UV (0.5 = вписане коло)
uniform int   u_count;       // скільки стопів задіяно (1..4)
uniform float u_stopT[4];    // позиції стопів, зростають
uniform vec4  u_stopC[4];    // кольори стопів, пряма альфа
uniform float u_clip;        // 1 — за радіусом нічого (форма = коло, як <circle> у SVG)
uniform float u_aa;          // згладжування краю кола, у частках радіуса

void main() {
    float t = length(v_localUV - u_center) / max(u_radius, 1e-5);

    vec4 c = u_stopC[0];
    for (int i = 1; i < 4; i++) {
        if (i >= u_count) break;
        float a = u_stopT[i - 1];
        float b = u_stopT[i];
        float k = clamp((t - a) / max(b - a, 1e-5), 0.0, 1.0);
        c = mix(c, u_stopC[i], k);
    }

    // Figma на прямокутнику тягне останній стоп до країв; на колі — обрізає.
    // Наш квад — прямокутник, тож коло вирізаємо самі
    float inside = mix(1.0, 1.0 - smoothstep(1.0 - u_aa, 1.0 + u_aa, t), u_clip);

    gl_FragColor = c * v_color * vec4(1.0, 1.0, 1.0, inside);
}
