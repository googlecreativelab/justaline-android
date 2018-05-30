precision highp float;

attribute vec3 position;
attribute vec3 previous;
attribute vec3 next;
attribute float side;
attribute float width;
attribute float counters;
attribute float length;
attribute float endCaps;

uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform vec2 resolution;
uniform float lineDepthScale;
uniform vec3 color;
uniform float near;
uniform float far;

varying float v_Depth;
varying vec4 v_Color;
varying vec2 v_TexCoord;
varying vec2 v_TexCoordEndCap;
varying vec2 v_TexCoordStartCap;

vec2 fix( vec4 i, float aspect ) {
    vec2 res = i.xy / i.w;
    res.x *= aspect;
    return res;
}

float map(float value, float inMin, float inMax, float outMin, float outMax) {
  return ((value - inMin) / (inMax - inMin) * (outMax - outMin) + outMin);
}

void main() {
    float aspect = resolution.x / resolution.y;

    mat4 m = projectionMatrix * modelViewMatrix;
    vec4 finalPosition = m * vec4( position, 1.0 );
    vec4 prevPos = m * vec4( previous, 1.0 );
    vec4 nextPos = m * vec4( next, 1.0 );

    vec2 currentP = fix( finalPosition, aspect );
    vec2 prevP = fix( prevPos, aspect );
    vec2 nextP = fix( nextPos, aspect );

    float w = width;

    // Scale the line up when its further away
    w *= map(clamp((finalPosition.z-near)/far, 0.0, 1.0), 0.0, 1.0, 1.0, lineDepthScale);

    // Calculate the direction of the line segment by comparing prev and next point
    vec2 dir;
    if( nextP == currentP ){
        dir = normalize( currentP - prevP );
    } else if( prevP == currentP ){
        dir = normalize( nextP - currentP );
    } else {
        vec2 dir1 = normalize( currentP - prevP );
        vec2 dir2 = normalize( nextP - currentP );
        dir = normalize( dir1 + dir2 );
    }

    // Calculate the normal of the line segment
    vec2 normal = vec2( -dir.y, dir.x );
    normal.x /= aspect;
    normal *= .5 * w;

    // Calculate the offset of the vertex
    vec4 offset = vec4( normal * side, 0.0, 1.0 );
    finalPosition.xy += offset.xy;

    gl_Position = finalPosition;

    v_TexCoord = vec2( 2.0 * length / w, (side + 1.0)/2.0);
    v_TexCoordEndCap = vec2( 2.0 * (length - endCaps + w/2.0)/ w, (side + 1.0)/2.0);
    v_TexCoordStartCap = vec2( 1.0 - 2.0 * length / w, (side + 1.0)/2.0);

    v_Color = vec4( color, 1.0 );
    v_Depth = gl_Position.z;
}