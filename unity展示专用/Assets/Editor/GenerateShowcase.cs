using UnityEditor;
using UnityEngine;
using UnityEditor.SceneManagement;
using System.IO;

public static class GenerateShowcase
{
    [MenuItem("Health/Generate Showcase Scenes")]
    public static void Generate()
    {
        string[] models = { "WireframeMan", "HumanBodyWireframe", "HologramingMan" };
        string[] scenes = { "01_WireframeMan", "02_HumanBodyWireframe", "03_HologramingMan" };
        for (int i = 0; i < models.Length; i++)
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            var root = new GameObject("Hologram Showcase");
            root.AddComponent<HologramShowcase>().initialIndex = i;
            var model = AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Models/" + models[i] + ".fbx");
            if (model != null)
            {
                string prefabPath = "Assets/Resources/" + models[i] + ".prefab";
                Directory.CreateDirectory("Assets/Resources");
                PrefabUtility.SaveAsPrefabAsset(model, prefabPath);
            }
            EditorSceneManager.SaveScene(scene, "Assets/Scenes/" + scenes[i] + ".unity");
        }
        EditorBuildSettings.scenes = new[]
        {
            new EditorBuildSettingsScene("Assets/Scenes/01_WireframeMan.unity", true),
            new EditorBuildSettingsScene("Assets/Scenes/02_HumanBodyWireframe.unity", true),
            new EditorBuildSettingsScene("Assets/Scenes/03_HologramingMan.unity", true),
        };
        AssetDatabase.SaveAssets();
        Debug.Log("Health hologram showcase scenes generated.");
    }
}
