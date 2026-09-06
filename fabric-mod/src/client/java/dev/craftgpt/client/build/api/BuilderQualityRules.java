package dev.craftgpt.client.build.api;

/** Shared, compact quality contract for every CraftGPT builder path. */
public final class BuilderQualityRules {
    public static final String PROMPT_SECTION = """
        MINECRAFT BUILD QUALITY BAR:
        - Establish a readable silhouette and proportion before adding decoration. Avoid an accidental
          plain box when the selected volume and requested style allow a stronger footprint, height
          variation, offsets, or a clearly shaped roof.
        - Use a restrained palette with clear roles such as base, wall, frame, roof, and accent. Create
          visible contrast between major surfaces. Avoid noisy random texturing and avoid several nearly
          identical materials that do not improve readability.
        - Create real depth where bounds permit: frames or pillars may sit one block forward, walls and
          windows may be inset, panes may replace full glass blocks, and roofs should normally overhang
          exterior walls by one block. Preserve usable interior space.
        - Make the roof fit the footprint and style. Give it a coherent slope, ridge, edge, or intentional
          flat-roof treatment. Do not leave a thin flat cap on a house unless the requested style calls for it.
        - Keep facade rhythm deliberate. Space doors, windows, supports, and bays consistently, but use
          asymmetry when it improves the requested design. Entrances must remain reachable and interiors
          intended for players must have at least two clear blocks of headroom.
        - Detail only after massing, palette, depth, and roof are sound. Prefer a few purposeful trims,
          shutters, sills, stairs, slabs, fences, or landscaping accents over clutter on every surface.
        - Every freestanding block group must read as a recognizable, supported feature. Never emit
          temporary-looking marker columns, isolated color cubes, floating accents, abstract block piles,
          or a tree or lamp made from a few crude full cubes merely to fill unused space.
        - Join the building cleanly to terrain. Use a coherent foundation, floor, steps, or retaining edge.
          Do not leave accidental exposed dirt strips inside the footprint or under apparently finished walls.
        - Furnish interiors around walls and circulation paths. Keep the entrance, windows, and at least a
          one-block-wide route usable. Avoid unexplained full cubes in the middle of a room. Each furnishing
          must have a clear role and support beneath it.
        - A house roof must read as a roof from ground level. Avoid oversized solid rectangular caps,
          thick monochrome bands, and snow or white blocks used as a giant flat lid. Use stairs and slabs
          for a coherent slope or a deliberately detailed flat roof with a thin edge and controlled height.
        - Adapt these principles to the prompt, biome context, dimensions, and operation budget. Do not
          force a medieval house pattern onto modern, organic, technical, tiny, or non-house requests.

        AUTOMATIC REJECTION CONDITIONS FOR BUILDINGS:
        - random or temporary-looking isolated blocks inside or outside the main structure
        - a roof that is only an oversized thick slab without a deliberate architectural treatment
        - inaccessible doors, blocked circulation, unexplained center-room cubes, or missing floor support
        - exposed terrain accidentally trapped inside a finished footprint
        - decorations that float, lack support, collide with the facade, or have no recognizable purpose
        - a noisy material band that runs around the building without matching the requested style

        FINAL SILENT AUDIT BEFORE OUTPUT:
        1. The silhouette, footprint, front, and roof read as one intentional design.
        2. Major materials have distinct roles and the palette is coherent rather than noisy.
        3. Visible walls have depth or a deliberate reason to remain flat.
        4. Required doors, windows, openings, floors, supports, and roof coverage exist in operations.
        5. Door halves, directional states, stairs, slabs, and supports are complete and plausible.
        6. Every operation is safe, canonical, unique, in bounds, and changes the supplied world context.
        7. No operation creates a random marker, isolated cube, floating decoration, or interior obstruction.
        8. The foundation meets terrain cleanly and every visible exterior side looks intentionally finished.
        9. Judge the result from normal player eye level, not only as a coordinate diagram from above.
        If the volume is too small for every preference, preserve function and a clear silhouette first.
        """;

    private BuilderQualityRules() {
    }
}
