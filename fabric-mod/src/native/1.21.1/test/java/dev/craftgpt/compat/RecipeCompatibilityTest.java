package dev.craftgpt.compat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCompatibilityTest {
    @Test void vanillaLoadsPackagedCraftBookRecipe() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var stream = getClass().getResourceAsStream("/data/craftgpt/recipe/craft_book.json")) {
            assertNotNull(stream, "Recipe must use the 1.21.1 resource directory");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("craftgpt:craft_book", json.getAsJsonObject("result").get("id").getAsString());
            // Keep the shipped ingredients and schema; vanilla bootstrap only provides vanilla result items.
            json.getAsJsonObject("result").addProperty("id", "minecraft:book");
            var ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            var recipe = ShapedRecipe.Serializer.CODEC.codec().parse(ops, json).getOrThrow();
            assertEquals(3, recipe.getWidth());
            assertEquals(3, recipe.getHeight());
            assertEquals(5, recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count());
        }
    }
}
