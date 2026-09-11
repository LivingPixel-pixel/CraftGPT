package dev.craftgpt.item;

import dev.craftgpt.compat.network.ServerPlayNetworking;
import dev.craftgpt.network.OpenCraftBookPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class CraftBookItem extends Item {
    public CraftBookItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, OpenCraftBookPayload.TYPE)) {
            serverPlayer.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.05F);
            ServerPlayNetworking.send(serverPlayer, new OpenCraftBookPayload(true));
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return level.isClientSide() ? InteractionResultHolder.success(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand));
    }
    @Override public boolean isFoil(ItemStack stack) { return true; }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.craftgpt.craft_book.tooltip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.craftgpt.craft_book.use").withStyle(ChatFormatting.AQUA));
    }
}
