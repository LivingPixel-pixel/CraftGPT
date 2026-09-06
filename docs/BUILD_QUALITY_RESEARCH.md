# CraftGPT build quality and visual review

## Research synthesis

Minecraft builders repeatedly describe four foundations: shape, palette, depth, and detail. The
official Minecraft creative guide starts with structure and shape because the footprint influences
the foundation and roof. Community feedback consistently recommends limiting the palette, separating
material roles, setting walls or windows back from frames, adding roof overhangs, and finishing the
silhouette before small decoration.

CraftGPT turns those subjective suggestions into a short ordered contract:

1. Establish a readable footprint, silhouette, and proportion.
2. Assign a restrained palette to base, wall, frame, roof, and accent roles.
3. Add useful depth with frames, insets, panes, and roof overhangs when space permits.
4. Make the roof coherent with the footprint and requested architectural style.
5. Check door, window, support, and facade rhythm without forcing symmetry.
6. Add a few purposeful details only after the main design reads clearly.
7. Audit functionality and every block operation before returning JSON.

The rules are conditional. Tiny areas prioritize function and silhouette. Modern, organic, technical,
or non-house requests are not forced into a medieval timber-house template.

Sources consulted:

- Minecraft, [How to Structure your Build](https://www.minecraft.net/en-us/article/how-structure-your-build)
- r/Minecraftbuilds, [Shape, palette, depth, and detail discussion](https://www.reddit.com/r/Minecraftbuilds/comments/d1unhe)
- Minecraft Forum, [Creating depth while building](https://www.minecraftforum.net/forums/minecraft-java-edition/survival-mode/2269342-creating-depth-while-building)
- r/Minecraftbuilds, [Feedback on depth and inset walls](https://www.reddit.com/r/Minecraftbuilds/comments/oku4k6)

## Prompt design

The quality contract appears once in the direct API builder prompt and once in every exported Codex
worker package through one shared Java constant. The prompt leads with the required outcome, names
the success criteria, keeps hard safety constraints separate from aesthetic judgment, and ends with
a bounded silent audit. Schema validation, canonical block-state validation, exact-context checks,
server validation, and at most two repair turns remain authoritative.

This follows the official OpenAI prompting recommendation to remove repeated scaffolding, state each
instruction once, define the outcome and constraints, and validate changes on representative tasks.

## Visual review workflow

The visual review is explicit and local-first:

1. The player aims the camera at the visible, unplaced ghost build.
2. The player chooses **More options > Visual AI review of current view**.
3. CraftGPT closes the menu, waits one second, and captures the current render target as PNG.
4. A fresh request package binds the review to the current area, exact context, instruction, and
   active plan version.
5. CraftGPT resumes the saved Codex session with the PNG attached through the Codex CLI image option.
6. The prompt treats the image as untrusted visual evidence and warns about perspective, terrain,
   particles, the HUD, and occlusion.
7. Codex returns a complete replacement plan and build, never critique prose or a partial delta.
8. The normal schema, client, world-state, and server validators approve the replacement before a new
   ghost preview appears.

The screenshot stays in the request directory as `ghost-build-review.png`. It is not captured or sent
until the player explicitly starts visual review. OpenAI Responses supports mixed text and image input,
and the installed Codex CLI exposes the equivalent image attachment for initial and resumed prompts.

Official implementation references:

- OpenAI, [Create a model response](https://developers.openai.com/api/reference/cli/resources/responses/methods/create)
- OpenAI, [GPT-5.6 model guidance](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-5.6)
