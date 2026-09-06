# Manual Minecraft acceptance checklist

Use a backed-up disposable world. Keep one mod version installed. Record the JAR version, Fabric/API versions, selected model, reasoning setting and effort with each report.

## First run

- [ ] Effort 1: request one dirt block. It becomes a placeable draft with no automatic image review.
- [ ] Stay on the waiting screen. Single-player continues ticking and server validation completes.
- [ ] Repeat while using Continue playing. A later review round does not reopen a menu.
- [ ] Cancel during startup, generation, image rendering and server validation. No later callback revives the cancelled job.
- [ ] Start a new job after each cancellation.

## Materials and functionality

- [ ] Request two rooms: dark oak floor in one, patterned stone and red terracotta in the other. Check that the material assignment is preserved.
- [ ] Request a copper-stair roof. Inspect stair orientation and confirm the material stays copper.
- [ ] Request a usable door. Check matching halves, opening direction and support.
- [ ] Request a sculpture. It is not forced to have a house floor, entrance or roof.
- [ ] Request one targeted inlay change. All blocks outside the displayed edit box remain identical.

## Review behavior

- [ ] Effort 2 produces a review with fresh images after the first validated build.
- [ ] Effort 4 can progress through three reviews, or stop early on keep.
- [ ] A keep response preserves the exact draft.
- [ ] An inspect response produces the requested region and correct orientation.
- [ ] Extra view requests stop at the configured limit and leave the last valid draft available.
- [ ] A rejected patch produces a reason and suggestion; recovery does not remove the valid preview.
- [ ] After a failed improvement, reopen the project and start another iteration.
- [ ] Inspect Review findings and the detailed run log.

## Visual and UI checks

- [ ] Compare solid preview stairs, slabs, doors, panes and logs against actual placed blocks.
- [ ] Inspect all three sheets and verify cutaway labels and orientation.
- [ ] Test GUI scales and small windows. Buttons and messages remain usable.
- [ ] Switch resource packs and regenerate images. Compare textures and note unsupported-model markers.
- [ ] Open and close the image viewer repeatedly. Check for graphics errors or growing memory use.
- [ ] Inspect a draft with thousands of blocks. Record rendering delay and frame rate.
- [ ] Test overlapping original terrain; use standalone inspection if the ghost is occluded.

## Placement and security

- [ ] Place only after explicit confirmation. Undo restores the prior world.
- [ ] Change the selected area or dimension during a job. Stale output is not applied.
- [ ] Modify a world block before placement. The existing world-state safety check still rejects stale placement.
- [ ] Verify permission rejection on a dedicated server without required authority.
- [ ] Confirm request packages contain relative block context, not API keys, inventories, player data or absolute world coordinates.

## Before public release

Do not publish this alpha as stable until the live checks above pass. Add representative model-output evaluations and resource-pack compatibility checks. Review distribution licensing, dependency support, installation documentation and recovery behavior separately.
