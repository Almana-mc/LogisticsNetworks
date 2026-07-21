package me.almana.logisticsnetworks.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonModelLoader {

    public static LayerDefinition loadLayerDefinition(String resourcePath) {
        JsonObject json = readJson(resourcePath);

        int texWidth = json.get("texture_width").getAsInt();
        int texHeight = json.get("texture_height").getAsInt();

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        JsonArray bones = json.getAsJsonArray("bones");
        for (JsonElement element : bones) {
            parseBone(root, element.getAsJsonObject());
        }

        return LayerDefinition.create(mesh, texWidth, texHeight);
    }

    private static JsonObject readJson(String resourcePath) {
        InputStream stream = JsonModelLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new RuntimeException("Model not found: " + resourcePath);
        }
        return JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
    }

    private static void parseBone(PartDefinition parent, JsonObject bone) {
        String name = bone.get("name").getAsString();

        CubeListBuilder cubeList = CubeListBuilder.create();

        if (bone.has("cubes")) {
            for (JsonElement cubeElement : bone.getAsJsonArray("cubes")) {
                JsonObject cube = cubeElement.getAsJsonObject();

                JsonArray uv = cube.getAsJsonArray("uv");
                int u = uv.get(0).getAsInt();
                int v = uv.get(1).getAsInt();

                JsonArray origin = cube.getAsJsonArray("origin");
                float ox = origin.get(0).getAsFloat();
                float oy = origin.get(1).getAsFloat();
                float oz = origin.get(2).getAsFloat();

                JsonArray size = cube.getAsJsonArray("size");
                float sx = size.get(0).getAsFloat();
                float sy = size.get(1).getAsFloat();
                float sz = size.get(2).getAsFloat();

                float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;

                cubeList.texOffs(u, v)
                        .addBox(ox, oy, oz, sx, sy, sz, new CubeDeformation(inflate));
            }
        }

        PartPose pose = parsePose(bone);
        PartDefinition part = parent.addOrReplaceChild(name, cubeList, pose);

        if (bone.has("children")) {
            for (JsonElement child : bone.getAsJsonArray("children")) {
                parseBone(part, child.getAsJsonObject());
            }
        }
    }

    private static PartPose parsePose(JsonObject bone) {
        float px = 0.0F;
        float py = 0.0F;
        float pz = 0.0F;

        if (bone.has("pivot")) {
            JsonArray pivot = bone.getAsJsonArray("pivot");
            px = pivot.get(0).getAsFloat();
            py = pivot.get(1).getAsFloat();
            pz = pivot.get(2).getAsFloat();
        }

        if (bone.has("rotation")) {
            JsonArray rot = bone.getAsJsonArray("rotation");
            float rx = (float) Math.toRadians(rot.get(0).getAsDouble());
            float ry = (float) Math.toRadians(rot.get(1).getAsDouble());
            float rz = (float) Math.toRadians(rot.get(2).getAsDouble());
            return PartPose.offsetAndRotation(px, py, pz, rx, ry, rz);
        }

        return PartPose.offset(px, py, pz);
    }
}
