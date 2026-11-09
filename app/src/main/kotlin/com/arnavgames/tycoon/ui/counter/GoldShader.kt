package com.arnavgames.tycoon.ui.counter

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The counter's background: slow flowing gold light (layered sine fields), a glow that warms with the combo, and a
 * ripple ring from each of the last four touches. AGSL, so it runs on the GPU (Android 13+); older phones draw a
 * gradient fallback instead.
 */
object GoldShader {
    private const val SRC = """
        uniform float2 res;
        uniform float time;
        uniform float heat;
        uniform float frenzy;
        uniform half4 gold;
        uniform float4 t0;
        uniform float4 t1;
        uniform float4 t2;
        uniform float4 t3;

        float ring(float2 p, float4 t) {
            if (t.w <= 0.0) return 0.0;
            float r = t.z * 1100.0;
            float d = distance(p, t.xy);
            float k = exp(-pow((d - r) / 46.0, 2.0));
            return k * t.w * (1.0 - clamp(t.z / 0.85, 0.0, 1.0));
        }

        half4 main(float2 p) {
            float2 uv = p / res;
            float f = sin(uv.x * 5.3 + time * 0.55) + sin(uv.y * 7.1 - time * 0.42) + sin((uv.x + uv.y) * 4.2 + time * 0.31)
                + sin(distance(uv, float2(0.3, 0.7)) * 9.0 - time * 0.8) * 0.6;
            f = f * 0.14 + 0.5;
            float glow = smoothstep(0.85, 0.0, distance(uv, float2(0.5, 0.58)));
            float a = (0.07 + 0.16 * heat + 0.18 * frenzy) * glow * (0.55 + 0.45 * f);
            a += 0.05 * f * (0.4 + heat);
            a += 0.55 * (ring(p, t0) + ring(p, t1) + ring(p, t2) + ring(p, t3));
            a = clamp(a, 0.0, 0.9);
            return half4(gold.rgb * a, a);
        }
    """

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    @RequiresApi(33)
    fun create(): RuntimeShader? = runCatching { RuntimeShader(SRC) }.getOrNull()
}
