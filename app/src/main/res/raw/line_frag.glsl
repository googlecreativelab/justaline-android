#extension GL_OES_standard_derivatives : enable
precision highp float;

//uniform sampler2D u_Texture;
uniform sampler2D u_EndCapTexture;

uniform float drawingDist;

varying float v_Depth;
varying float v_EndCapPosition;
varying vec4 v_Color;
varying vec2 v_TexCoord;
varying vec2 v_TexCoordEndCap;
varying vec2 v_TexCoordStartCap;

float map(float value, float inMin, float inMax, float outMin, float outMax) {
  return ((value - inMin) / (inMax - inMin) * (outMax - outMin) + outMin);
}

void main() {
    vec4 endCapTexture = texture2D(u_EndCapTexture, v_TexCoordEndCap);
    vec4 startCapTexture = texture2D(u_EndCapTexture, v_TexCoordStartCap);

    if(v_TexCoordEndCap.x > 1.0){
        discard;
    }

    gl_FragColor = v_Color;
    gl_FragColor.a *= min(startCapTexture.a, endCapTexture.a);


//    // Calculate the distance
//    vec4 control = texture2D(u_Texture, v_TexCoord);
//    float distDiff = abs(drawingDist - v_Depth);
//
//    if(distDiff > 0.0){
//        float c = clamp(0.002/distDiff, 0.0, 1.0);
//        gl_FragColor = max(gl_FragColor, mix(vec4(0), control, c));
//    }

}