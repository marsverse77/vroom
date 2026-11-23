package com.vroombuddies.game.render

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialBuilder.*
import com.vroombuddies.game.render.fx.FxShaders
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * All shaders of the game, compiled on the device with Filament's material compiler (filamat)
 * the first time the game runs, then cached in app storage. This avoids shipping prebuilt binaries
 * and keeps every material readable here in one file.
 */
class MaterialLib(private val ctx: Context) {
    lateinit var lit: Material        // vertex-coloured PBR (scenery, car trims), with wind sway
    lateinit var paint: Material      // car paint: clear coat
    lateinit var textured: Material   // road, walls, ground, ramps
    lateinit var glow: Material       // unlit bright colour (lights, neon, flames)
    lateinit var glowTex: Material    // unlit scrolling texture (boost pads)
    lateinit var particleAdd: Material
    lateinit var particleAlpha: Material
    // optional effects materials (render/fx): null if this phone's driver can't build them, and the effect is skipped
    var water: Material? = null
    var postFx: Material? = null

    private class Spec(val name: String, val configure: (MaterialBuilder) -> MaterialBuilder, val source: String, val optional: Boolean = false)

    // skinning, VSM and stereo variants are never used: skip them to make compiling 3x faster
    private val variantFilter = 8 or 32 or 128

