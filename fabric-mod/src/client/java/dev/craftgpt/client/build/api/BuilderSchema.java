package dev.craftgpt.client.build.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class BuilderSchema {
    private static final String SCHEMA = """
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "schemaVersion": {"type": "integer", "enum": [1]},
            "summary": {"type": "string"},
            "palette": {
              "type": "array",
              "items": {"type": "string"}
            },
            "operations": {
              "type": "array",
              "items": {"type": "string"}
            },
            "components": {
              "type": "array", "maxItems": 128,
              "items": {
                "type": "object", "additionalProperties": false,
                "properties": {
                  "id": {"type": "string"},
                  "kind": {"type": "string", "enum": ["fill", "shell", "gable", "door"]},
                  "from": {"type": "array", "items": {"type": "integer"}, "minItems": 3, "maxItems": 3},
                  "to": {"type": "array", "items": {"type": "integer"}, "minItems": 3, "maxItems": 3},
                  "paletteIndex": {"type": "integer"},
                  "axis": {"type": "string", "enum": ["x", "z"]},
                  "facing": {"type": "string", "enum": ["north", "south", "east", "west"]}
                },
                "required": ["id", "kind", "from", "to", "paletteIndex", "axis", "facing"]
              }
            }
          },
          "required": ["schemaVersion", "summary", "palette", "operations", "components"]
        }
        """;

    private BuilderSchema() {
    }

    public static JsonObject json() {
        return JsonParser.parseString(SCHEMA).getAsJsonObject();
    }
}
