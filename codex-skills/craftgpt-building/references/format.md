# CraftGPT portable format

The result must be one JSON object with this shape:

```json
{
  "schemaVersion": 1,
  "requestId": "copy exactly from request",
  "contextHash": "copy exactly from request",
  "instructionHash": "copy exactly from request",
  "plan": {
    "title": "short project title",
    "summary": "complete design summary",
    "goals": ["goal"],
    "style": "architectural style",
    "targetDimensions": {"width": 1, "height": 1, "depth": 1},
    "orientation": "front direction and placement within the area",
    "materialRoles": [
      {
        "role": "foundation",
        "blockCandidates": ["minecraft:stone_bricks"],
        "purpose": "structural base"
      }
    ],
    "requiredFeatures": ["feature"],
    "preferredFeatures": ["feature"],
    "constraints": ["constraint"],
    "avoid": ["thing to avoid"],
    "assumptions": ["assumption"],
    "estimatedBlockChanges": 1,
    "designRationale": "why the design fits",
    "implementationBrief": "how the block layout is organized"
  },
  "build": {
    "schemaVersion": 1,
    "summary": "what was compiled",
    "palette": ["minecraft:stone_bricks"],
    "operations": ["0,0,0,0"]
  }
}
```

Each operation is `x,y,z,paletteIndex`. Values are non-negative decimal integers without spaces or leading zeroes. Palette indices are zero-based. Every coordinate may occur at most once.

Plan lists may contain at most 48 non-empty strings. Dimensions must fit inside the request area. `estimatedBlockChanges` and the operations array must not exceed `maximumOperations`.

The result metadata is a binding contract. Never regenerate or alter `requestId`, `contextHash`, or `instructionHash`.
