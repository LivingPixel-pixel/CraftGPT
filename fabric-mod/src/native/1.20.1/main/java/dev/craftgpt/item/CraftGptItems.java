package dev.craftgpt.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Item registration before Minecraft introduced data components. */
public final class CraftGptItems {
    public static final CraftBookItem CRAFT_BOOK = Registry.register(BuiltInRegistries.ITEM,
        new ResourceLocation("craftgpt", "craft_book"), new CraftBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    private CraftGptItems() {}
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(CRAFT_BOOK));
    }
}
