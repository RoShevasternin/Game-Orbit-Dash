#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// GAUSSIAN BLUR — один прохід (H або V) із ПОВНИМ ядром під поточну σ.
//
//   Ваги й зсуви рахує BlurEffect на CPU під точну σ у текселях цього буфера
//   і віддає масивами. Кожна пара сусідніх текселів береться ОДНИМ білінійним
//   семплом на дробовому зсуві (linear sampling): вага пари = w1 + w2, зсув =
//   зважене середнє — це точно та сама дискретна згортка, лише вдвічі менше
//   fetch-ів. Тому 37 семплів покривають ±36 текселів = 3σ при σ = 12.
//
//   Один прохід H + один V = точний 2D-гаус будь-якої σ ≤ MAX_PAIRS·2/3
//   текселів. Більшу σ BlurEffect не просить: він спершу ділить буфер пірамідою.
//   Раніше σ набиралась повторами 9-tap ядра — до 13 пар проходів на кадр;
//   тепер завжди одна пара. Ціна проходу на тайловому GPU (перемикання render
//   target) набагато вища за ціну зайвих семплів на малому буфері.
//
//   highp для координат: на буфері 480 px mediump (fp16) дає похибку ~0.5 px.
// ─────────────────────────────────────────────────────────────────────────────

#define MAX_PAIRS 18

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2  u_texelStep;              // (1/w, 0) для H або (0, 1/h) для V
uniform int   u_pairs;                  // скільки пар справді задано (≤ MAX_PAIRS)
uniform float u_center;                 // вага центрального текселя
uniform float u_offset[MAX_PAIRS];      // зсув пари в текселях (дробовий)
uniform float u_weight[MAX_PAIRS];      // вага пари (на обидва боки однакова)

void main() {
    vec4 sum = texture2D(u_texture, v_texCoords) * u_center;
    for (int i = 0; i < MAX_PAIRS; i++) {
        if (i >= u_pairs) break;
        vec2 d = u_texelStep * u_offset[i];
        sum += (texture2D(u_texture, v_texCoords + d) + texture2D(u_texture, v_texCoords - d)) * u_weight[i];
    }
    gl_FragColor = sum;
}