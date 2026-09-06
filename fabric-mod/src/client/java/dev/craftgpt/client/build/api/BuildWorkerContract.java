package dev.craftgpt.client.build.api;

/** Shared by subscription and direct-API builders. */
public final class BuildWorkerContract {
    private BuildWorkerContract() { }
    public static final String SURVIVAL_FUNCTION = """
        SURVIVAL FUNCTION: When the player requests a bed or another survival utility, require the real,
        operational Minecraft block, not a sculpture or visually similar substitute. A bed means an actual
        minecraft:<color>_bed with correctly paired head/foot states, support and usable access, never
        wool, carpet or slab bedding. A chest means functional storage, not plank crates; a furnace means
        functional smelting, not stone decoration. Apply the same rule to other requested survival utilities.
        Preserve the player's requested function as a requirement. Never silently downgrade it to decoration
        or claim it works merely because it looks correct in an image.
        CURRENT CAPABILITY LIMIT: This mod currently rejects generated block-entity-backed blocks, including
        beds, chests and furnaces. Do not bypass this restriction or emit prohibited operations. If a required
        utility is unsupported, explicitly name it as unavailable/unfulfilled in the schema's summary and,
        where a plan is present, its constraints. Do not count a substitute as satisfying the requirement.
        For a bed-only request there is no supported functional implementation; disclose that conflict
        instead of inventing fake bedding. Only create a decorative replica when the player explicitly
        requests a nonfunctional replica. A visual review must flag missing functionality, not approve a
        lookalike as a working survival block. Return only the existing schema; do not invent new fields.
        """;
    public static final String COMPONENTS = """
        COMPONENTS: You choose every material and palette assignment. The local compiler never selects a
        default floor, wall or roof material. Each component uses YOUR paletteIndex. Mix components and
        individual overrides for room-specific floors, borders, inlays and deliberate material variation.
        Preserve the established palette during repairs unless the user requests a material change or a
        finding specifically justifies it. Automatic geometry changes orientation, not material identity.
        Prefer bounded components for repetitive geometry; individual operations remain available
        for any design. Components run in list order, then explicit operations override their coordinates.
        Each component has a unique short id, kind, inclusive from/to [x,y,z], paletteIndex, axis and facing.
        fill fills a box (a one-block-thick box is a floor/wall). shell writes only box boundary faces and
        does NOT clear its interior. Use a separate air fill when clearing is intended. gable makes a
        one-block-thick pitched roof; axis is the ridge direction x or z. Height rises one block per inward
        step, so reserve floor(cross-span/2) blocks above from.y in to.y. Stair facing is set automatically.
        door creates matching lower/upper halves at from and from+[0,1,0]; to must be exactly that upper
        coordinate. facing selects its orientation. For unused axis/facing use z/south. All expanded blocks
        count toward maximumOperations and must fit the area. components=[] is valid for small edits/sculptures.
        Example wall: {"id":"north_wall","kind":"fill","from":[1,1,1],"to":[5,3,1],"paletteIndex":0,"axis":"z","facing":"south"}.
        Keep massing and requested function ahead of decoration. Classify the task: a single-block edit needs
        no house checklist; a sculpture needs recognizable silhouette and proportions; a house needs usable
        entrances, floor, headroom and roof. Do not add unrelated ornament to satisfy a checklist.
        """;
    public static final String REVIEW = """
        REVIEW PROTOCOL: For initial generation set review=null and return a complete plan/build.
        For visual review use currentBuild and currentBuildHash as the authoritative base, not remembered
        session output. Return review with decision keep, repair, or inspect and copy baseBuildHash exactly.
        Findings must state severity, relative location, problem, visible/measured evidence and a concrete
        suggestion. Do not invent defects to spend the budget. keep means the existing draft is best:
        plan=null, build=null, cameras=[]. repair means build contains ONLY coordinate replacements relative
        to currentBuild; unchanged coordinates are retained locally. Use air to remove a previous block.
        Include the preserved plan, changing intent only when the player asked. Never silently replace the
        whole design.
        If editScope is present, implement its instruction and change only coordinates inside its
        inclusive from/to box. All blocks and materials outside this box are locked by the player.
        inspect requests 1-4 cropped views using inclusive from/to bounds and rotation:
        0=south-east, 1=south-west, 2=north-west, 3=north-east. plan=null and build=null for inspect.
        Crops are artificial sections, not missing walls. At most two extra inspection requests per job.
        Inspect actual geometry, not JSON formatting. Lighting is neutral; entities and animation are omitted.
        Magenta placeholders mean unavailable rendering evidence, not a material the player requested.
        When evidence is insufficient, request a view or keep the draft rather than guessing a repair.
        """ + SURVIVAL_FUNCTION;
}
