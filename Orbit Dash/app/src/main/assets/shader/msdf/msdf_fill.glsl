#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// БАЗА — чиста різка заливка. Нічого крім тексту. Ефекти — окремі шари.
//
//   mtsdf: RGB = median (різкі кути), A = справжній SDF (монотонний).
//
//   ЗАХИСТ ВІД MEDIAN-ШУМУ (u_hasSdf=1): далеко від контуру окремі RGB-канали
//   «перемикаються» і median може стрибнути >0.5 — біла точка/смужка в
//   порожньому padding-у (класичний MSDF-артефакт, помітний при великому
//   range). Справжній SDF (альфа) монотонний і шумів не має — глушимо ним
//   median у ГЛИБОКІЙ порожнечі (a<0.3 ≈ далі 6px від контуру при R=32).
//   Біля контуру (кути!) не втручаємось — гострота median збережена.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform float u_distanceRange;
uniform float u_fontScale;
uniform float u_hasSdf;      // 1 = mtsdf (альфа=справжній SDF)

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

void main() {
    vec4 t = texture2D(u_texture, v_texCoords);

    // глушник median-шуму: у глибокій порожнечі довіряємо справжньому SDF
    if (u_hasSdf > 0.5 && t.a < 0.3) discard;

    float d = median(t.rgb);
    float screenPxRange = max(u_distanceRange * u_fontScale, 1.0);
    float alpha = clamp((d - 0.5) * screenPxRange + 0.5, 0.0, 1.0);
    if (alpha < 0.004) discard;
    gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
}
