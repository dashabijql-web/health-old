using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

public sealed class HologramShowcase : MonoBehaviour
{
    public int initialIndex;
    [Serializable]
    private sealed class ModelStyle
    {
        public string title;
        public string subtitle;
        public string resourceName;
        public Color primary;
        public Color accent;
        public float particleRate;
        public float rotationSpeed;
    }

    private readonly ModelStyle[] styles =
    {
        new ModelStyle { title = "WIREFRAME MAN", subtitle = "CYAN SCAN // SIGNAL BODY", resourceName = "WireframeMan", primary = new Color(0.03f, 0.82f, 1f), accent = new Color(0.12f, 0.36f, 1f), particleRate = 42f, rotationSpeed = 24f },
        new ModelStyle { title = "HUMAN BODY WIREFRAME", subtitle = "VIOLET PULSE // ANATOMY GRID", resourceName = "HumanBodyWireframe", primary = new Color(0.76f, 0.16f, 1f), accent = new Color(1f, 0.15f, 0.56f), particleRate = 58f, rotationSpeed = 16f },
        new ModelStyle { title = "HOLOGRAMING MAN", subtitle = "DATA RAIN // GHOST PROTOCOL", resourceName = "HologramingMan", primary = new Color(0.28f, 1f, 0.82f), accent = new Color(0.1f, 0.68f, 0.95f), particleRate = 76f, rotationSpeed = 31f },
    };

    private Transform modelRoot;
    private float yaw;
    private float pitch;
    private float distance = 6.2f;
    private bool dragging;
    private Vector3 lastMouse;
    private int activeIndex;
    private Text titleText;
    private Text subtitleText;
    private Text sceneText;
    private Camera cam;
    private HologramRuntime runtime;

    private void Start()
    {
        Application.targetFrameRate = 60;
        BuildEnvironment();
        BuildHud();
        ShowModel(initialIndex);
    }

    private void Update()
    {
        if (Input.GetMouseButtonDown(0)) { dragging = true; lastMouse = Input.mousePosition; }
        if (Input.GetMouseButtonUp(0)) dragging = false;
        if (dragging)
        {
            Vector3 delta = Input.mousePosition - lastMouse;
            yaw += delta.x * 0.25f;
            pitch = Mathf.Clamp(pitch - delta.y * 0.18f, -22f, 22f);
            lastMouse = Input.mousePosition;
        }
        distance = Mathf.Clamp(distance - Input.mouseScrollDelta.y * 0.45f, 3.8f, 9.5f);
        if (!dragging) yaw += styles[activeIndex].rotationSpeed * Time.deltaTime;
        if (modelRoot != null)
        {
            modelRoot.rotation = Quaternion.Euler(pitch, yaw, 0f);
            cam.transform.position = new Vector3(0f, 1.5f, -distance);
            cam.transform.LookAt(new Vector3(0f, 1.55f, 0f));
        }
    }

    private void BuildEnvironment()
    {
        cam = new GameObject("Showcase Camera").AddComponent<Camera>();
        cam.clearFlags = CameraClearFlags.SolidColor;
        cam.backgroundColor = new Color(0.004f, 0.008f, 0.025f);
        cam.fieldOfView = 42f;
        cam.transform.position = new Vector3(0f, 1.5f, -distance);
        cam.transform.LookAt(new Vector3(0f, 1.55f, 0f));

        RenderSettings.ambientLight = new Color(0.01f, 0.02f, 0.06f);
        RenderSettings.fog = true;
        RenderSettings.fogColor = new Color(0.004f, 0.008f, 0.025f);
        RenderSettings.fogDensity = 0.012f;
        var key = new GameObject("Key Light").AddComponent<Light>();
        key.type = LightType.Point; key.color = styles[0].primary; key.intensity = 2.2f; key.range = 12f; key.transform.position = new Vector3(0f, 3.6f, -1f);
        var rim = new GameObject("Rim Light").AddComponent<Light>();
        rim.type = LightType.Point; rim.color = new Color(0.2f, 0.3f, 1f); rim.intensity = 2.8f; rim.range = 10f; rim.transform.position = new Vector3(0f, 1f, 3f);

        var floor = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
        floor.name = "Projection Platform";
        floor.transform.SetPositionAndRotation(new Vector3(0f, -0.02f, 0f), Quaternion.identity);
        floor.transform.localScale = new Vector3(2.65f, 0.05f, 2.65f);
        floor.GetComponent<Renderer>().material = CreatePlatformMaterial(new Color(0.018f, 0.035f, 0.075f), new Color(0.015f, 0.10f, 0.22f), 0.35f);
        for (int i = 0; i < 3; i++)
        {
            var ring = CreateRing("Projection Ring " + i, 0.88f - i * 0.21f, 0.018f);
            ring.name = "Projection Ring " + i;
            ring.transform.SetPositionAndRotation(new Vector3(0f, 0.055f + i * 0.022f, 0f), Quaternion.identity);
            ring.GetComponent<Renderer>().material = CreatePlatformMaterial(new Color(0.03f, 0.18f, 0.28f), new Color(0.02f, 0.32f, 0.48f), 0.7f);
        }
    }