    private val specs = listOf(
        Spec("lit", { b ->
            b.shading(Shading.LIT).require(VertexAttribute.COLOR)
                .parameter("baseColor", UniformType.FLOAT4).parameter("roughness", UniformType.FLOAT)
                .parameter("metallic", UniformType.FLOAT).parameter("emissive", UniformType.FLOAT4)
                .parameter("wind", UniformType.FLOAT)
                .specularAntiAliasing(true)
                .materialVertex("""
                    void materialVertex(inout MaterialVertexInputs material) {
                        float w = material.color.a * materialParams.wind;
                        if (w > 0.0) {
                            float t = getUserTime().x;
                            vec3 p = getPosition().xyz;
                            float ph = p.x * 0.13 + p.z * 0.09 + getWorldFromModelMatrix()[3].x * 0.05;
                            material.worldPosition.x += sin(t * 1.9 + ph) * w;
                            material.worldPosition.z += cos(t * 1.4 + ph * 1.3) * w * 0.6;
                        }
                    }
                """.trimIndent())
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = vec4(getColor().rgb, 1.0) * materialParams.baseColor;
                material.roughness = materialParams.roughness;
                material.metallic = materialParams.metallic;
                material.emissive = materialParams.emissive;
            }
        """),
        Spec("paint", { b ->
            b.shading(Shading.LIT).require(VertexAttribute.COLOR)
                .parameter("baseColor", UniformType.FLOAT4).parameter("roughness", UniformType.FLOAT)
                .parameter("metallic", UniformType.FLOAT).parameter("rainbow", UniformType.FLOAT)
                .parameter("glow", UniformType.FLOAT)
                .specularAntiAliasing(true)
        }, """
            vec3 hue(float h) { return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0); }
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec3 base = materialParams.baseColor.rgb;
                if (materialParams.rainbow > 0.5) {
                    vec3 wp = getWorldPosition();
                    base = hue(wp.x * 0.15 + wp.y * 0.2 + wp.z * 0.15 + getUserTime().x * 0.3);
                    base = base * base;
                }
                material.baseColor = vec4(base * getColor().rgb, 1.0);
                material.roughness = materialParams.roughness;
                material.metallic = materialParams.metallic;
                material.clearCoat = 1.0;
                material.clearCoatRoughness = 0.05;
                material.emissive = vec4(base * materialParams.glow, 0.0);
            }
        """),
        Spec("textured", { b ->
            b.shading(Shading.LIT).require(VertexAttribute.COLOR).require(VertexAttribute.UV0)
                .parameter("albedo", SamplerType.SAMPLER_2D)
                .parameter("uvScale", UniformType.FLOAT2).parameter("tint", UniformType.FLOAT4)
                .parameter("roughness", UniformType.FLOAT).parameter("glow", UniformType.FLOAT)
                .specularAntiAliasing(true)
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec4 t = texture(materialParams_albedo, getUV0() * materialParams.uvScale);
                material.baseColor = vec4(t.rgb * getColor().rgb, 1.0) * materialParams.tint;
                material.roughness = materialParams.roughness * (1.15 - 0.3 * t.a);
                material.emissive = vec4(material.baseColor.rgb * materialParams.glow * t.a, 0.0);
            }
        """),
        Spec("glow", { b ->
            b.shading(Shading.UNLIT).require(VertexAttribute.COLOR).parameter("color", UniformType.FLOAT4)
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = vec4(getColor().rgb * materialParams.color.rgb, 1.0);
            }
        """),
        Spec("glowTex", { b ->
            b.shading(Shading.UNLIT).require(VertexAttribute.UV0).blending(BlendingMode.ADD).depthWrite(false)
                .parameter("tex", SamplerType.SAMPLER_2D).parameter("color", UniformType.FLOAT4).parameter("scroll", UniformType.FLOAT)
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 uv = getUV0();
                uv.y -= getUserTime().x * materialParams.scroll;
                vec4 t = texture(materialParams_tex, uv);
                float pulse = 0.8 + 0.2 * sin(getUserTime().x * 8.0);
                material.baseColor = vec4(materialParams.color.rgb * t.rgb * t.a * pulse, 0.0);
            }
        """),
        Spec("particleAdd", { b ->
            b.shading(Shading.UNLIT).require(VertexAttribute.COLOR).require(VertexAttribute.UV0)
                .blending(BlendingMode.ADD).depthWrite(false).culling(CullingMode.NONE)
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 d = getUV0() * 2.0 - 1.0;
                float soft = clamp(1.0 - dot(d, d), 0.0, 1.0);
                vec4 c = getColor();
                material.baseColor = vec4(c.rgb * c.a * soft * soft, 0.0);
            }
        """),
        Spec("particleAlpha", { b ->
            b.shading(Shading.UNLIT).require(VertexAttribute.COLOR).require(VertexAttribute.UV0)
                .blending(BlendingMode.TRANSPARENT).depthWrite(false).culling(CullingMode.NONE)
        }, """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec2 d = getUV0() * 2.0 - 1.0;
                float soft = clamp(1.0 - dot(d, d), 0.0, 1.0);
                vec4 c = getColor();
                float a = c.a * soft;
                material.baseColor = vec4(c.rgb * a, a);
            }
        """),
    ) + FxShaders.specs.map { Spec(it.name, it.configure, it.source, optional = true) }

    /** Slow (first launch only): compile or load every material package. Safe on a background thread. */
    fun preparePackages(progress: (Float) -> Unit): Map<String, ByteBuffer> {
        val dir = File(ctx.filesDir, "materials").apply { mkdirs() }
        val out = HashMap<String, ByteBuffer>()
        var initDone = false
        specs.forEachIndexed { i, spec ->
            val key = (spec.name + spec.source + VERSION).hashCode().toUInt().toString(16)
            val f = File(dir, "${spec.name}-$key.filamat")
            val bytes = if (f.exists()) f.readBytes() else try {
                if (!initDone) { MaterialBuilder.init(); initDone = true }
                val b = MaterialBuilder()
                    .name(spec.name)
                    .platform(Platform.MOBILE)
                    .targetApi(TargetApi.OPENGL)
                    .optimization(Optimization.PERFORMANCE)
                    .variantFilter(variantFilter)
                    .material(spec.source.trimIndent())
                val pkg = spec.configure(b).build()
                check(pkg.isValid) { "material ${spec.name} failed to compile" }
                val buf = pkg.buffer
                val arr = ByteArray(buf.remaining()); buf.get(arr)
                f.writeBytes(arr)
                arr
            } catch (t: Throwable) {
                if (!spec.optional) throw t
                android.util.Log.w("Vroom", "optional material ${spec.name} skipped", t)
                null
            }
            if (bytes != null) out[spec.name] = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
            progress((i + 1f) / specs.size)
        }
        if (initDone) MaterialBuilder.shutdown()
        return out
    }

    /** Fast: create GPU materials from prepared packages. Must run on the render thread. */
    fun create(engine: Engine, pkgs: Map<String, ByteBuffer>) {
        fun mk(n: String): Material { val b = pkgs.getValue(n); return Material.Builder().payload(b, b.remaining()).build(engine) }
        lit = mk("lit"); paint = mk("paint"); textured = mk("textured"); glow = mk("glow"); glowTex = mk("glowTex")
        particleAdd = mk("particleAdd"); particleAlpha = mk("particleAlpha")
        fun opt(n: String): Material? = if (pkgs.containsKey(n)) try { mk(n) } catch (t: Throwable) { null } else null
        water = opt("water"); postFx = opt("postFx")
    }

    companion object { const val VERSION = "filament-1.81.0-v1" }
}
