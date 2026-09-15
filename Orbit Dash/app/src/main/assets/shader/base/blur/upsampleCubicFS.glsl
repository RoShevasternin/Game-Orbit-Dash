#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// UPSAMPLE CUBIC — підняти буфер піраміди назад кубічним B-сплайном.
//
//   Білінійний апскейл — кусково-лінійний: на гладкому гаусі видно злами
//   нахилу між текселями джерела (Mach-смуги), якщо σ там менша за ~12
//   текселів. B-сплайн — C¹-гладкий, зламів немає, тому дно піраміди може
//   бути з σ ≈ 6 — у 4 рази менше пікселів на прохід блюру.
//
//   Чотири білінійні семпли замість шістнадцяти (Sigg & Hadwiger, GPU Gems 2,
//   гл. 20): пари сусідніх текселів беруться одним семплом на зваженому зсуві.
//   Ціна — сплайн трохи домішує розмиття (σ² += 1/3 текселя джерела): при
//   σ = 6 це +0.5 % ширини, для Figma-еталона невидимо.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_srcSize;     // розмір джерела в текселях

void main() {
    vec2 coord = v_texCoords * u_srcSize - 0.5;     // центри текселів джерела — на цілих
    vec2 f     = fract(coord);
    coord     -= f;                                 // індекс базового текселя i

    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;      // тексель i-1
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;          // тексель i
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;// тексель i+1
    vec2 w3 = f3 / 6.0;                                   // тексель i+2

    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = coord - 1.0 + w1 / g0;    // зсув між i-1 та i, де білінійний семпл дає w0:w1
    vec2 h1 = coord + 1.0 + w3 / g1;    // між i+1 та i+2

    vec2 inv = 1.0 / u_srcSize;
    vec4 c = g0.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h0.y) + 0.5) * inv)
    + g1.x * texture2D(u_texture, (vec2(h1.x, h0.y) + 0.5) * inv))
    + g1.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h1.y) + 0.5) * inv)
    + g1.x * texture2D(u_texture, (vec2(h1.x, h1.y) + 0.5) * inv));
    gl_FragColor = c;
}