package dev.craftgpt.client.planning.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class PlannerSchema {
    private static final String SCHEMA = """
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "title": {"type": "string"},
            "summary": {"type": "string"},
            "goals": {"type": "array", "items": {"type": "string"}},
            "style": {"type": "string"},
            "targetDimensions": {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "width": {"type": "integer"},
                "height": {"type": "integer"},
                "depth": {"type": "integer"}
              },
              "required": ["width", "height", "depth"]
            },
            "orientation": {"type": "string"},
            "materialRoles": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "properties": {
                  "role": {"type": "string"},
                  "blockCandidates": {"type": "array", "items": {"type": "string"}},
                  "purpose": {"type": "string"}
                },
                "required": ["role", "blockCandidates", "purpose"]
              }
            },
            "requiredFeatures": {"type": "array", "items": {"type": "string"}},
            "preferredFeatures": {"type": "array", "items": {"type": "string"}},
            "constraints": {"type": "array", "items": {"type": "string"}},
            "avoid": {"type": "array", "items": {"type": "string"}},
            "assumptions": {"type": "array", "items": {"type": "string"}},
            "estimatedBlockChanges": {"type": "integer"},
            "designRationale": {"type": "string"},
            "implementationBrief": {"type": "string"}
          },
          "required": [
            "title", "summary", "goals", "style", "targetDimensions", "orientation",
            "materialRoles", "requiredFeatures", "preferredFeatures", "constraints", "avoid",
            "assumptions", "estimatedBlockChanges", "designRationale", "implementationBrief"
          ]
        }
        """;

    private PlannerSchema() {
    }

    public static JsonObject json() {
        return JsonParser.parseString(SCHEMA).getAsJsonObject();
    }
}
