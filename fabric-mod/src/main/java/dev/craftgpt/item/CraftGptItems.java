package dev.craftgpt.item;

import dev.craftgpt.CraftGptMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.function.Function;

public final class CraftGptItems {
    public static final CraftBookItem CRAFT_BOOK = register(
        "craft_book",
        CraftBookItem::new,
        new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.RARE)
            .component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.craftgpt.craft_book.tooltip").withStyle(ChatFormatting.GRAY),
                Component.translatable("item.craftgpt.craft_book.use").withStyle(ChatFormatting.AQUA)
            )))
    );

    private CraftGptItems() {}

    private static <T extends Item> T register(
        String name,
        Function<Item.Properties, T> factory,
        Item.Properties properties
    ) {
        ResourceKey<Item> key = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(CraftGptMod.MOD_ID, name)
        );
        T item = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(CRAFT_BOOK));
    }
}
