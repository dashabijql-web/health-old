Shader "Health/HologramSciFi"
{
    Properties
    {
        _BaseColor ("Base Color", Color) = (0.02, 0.65, 1, 0.42)
        _EdgeColor ("Edge Color", Color) = (0.3, 0.95, 1, 1)
        _ScanColor ("Scan Color", Color) = (0.7, 1, 1, 1)
        _ScanDensity ("Scan Density", Float) = 42
        _ScanSpeed ("Scan Speed", Float) = 1.4
        _FresnelPower ("Fresnel Power", Range(0.2, 8)) = 2.4
        _PulseSpeed ("Pulse Speed", Float) = 2
        _NoiseAmount ("Noise Amount", Range(0, 0.5)) = 0.08
        _Opacity ("Opacity", Range(0.02, 1)) = 0.5
    }
    SubShader
    {
        Tags { "Queue"="Transparent" "RenderType"="Transparent" }
        LOD 200
        Cull Off
        ZWrite Off
        Blend SrcAlpha One

        CGPROGRAM
        #pragma surface surf Standard alpha:fade vertex:vert
        #pragma target 3.0

        fixed4 _BaseColor;
        fixed4 _EdgeColor;
        fixed4 _ScanColor;
        float _ScanDensity;
        float _ScanSpeed;
        float _FresnelPower;
        float _PulseSpeed;
        float _NoiseAmount;
        float _Opacity;

        struct Input
        {
            float3 worldPos;
            float3 viewDir;
            float screenNoise;
        };

        void vert(inout appdata_full v, out Input o)
        {
            UNITY_INITIALIZE_OUTPUT(Input, o);
            float seed = dot(v.vertex.xyz, float3(12.9898, 78.233, 37.719));
            o.screenNoise = frac(sin(seed) * 43758.5453);
        }

        void surf(Input IN, inout SurfaceOutputStandard o)
        {
            float fresnel = pow(1.0 - saturate(dot(normalize(IN.viewDir), o.Normal)), _FresnelPower);
            float scanWave = sin((IN.worldPos.y + _Time.y * _ScanSpeed) * _ScanDensity);
            float scan = smoothstep(0.86, 1.0, scanWave);
            float movingBand = smoothstep(0.03, 0.0, abs(frac(IN.worldPos.y * 0.17 - _Time.y * 0.22) - 0.5));
            float pulse = 0.82 + 0.18 * sin(_Time.y * _PulseSpeed + IN.worldPos.y * 2.2);
            float glitch = step(0.975, frac(IN.screenNoise + floor(_Time.y * 18) * 0.173)) * _NoiseAmount;

            fixed3 color = _BaseColor.rgb * pulse;
            fixed3 emission = color * 1.8 + _EdgeColor.rgb * fresnel * 3.2;
            emission += _ScanColor.rgb * (scan * 2.8 + movingBand * 3.5 + glitch * 4.0);
            o.Albedo = color * 0.25;
            o.Metallic = 0.05;
            o.Smoothness = 0.78;
            o.Emission = emission;
            o.Alpha = saturate(_Opacity + fresnel * 0.38 + scan * 0.25 + movingBand * 0.2);
        }
        ENDCG
    }
    FallBack "Transparent/Diffuse"
}
