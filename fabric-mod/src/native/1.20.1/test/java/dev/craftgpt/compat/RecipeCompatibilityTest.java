package dev.craftgpt.compat;

import com.google.gson.JsonParser;
import dev.craftgpt.compat.network.NativeNetwork;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeCompatibilityTest {
    @Test void vanillaLoadsPackagedCraftBookRecipe() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var stream = getClass().getResourceAsStream("/data/craftgpt/recipes/craft_book.json")) {
            assertNotNull(stream, "Recipe must use the 1.20.1 resource directory");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("craftgpt:craft_book", json.getAsJsonObject("result").get("item").getAsString());
            // Vanilla bootstrap has no custom item registration. Validate the real recipe schema with a vanilla result.
            json.getAsJsonObject("result").addProperty("item", "minecraft:book");
            var recipe = new ShapedRecipe.Serializer().fromJson(NativeNetwork.id("craftgpt", "craft_book"), json);
            assertEquals(3, recipe.getWidth());
            assertEquals(3, recipe.getHeight());
            assertEquals(5, recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count());
        }
    }
}
