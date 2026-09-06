package dev.craftgpt.item;

import dev.craftgpt.network.OpenCraftBookPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class CraftBookItem extends Item {
    public CraftBookItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer
            && ServerPlayNetworking.canSend(serverPlayer, OpenCraftBookPayload.TYPE)) {
            serverPlayer.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.05F);
            ServerPlayNetworking.send(serverPlayer, new OpenCraftBookPayload(true));
            return InteractionResult.SUCCESS_SERVER;
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

}
