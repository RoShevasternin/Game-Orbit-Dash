#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// STROKE (OUTSIDE) — обведення назовні від контуру. Малюється ПІД заливкою.
//
//   Смуга = кільце від зовнішнього краю (u_outerPx, назовні = від'ємна відстань)
//   до контуру (0). Заливка малюється поверх → лишається зовнішня обводка.
//   На mtsdf альфа-SDF → чисто на будь-якій товщині (в межах range/2).
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_distanceRange;
uniform float u_fontScale;
uniform float u_hasSdf;
uniform float u_outerPx;     // від'ємне: наскільки далеко назовні

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

float distPx(vec2 uv) {
    vec4 t = texture2D(u_texture, uv);
    float d = mix(median(t.rgb), t.a, u_hasSdf);
    return (d - 0.5) * u_distanceRange;
}

void main() {
    float dPx = distPx(v_texCoords);
    float aa  = 0.5 / max(u_fontScale, 0.001);

    // силует розширений назовні до u_outerPx (усе, що ближче outer до контуру)
    float a = smoothstep(u_outerPx - aa, u_outerPx + aa, dPx);
    if (a < 0.004) discard;
    gl_FragColor = vec4(v_color.rgb, v_color.a * a);
}
