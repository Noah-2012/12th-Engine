#version 130
varying vec2 vTexCoord;

uniform sampler2D uColorTex;
uniform sampler2D uDepthTex;
uniform mat4  uInvVP;
uniform mat4  uPrevVP;
uniform float uBlurStrength;
uniform int   uSamples;
uniform float uNear;
uniform float uFar;
uniform int   uDebugMode; // 0=normal, 1=velocity, 2=depth, 3=worldpos

void main() {
    vec4 originalColor = texture2D(uColorTex, vTexCoord);

    float depthSample = texture2D(uDepthTex, vTexCoord).r;

    // Sky / background — no blur
    if (depthSample >= 0.9999) {
        gl_FragColor = originalColor;
        return;
    }

    // Reconstruct NDC clip position for this pixel
    float z = depthSample * 2.0 - 1.0;
    vec4 clipPos = vec4(vTexCoord * 2.0 - 1.0, z, 1.0);

    // Unproject to world space using the inverse of the current VP matrix
    vec4 worldPos = uInvVP * clipPos;
    if (abs(worldPos.w) < 0.001) {
        gl_FragColor = originalColor;
        return;
    }
    worldPos /= worldPos.w;

    // Project the world position through the PREVIOUS frame's VP matrix
    vec4 prevClip = uPrevVP * worldPos;
    if (abs(prevClip.w) < 0.001) {
        gl_FragColor = originalColor;
        return;
    }
    prevClip /= prevClip.w;

    // FIX: Velocity direction was backwards.
    //      clipPos.xy is where the pixel IS now (NDC).
    //      prevClip.xy is where it WAS last frame (NDC).
    //      current - previous  =  the direction the camera/object moved,
    //      i.e. the direction we should smear BACK along to fake motion blur.
    //      The old code (prevClip - clipPos) pointed the blur the wrong way,
    //      causing partial self-cancellation when strength was raised.
    //
    //      Multiply by 0.5 converts from NDC [-1,1] range to UV [0,1] range.
    vec2 rawVelocity = (clipPos.xy - prevClip.xy) * 0.5;

    float rawLen = length(rawVelocity);
    if (rawLen < 0.00001) {
        gl_FragColor = originalColor;
        return;
    }

    // FIX: Raw velocity clamp raised from 0.02 (2%) to 0.15 (15% of screen).
    //      The old 2% cap was too aggressive — fast camera rotation legitimately
    //      produces velocities well above that, so the blur was being truncated
    //      before uBlurStrength was even applied.  15% matches what most games
    //      use as a per-frame motion budget before temporal accumulation.
    const float MAX_RAW_VEL = 0.15;
    if (rawLen > MAX_RAW_VEL) {
        rawVelocity = normalize(rawVelocity) * MAX_RAW_VEL;
    }

    // Apply user-controlled blur strength AFTER clamping reprojection accuracy
    vec2 velocity = rawVelocity * uBlurStrength;

    float velLen = length(velocity);
    if (velLen < 0.00001) {
        gl_FragColor = originalColor;
        return;
    }

    // Clamp total blur so no smear exceeds 25% of screen regardless of strength.
    // This prevents the "fragment explosion" artifact at high strength values.
    const float MAX_BLUR = 0.25;
    if (velLen > MAX_BLUR) {
        velocity = normalize(velocity) * MAX_BLUR;
    }

    // Distribute samples evenly across the full velocity vector,
    // centred on the current pixel (bidirectional blur).
    // Bidirectional is more physically correct and avoids the one-sided
    // "comet tail" look of a purely forward accumulation.
    vec2 stepVec = velocity / float(uSamples);

    vec4  color = originalColor;
    int   count = 1;

    // Forward direction (current → where it's going)
    vec2 uvFwd = vTexCoord;
    for (int i = 1; i < uSamples / 2; i++) {
        uvFwd += stepVec;
        if (uvFwd.x < 0.0 || uvFwd.x > 1.0 || uvFwd.y < 0.0 || uvFwd.y > 1.0) continue;
        color += texture2D(uColorTex, uvFwd);
        count++;
    }

    // Backward direction (current → where it came from)
    vec2 uvBwd = vTexCoord;
    for (int i = 1; i < uSamples - uSamples / 2; i++) {
        uvBwd -= stepVec;
        if (uvBwd.x < 0.0 || uvBwd.x > 1.0 || uvBwd.y < 0.0 || uvBwd.y > 1.0) continue;
        color += texture2D(uColorTex, uvBwd);
        count++;
    }

    // Debug visualisation modes
    if (uDebugMode == 1) {
        // Velocity as colour: R=X G=Y B=magnitude
        gl_FragColor = vec4(abs(velocity.x) * 10.0, abs(velocity.y) * 10.0, velLen * 10.0, 1.0);
        return;
    }
    if (uDebugMode == 2) {
        gl_FragColor = vec4(vec3(depthSample), 1.0);
        return;
    }
    if (uDebugMode == 3) {
        gl_FragColor = vec4(worldPos.xyz * 0.1 + 0.5, 1.0);
        return;
    }

    gl_FragColor = color / float(count);
}
