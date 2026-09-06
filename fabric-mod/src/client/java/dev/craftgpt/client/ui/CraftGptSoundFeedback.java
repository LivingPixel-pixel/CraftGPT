package dev.craftgpt.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class CraftGptSoundFeedback {
    private CraftGptSoundFeedback() {}

    public static void page(Minecraft minecraft) { play(minecraft, SoundEvents.BOOK_PAGE_TURN, 0.65F, 1.05F); }
    public static void select(Minecraft minecraft) { play(minecraft, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.25F); }
    public static void working(Minecraft minecraft) { play(minecraft, SoundEvents.ENCHANTMENT_TABLE_USE, 0.55F, 1.15F); }
    public static void success(Minecraft minecraft) { play(minecraft, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65F, 1.2F); }
    public static void complete(Minecraft minecraft) { play(minecraft, SoundEvents.PLAYER_LEVELUP, 0.55F, 1.2F); }
    public static void error(Minecraft minecraft) { play(minecraft, SoundEvents.VILLAGER_NO, 0.5F, 1.1F); }

    private static void play(Minecraft minecraft, SoundEvent sound, float volume, float pitch) {
        if (minecraft != null && minecraft.player != null) minecraft.player.playSound(sound, volume, pitch);
    }
}
