package com.armaninyow.beegui;

import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BeeGUI implements ModInitializer {
	public static final String MOD_ID = "beegui";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BeeGuiPacket.registerServerPackets();

		// Register the C2S refresh packet
		PayloadTypeRegistry.playC2S().register(BeeGuiRefreshPacket.ID, BeeGuiRefreshPacket.CODEC);

		// When the client requests a refresh, re-send the hive data
		ServerPlayNetworking.registerGlobalReceiver(BeeGuiRefreshPacket.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			var pos   = payload.pos();
			var world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof BeehiveBlock)) return;
			var be = world.getBlockEntity(pos);
			if (!(be instanceof BeehiveBlockEntity beehive)) return;
			BeeGuiPacket.sendToClient(player, beehive, state, pos);
		});

		// Initial open on shift+right-click
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
			if (!player.isSneaking()) return ActionResult.PASS;

			var pos   = hitResult.getBlockPos();
			var state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof BeehiveBlock)) return ActionResult.PASS;

			// Return SUCCESS on both client and server to cancel block placement
			if (world.isClient()) return ActionResult.SUCCESS;

			var blockEntity = world.getBlockEntity(pos);
			if (!(blockEntity instanceof BeehiveBlockEntity beehive)) return ActionResult.PASS;

			if (player instanceof ServerPlayerEntity serverPlayer) {
				BeeGuiPacket.sendToClient(serverPlayer, beehive, state, pos);
			}
			return ActionResult.SUCCESS;
		});
	}
}