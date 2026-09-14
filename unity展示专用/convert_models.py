import bpy
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCES = [
    (ROOT / "Assets/Models/wireframe_man.glb", ROOT / "Assets/Models/WireframeMan.fbx"),
    (ROOT / "Assets/Models/3d_human_body_wireframe_model.glb", ROOT / "Assets/Models/HumanBodyWireframe.fbx"),
    (ROOT / "Assets/Models/holograming_man.glb", ROOT / "Assets/Models/HologramingMan.fbx"),
]


for source, target in SOURCES:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=str(source))

    meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
    if not meshes:
        raise RuntimeError(f"No mesh found in {source.name}")

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.export_scene.fbx(
        filepath=str(target),
        use_selection=True,
        apply_unit_scale=True,
        apply_scale_options="FBX_SCALE_ALL",
        axis_forward="-Z",
        axis_up="Y",
        add_leaf_bones=False,
        bake_anim=False,
        path_mode="AUTO",
    )
    print(f"Converted {source.name} -> {target.name}")

