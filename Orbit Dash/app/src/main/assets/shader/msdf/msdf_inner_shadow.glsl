#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// INNER SHADOW — тінь ВСЕРЕДИНІ літери (Figma Inner shadow: x, y, blur, color).
//
//   Малюється ПОВЕРХ заливки (layer=+1). Ефект «вдавленості»: біля краю з
//   боку, ПРОТИЛЕЖНОГО зсуву, з'являється затемнення що згасає всередину.
//
//   Принцип: беремо альфу гліфа (де ми ВСЕРЕДИНІ літери) і множимо на
//   (1 − альфа_зсунутого) — там, де зсунутий силует уже НЕ покриває, але
//   ми ще всередині літери, лягає тінь. Клампимо до заливки (тінь лише в
//   межах літери). Конвенція Figma: y+ = вниз.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір inner shadow (через glyphColor)
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_distanceRange;
uniform float u_fontScale;
uniform float u_hasSdf;
uniform vec2  u_offsetUV;   // зсув у UV
uniform float u_blurPx;

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

float distAt(vec2 uv) {
    vec4 t = texture2D(u_texture, uv);
    return (mix(median(t.rgb), t.a, u_hasSdf) - 0.5) * u_distanceRange;
}

void main() {
    float aa = 0.5 / max(u_fontScale, 0.001);

    // альфа ВСЕРЕДИНІ літери (поточна точка)
    float inside = smoothstep(-aa, aa, distAt(v_texCoords));
    if (inside < 0.004) discard;             // поза літерою — нічого

    // альфа зсунутого силуету, з розмиттям
    float s = max(u_blurPx, aa);
    float shifted = smoothstep(-s, s, distAt(v_texCoords - u_offsetUV));

    // тінь там, де ми всередині, але зсунутий силует НЕ покриває
    float shadow = inside * (1.0 - shifted);

    if (shadow < 0.004) discard;
    gl_FragColor = vec4(v_color.rgb, v_color.a * shadow);
}
