#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;    // Основна текстура (src FBO)
uniform sampler2D u_mask;       // Текстура маски (може бути атласна сторінка)

// UV-межі маски в її текстурі: xy = (u, v) верхній кут, zw = (u2, v2) нижній.
// Для standalone Texture: (0,0,1,1) → поведінка ідентична старій.
// Для TextureRegion з атласу: реальні UV регіону → семплимо ТІЛЬКИ його ділянку.
uniform vec4 u_maskUv;

void main() {
    // v_texCoords (0..1 по FBO) → ремап у UV-простір регіону маски.
    // 1.0 - y зберігає стару Y-інверсію (FBO перевернутий відносно текстур).
    vec2 maskUV = mix(u_maskUv.xy, u_maskUv.zw, vec2(v_texCoords.x, 1.0 - v_texCoords.y));

    vec4 maskColor = texture2D(u_mask, maskUV);
    vec4 texColor  = texture2D(u_texture, v_texCoords);

    // Приглушуємо і колір, і альфу на прозорих ділянках маски (premultiplied-friendly)
    texColor.rgb *= maskColor.a;
    texColor.a   *= maskColor.a;

    gl_FragColor = texColor * v_color;
}
