using System.IO;
using UnityEditor;
using UnityEngine;

public static class BuildStandalone
{
    [MenuItem("Health/Build macOS Showcase")]
    public static void BuildMac()
    {
        Directory.CreateDirectory("Assets/Resources");
        var shader = Shader.Find("Health/HologramSciFi");
        if (shader != null && AssetDatabase.LoadAssetAtPath<Material>("Assets/Resources/HologramRuntimeMaterial.mat") == null)
        {
            var material = new Material(shader);
            AssetDatabase.CreateAsset(material, "Assets/Resources/HologramRuntimeMaterial.mat");
        }
        var particleShader = Shader.Find("Legacy Shaders/Particles/Additive") ?? Shader.Find("Unlit/Color");
        if (particleShader != null && AssetDatabase.LoadAssetAtPath<Material>("Assets/Resources/ParticleRuntimeMaterial.mat") == null)
        {
            var material = new Material(particleShader);
            AssetDatabase.CreateAsset(material, "Assets/Resources/ParticleRuntimeMaterial.mat");
        }
        AssetDatabase.SaveAssets();
        Directory.CreateDirectory("Builds");
        var scenes = new[]
        {
            "Assets/Scenes/01_WireframeMan.unity",
            "Assets/Scenes/02_HumanBodyWireframe.unity",
            "Assets/Scenes/03_HologramingMan.unity",
        };
        BuildPipeline.BuildPlayer(scenes, "Builds/unity展示专用.app", BuildTarget.StandaloneOSX, BuildOptions.None);
        Debug.Log("Built Builds/unity展示专用.app");
    }

}
