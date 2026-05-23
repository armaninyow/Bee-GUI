package com.armaninyow.beegui;

import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 26.1.x
public class BeeGUI implements ModInitializer {
    public static final String MOD_ID = "beegui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BeeGuiPacket.registerServerPackets();

        PayloadTypeRegistry.serverboundPlay().register(BeeGuiRefreshPacket.ID, BeeGuiRefreshPacket.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BeeGuiRefreshPacket.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            var pos   = payload.pos();
            var world = (ServerLevel) player.level();
            var state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof BeehiveBlock)) return;
            var be = world.getBlockEntity(pos);
            if (!(be instanceof BeehiveBlockEntity beehive)) return;
            BeeGuiPacket.sendToClient(player, beehive, state, pos);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;

            var pos   = hitResult.getBlockPos();
            var state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof BeehiveBlock)) return InteractionResult.PASS;

            if (world.isClientSide()) return InteractionResult.SUCCESS;

            var blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof BeehiveBlockEntity beehive)) return InteractionResult.PASS;

            if (player instanceof ServerPlayer serverPlayer) {
                BeeGuiPacket.sendToClient(serverPlayer, beehive, state, pos);
            }
            return InteractionResult.SUCCESS;
        });
    }
}