    private GameObject CreateRing(string name, float radius, float tubeRadius)
    {
        const int segments = 64;
        const int sides = 6;
        var go = new GameObject(name);
        var mesh = new Mesh { name = name + " Mesh" };
        var vertices = new Vector3[segments * sides];
        var triangles = new int[segments * sides * 6];
        for (int u = 0; u < segments; u++)
        {
            float angle = u * Mathf.PI * 2f / segments;
            for (int v = 0; v < sides; v++)
            {
                float tube = v * Mathf.PI * 2f / sides;
                float radial = radius + tubeRadius * Mathf.Cos(tube);
                vertices[u * sides + v] = new Vector3(radial * Mathf.Cos(angle), tubeRadius * Mathf.Sin(tube), radial * Mathf.Sin(angle));
                int nextU = (u + 1) % segments;
                int nextV = (v + 1) % sides;
                int t = (u * sides + v) * 6;
                triangles[t] = u * sides + v;
                triangles[t + 1] = nextU * sides + v;
                triangles[t + 2] = u * sides + nextV;
                triangles[t + 3] = u * sides + nextV;
                triangles[t + 4] = nextU * sides + v;
                triangles[t + 5] = nextU * sides + nextV;
            }
        }
        mesh.vertices = vertices;
        mesh.triangles = triangles;
        mesh.RecalculateNormals();
        go.AddComponent<MeshFilter>().sharedMesh = mesh;
        go.AddComponent<MeshRenderer>();
        return go;
    }

    private void ShowModel(int index)
    {
        activeIndex = (index + styles.Length) % styles.Length;
        if (modelRoot != null) Destroy(modelRoot.gameObject);
        var style = styles[activeIndex];
        var prefab = Resources.Load<GameObject>(style.resourceName);
        modelRoot = new GameObject(style.title + " Root").transform;
        if (prefab != null)
        {
            var instance = Instantiate(prefab, modelRoot);
            instance.transform.localPosition = Vector3.zero;
            instance.transform.localRotation = Quaternion.identity;
            instance.transform.localScale = Vector3.one;
            var bounds = GetBounds(instance);
            float scale = 3.0f / Mathf.Max(bounds.size.y, 0.1f);
            instance.transform.localScale = Vector3.one * scale;
            bounds = GetBounds(instance);
            // Align the imported asset's world-space feet with the projection pad.
            instance.transform.position += new Vector3(-bounds.center.x, 0.12f - bounds.min.y, -bounds.center.z);
            foreach (var renderer in instance.GetComponentsInChildren<Renderer>()) renderer.material = CreateMaterial(style.primary, style.accent, 0.42f);
        }
        runtime = modelRoot.gameObject.AddComponent<HologramRuntime>();
        runtime.Configure(style.primary, style.accent, style.particleRate);
        titleText.text = style.title;
        subtitleText.text = style.subtitle;
        sceneText.text = $"SCENE 0{activeIndex + 1}  /  03     DRAG TO ROTATE     WHEEL TO ZOOM";
    }

    private Bounds GetBounds(GameObject root)
    {
        var renderers = root.GetComponentsInChildren<Renderer>();
        if (renderers.Length == 0) return new Bounds(Vector3.zero, Vector3.one);
        var bounds = renderers[0].bounds;
        for (int i = 1; i < renderers.Length; i++) bounds.Encapsulate(renderers[i].bounds);
        return bounds;
    }

    private Material CreateMaterial(Color baseColor, Color emission, float intensity)
    {
        var source = Resources.Load<Material>("HologramRuntimeMaterial");
        var shader = source != null ? source.shader : Shader.Find("Unlit/Color");
        var mat = new Material(shader) { name = "Hologram Material" };
        if (mat.HasProperty("_BaseColor")) mat.SetColor("_BaseColor", new Color(baseColor.r, baseColor.g, baseColor.b, 0.48f));
        if (mat.HasProperty("_EdgeColor")) mat.SetColor("_EdgeColor", emission);
        if (mat.HasProperty("_ScanColor")) mat.SetColor("_ScanColor", Color.white);
        if (mat.HasProperty("_PulseSpeed")) mat.SetFloat("_PulseSpeed", intensity);
        if (mat.HasProperty("_ScanDensity")) mat.SetFloat("_ScanDensity", 14f);
        if (mat.HasProperty("_ScanSpeed")) mat.SetFloat("_ScanSpeed", 0.22f);
        if (mat.HasProperty("_NoiseAmount")) mat.SetFloat("_NoiseAmount", 0.015f);
        return mat;
    }

