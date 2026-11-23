package com.vroombuddies.game.render.fx

import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialBuilder.*

/**
 * Shaders for the effects stream. They're compiled with the rest in [com.vroombuddies.game.render.MaterialLib],
 * but marked optional: if one fails on a phone, only that effect is switched off and the game still runs.
 */
object FxShaders {
    class FxSpec(val name: String, val configure: (MaterialBuilder) -> MaterialBuilder, val source: String)

    /**
     * Water: animated ripples (a few travelling waves summed into a normal), deep/shallow colour by view angle,
     * shore foam from vertex alpha. It's glossy, so the sky light (IBL) and SSR give it real reflections.
     * The ripples fade with distance so far water doesn't sparkle or alias.
     * UV0 holds world x/z in metres.
     */
    val water = FxSpec("water", { b ->
        b.shading(Shading.LIT).require(VertexAttribute.COLOR).require(VertexAttribute.UV0)
            .parameter("deep", UniformType.FLOAT4).parameter("shallow", UniformType.FLOAT4)
            .parameter("wave", UniformType.FLOAT)
            .specularAntiAliasing(true)
    }, """
        vec2 waveGrad(vec2 p, vec2 dir, float freq, float speed, float amp, float t) {
            float ph = dot(p, dir) * freq + t * speed;
            return dir * (cos(ph) * freq * amp);
        }
        void material(inout MaterialInputs material) {
            vec2 p = getUV0();
            float t = getUserTime().x;
            float dist = length(getWorldPosition() - getWorldCameraPosition());
            float fade = 1.0 - smoothstep(60.0, 420.0, dist);
            vec2 g = vec2(0.0);
            g += waveGrad(p, vec2(0.80, 0.60), 0.35, 1.3, 0.22, t);
            g += waveGrad(p, vec2(-0.45, 0.89), 0.62, 1.9, 0.12, t);
            g += waveGrad(p, vec2(0.97, -0.24), 1.10, 2.6, 0.06, t);
            g += waveGrad(p, vec2(-0.70, -0.71), 1.90, 3.3, 0.03, t);
            g *= materialParams.wave * fade;
            // tangent frame of a flat plane here: tangent = world z, bitangent = world x, normal = up
            material.normal = normalize(vec3(-g.y, -g.x, 1.0));
            prepareMaterial(material);
            float facing = clamp(dot(shading_view, shading_normal), 0.0, 1.0);
            vec3 col = mix(materialParams.shallow.rgb, materialParams.deep.rgb, 1.0 - facing * 0.7);
            // shore foam: bands that roll in toward the beach
            float shore = getColor().a;
            float bands = 0.5 + 0.5 * sin(shore * 18.0 - t * 2.2 + p.x * 0.05);
            float foam = clamp(shore * (0.55 + 0.45 * bands) * 1.4 - 0.25, 0.0, 1.0);
            material.baseColor = vec4(mix(col, vec3(0.95, 0.98, 1.0), foam), 1.0);
            material.roughness = mix(0.05, 0.6, foam);
            material.metallic = 0.0;
            material.reflectance = 0.6;
        }
    """)

    /**
     * Full-screen pass on the finished race image (see [PostFx]):
     *  - radial motion blur toward the vanishing point, keeping the middle (your car) sharp,
     *  - god rays: bright sky near the sun is smeared toward the sun, so trees, buildings and arches cast light shafts,
     *  - boost: chromatic split at the edges and a warm glow rim.
     * params = (blur, rays, boost, unused), sun = (u, v, visibility, unused), focus = (u, v).
     */
    val postFx = FxSpec("postFx", { b ->
        b.shading(Shading.UNLIT).vertexDomain(VertexDomain.DEVICE).require(VertexAttribute.UV0)
            .depthWrite(false).depthCulling(false).culling(CullingMode.NONE)
            .parameter("scene", SamplerType.SAMPLER_2D)
            .parameter("params", UniformType.FLOAT4).parameter("sun", UniformType.FLOAT4)
            .parameter("focus", UniformType.FLOAT2).parameter("rayColor", UniformType.FLOAT4)
            .parameter("boostColor", UniformType.FLOAT4)
    }, """
        float fxLuma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
        void material(inout MaterialInputs material) {
            prepareMaterial(material);
            vec2 uv = getNormalizedViewportCoord().xy;
            vec4 res = getResolution();
            vec2 asp = vec2(res.x * res.w, 1.0);
            float jit = fract(sin(dot(floor(uv * res.xy), vec2(12.9898, 78.233))) * 43758.5453);
            float blur = materialParams.params.x;
            float rays = materialParams.params.y * materialParams.sun.z;
            float boost = materialParams.params.z;

            vec2 d = uv - materialParams.focus;
            float r = length(d * asp);
            vec3 col;
            if (boost > 0.001) {
                vec2 ca = d * (0.006 * boost * r);
                col = vec3(texture(materialParams_scene, uv + ca).r, texture(materialParams_scene, uv).g, texture(materialParams_scene, uv - ca).b);
            } else {
                col = texture(materialParams_scene, uv).rgb;
            }

            if (blur > 0.001) {
                float m = smoothstep(0.12, 0.75, r) * blur * 0.11;
                vec3 acc = col;
                for (int i = 1; i <= 7; i++) {
                    float t = (float(i) - jit) / 7.0;
                    acc += texture(materialParams_scene, uv - d * (t * m)).rgb;
                }
                col = acc / 8.0;
            }

            if (rays > 0.001) {
                vec2 s = materialParams.sun.xy;
                vec2 stp = (uv - s) / 14.0 * 0.85;
                vec2 p = uv - stp * jit;
                float decay = 1.0;
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 14; i++) {
                    p -= stp;
                    vec2 pc = clamp(p, vec2(0.001), vec2(0.999));
                    vec3 c = texture(materialParams_scene, pc).rgb;
                    float nearSun = 1.0 - smoothstep(0.0, 0.55, length((pc - s) * asp));
                    sum += c * smoothstep(0.72, 0.97, fxLuma(c)) * nearSun * decay;
                    decay *= 0.92;
                }
                float fall = 1.0 - smoothstep(0.1, 1.3, length((uv - s) * asp));
                col += sum * (rays * fall * 0.16) * materialParams.rayColor.rgb;
            }

            if (boost > 0.001) {
                float rim = smoothstep(0.35, 1.0, r);
                col += materialParams.boostColor.rgb * (rim * rim * boost * 0.35);
            }
            material.baseColor = vec4(col, 1.0);
        }
    """)

    val specs = listOf(water, postFx)
}
