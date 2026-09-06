package dev.craftgpt.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.util.TreeMap;

public final class AreaContextJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private AreaContextJson() {
    }

    public static String encode(AreaContext context) {
        return GSON.toJson(context);
    }

    public static AreaContext decode(String json) {
        return GSON.fromJson(json, AreaContext.class);
    }

    public static String canonicalEncode(AreaContext context) {
        JsonObject root = GSON.toJsonTree(context).getAsJsonObject();
        root.remove("selectionId");
        // Exact coordinates are a prompt representation. worldStateHash already fingerprints every state,
        // so excluding this field preserves hashes for projects created before detailed context existed.
        root.remove("exactBlocks");
        JsonObject materials = new JsonObject();
        new TreeMap<>(context.materials()).forEach(materials::addProperty);
        root.add("materials", materials);
        return GSON.toJson(root);
    }
}