    private Material CreatePlatformMaterial(Color baseColor, Color emission, float emissionStrength)
    {
        var mat = new Material(Shader.Find("Standard")) { name = "Calm Platform Material" };
        mat.SetColor("_Color", baseColor);
        mat.SetFloat("_Metallic", 0.72f);
        mat.SetFloat("_Glossiness", 0.82f);
        mat.EnableKeyword("_EMISSION");
        mat.SetColor("_EmissionColor", emission * emissionStrength);
        return mat;
    }

    private void BuildHud()
    {
        var canvas = new GameObject("HUD").AddComponent<Canvas>();
        canvas.renderMode = RenderMode.ScreenSpaceOverlay;
        canvas.gameObject.AddComponent<CanvasScaler>();
        canvas.gameObject.AddComponent<GraphicRaycaster>();
        titleText = MakeText(canvas.transform, "WIREFRAME MAN", 32, new Vector2(50, -42), TextAnchor.UpperLeft);
        subtitleText = MakeText(canvas.transform, "CYAN SCAN // SIGNAL BODY", 14, new Vector2(52, -80), TextAnchor.UpperLeft);
        sceneText = MakeText(canvas.transform, "SCENE 01 / 03", 12, new Vector2(50, 38), TextAnchor.LowerLeft);
        MakeButton(canvas.transform, "‹", new Vector2(-70, 44), () => ShowModel(activeIndex - 1));
        MakeButton(canvas.transform, "›", new Vector2(70, 44), () => ShowModel(activeIndex + 1));
    }

    private Text MakeText(Transform parent, string value, int size, Vector2 anchored, TextAnchor anchor)
    {
        var go = new GameObject("Text"); go.transform.SetParent(parent, false);
        var rect = go.AddComponent<RectTransform>(); rect.anchorMin = anchor == TextAnchor.LowerLeft ? new Vector2(0, 0) : new Vector2(0, 1); rect.anchorMax = rect.anchorMin; rect.anchoredPosition = anchored; rect.sizeDelta = new Vector2(800, 80);
        var text = go.AddComponent<Text>(); text.text = value; text.font = Resources.GetBuiltinResource<Font>("Arial.ttf"); text.fontSize = size; text.color = Color.white; text.alignment = anchor; text.horizontalOverflow = HorizontalWrapMode.Overflow; return text;
    }

    private void MakeButton(Transform parent, string label, Vector2 anchored, Action click)
    {
        var go = new GameObject("Nav " + label); go.transform.SetParent(parent, false);
        var rect = go.AddComponent<RectTransform>(); rect.anchorMin = new Vector2(0.5f, 0); rect.anchorMax = rect.anchorMin; rect.anchoredPosition = anchored; rect.sizeDelta = new Vector2(70, 46);
        var image = go.AddComponent<Image>(); image.color = new Color(0.02f, 0.18f, 0.35f, 0.72f);
        var button = go.AddComponent<Button>(); button.onClick.AddListener(() => click());
        var text = MakeText(go.transform, label, 28, Vector2.zero, TextAnchor.MiddleCenter); text.rectTransform.anchorMin = Vector2.zero; text.rectTransform.anchorMax = Vector2.one; text.rectTransform.anchoredPosition = Vector2.zero; text.rectTransform.sizeDelta = Vector2.zero;
    }
}

public sealed class RotateConstantly : MonoBehaviour
{
    public float speed = 30f;
    private void Update() => transform.Rotate(Vector3.up, speed * Time.deltaTime, Space.World);
}

public sealed class HologramRuntime : MonoBehaviour
{
    private Color primary;
    private Color accent;
    private float rate;
    public void Configure(Color primaryColor, Color accentColor, float particleRate) { primary = primaryColor; accent = accentColor; rate = particleRate; BuildParticles(); }
    private void BuildParticles()
    {
        var go = new GameObject("Particle Field"); go.transform.SetParent(transform, false);
        var ps = go.AddComponent<ParticleSystem>();
        var main = ps.main; main.loop = true; main.startLifetime = new ParticleSystem.MinMaxCurve(1.1f, 2.6f); main.startSpeed = new ParticleSystem.MinMaxCurve(0.25f, 0.9f); main.startSize = new ParticleSystem.MinMaxCurve(0.018f, 0.055f); main.startColor = new ParticleSystem.MinMaxGradient(primary, accent); main.maxParticles = 220;
        var emission = ps.emission; emission.rateOverTime = rate;
        var shape = ps.shape; shape.shapeType = ParticleSystemShapeType.Cone; shape.radius = 1.5f; shape.angle = 9f; shape.length = 3.2f; shape.position = new Vector3(0f, 0.05f, 0f);
        var renderer = go.GetComponent<ParticleSystemRenderer>(); renderer.renderMode = ParticleSystemRenderMode.Billboard; renderer.material = MakeParticleMaterial(primary);
    }
    private Material MakeParticleMaterial(Color color)
    {
        var source = Resources.Load<Material>("ParticleRuntimeMaterial");
        var shader = source != null ? source.shader : Shader.Find("Unlit/Color");
        var mat = new Material(shader); if (mat.HasProperty("_Color")) mat.SetColor("_Color", color); return mat;
    }
}
