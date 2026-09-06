# Phase 8 — CraftBook UX

Phase 8 makes CraftGPT usable without memorizing commands while keeping the command interface intact for power users and automation.

## Getting the CraftBook

- Creative mode: **Tools & Utilities** tab.
- Command: `/craftgpt book`.
- Crafting: a book in the center, redstone on both sides and amethyst above and below.

The item is rare, stack-limited to one and rendered with an enchantment glint. Right-clicking it opens the visual dashboard.

## Guided workflow

The main button always describes and performs the safest next action:

1. Start the area at the player's current block.
2. Move to the opposite corner and finish the area.
3. Describe or iterate the versioned build intention.
4. Generate and inspect a non-mutating ghost preview.
5. Accept and place explicitly.
6. Use history for undo, redo, archive, import and visualization.

Secondary buttons provide direct access to plan versions, preview, recovery history and settings. The help page explains the workflow, local/API boundary and corresponding text commands.

## Model selection

The settings UI now distinguishes role from exact API ID:

- **Quality · Sol** — `gpt-5.6-sol`, intended for the most complex planning work.
- **Balanced · Terra** — `gpt-5.6-terra`, the default planner balance.
- **Budget · Luna** — `gpt-5.6-luna`, the default lower-cost builder.
- **Custom model** — preserves arbitrary Responses-compatible model IDs.

The in-game **Models** link opens the official catalog: <https://developers.openai.com/api/docs/models>. Existing saved model IDs are not migrated automatically; unknown IDs appear as Custom.

Reasoning choices are independent for planning and building: `none`, `low`, `medium`, `high`, `xhigh`, and `max`.

## Safety and networking

CraftBook buttons do not send arbitrary command strings. The client packet accepts only seven fixed actions, and the server maps each accepted action to an existing `/craftgpt` command. All established validation, permission and recovery behavior therefore stays in one command path.

## Sound language

- page turn: open/close or navigate;
- amethyst chime: area point accepted;
- enchantment-table sound: AI or preview work started;
- experience sound: successful validation or operation;
- level-up sound: plan, area or placement completed;
- villager rejection: invalid configuration, rejected placement or interrupted outcome.

## Manual checks

1. Obtain the item by command, recipe and creative tab.
2. Right-click and complete the entire guided flow without typing commands.
3. Reopen the book at each state and verify the main action changes correctly.
4. Cycle all model presets, verify exact IDs and confirm Custom accepts an existing non-preset ID.
5. Open the official model link through Minecraft's confirmation screen.
6. Confirm sounds remain audible but do not duplicate excessively.
7. Verify the original `/craftgpt` commands still produce the same behavior.
