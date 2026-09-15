#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// MASK + UPSAMPLE — маска й підняття джерела ОДНИМ проходом.
//
//   Маска — термінальний ефект: робочий буфер групи може бути в кілька разів
//   менший за вихідний (блюру достатньо σ ≈ 12 текселів, масці потрібен
//   різкий край). Тому основна текстура семплиться кубічним B-сплайном, а
//   маска — звичайно, у вихідній роздільності.
//
//   Блок sampleCubic — копія з shader/base/blur/upsampleCubicFS.glsl (GLSL не
//   має #include). Міняєш там — міняй і тут.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;    // робочий буфер (розмите тло)
uniform sampler2D u_mask;       // текстура маски (може бути атласна сторінка)
uniform vec4      u_maskUv;     // UV-межі маски: xy = (u, v), zw = (u2, v2)
uniform vec2      u_srcSize;    // розмір u_texture у текселях

// Кубічний B-сплайн через 4 білінійні семпли (Sigg & Hadwiger, GPU Gems 2, гл. 20)
vec4 sampleCubic(vec2 uv) {
    vec2 coord = uv * u_srcSize - 0.5;
    vec2 f     = fract(coord);
    coord     -= f;

    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    vec2 w3 = f3 / 6.0;

    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = coord - 1.0 + w1 / g0;
    vec2 h1 = coord + 1.0 + w3 / g1;

    vec2 inv = 1.0 / u_srcSize;
    return g0.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h0.y) + 0.5) * inv)
    + g1.x * texture2D(u_texture, (vec2(h1.x, h0.y) + 0.5) * inv))
    + g1.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h1.y) + 0.5) * inv)
    + g1.x * texture2D(u_texture, (vec2(h1.x, h1.y) + 0.5) * inv));
}

void main() {
    // v_texCoords (0..1 по буферу) → ремап у UV-простір регіону маски.
    // 1.0 - y зберігає стару Y-інверсію (FBO перевернутий відносно текстур).
    vec2 maskUV = mix(u_maskUv.xy, u_maskUv.zw, vec2(v_texCoords.x, 1.0 - v_texCoords.y));

    vec4 maskColor = texture2D(u_mask, maskUV);
    vec4 texColor  = sampleCubic(v_texCoords);

    // Приглушуємо і колір, і альфу на прозорих ділянках маски (premultiplied-friendly)
    texColor.rgb *= maskColor.a;
    texColor.a   *= maskColor.a;

    gl_FragColor = texColor * v_color;
}