package dev.craftgpt.build.model;

import java.util.List;

/** Bounded declarative geometry, never executable code. Bounds are inclusive. */
public record BuildComponent(String id, String kind, List<Integer> from, List<Integer> to,
                             int paletteIndex, String axis, String facing) { }
