package com.armaninyow.beegui.client;

import com.armaninyow.beegui.network.BeeGuiPacket;
import com.armaninyow.beegui.network.BeeGuiRefreshPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

public class BeeGUIClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// S2C: when server sends updated hive data, update the open screen in-place
		ClientPlayNetworking.registerGlobalReceiver(BeeGuiPacket.Payload.ID, (payload, context) -> {
			context.client().execute(() -> {
				MinecraftClient client = context.client();
				if (client.currentScreen instanceof BeeManagementScreen screen
					&& screen.getPos().equals(payload.pos())) {
					// Screen already open for this hive — just refresh the data
					screen.updateData(payload.isHive(), payload.honeyLevel(), payload.bees());
				} else {
					// First open, or a different hive — open fresh screen
					client.setScreen(new BeeManagementScreen(
						payload.isHive(),
						payload.honeyLevel(),
						payload.bees(),
						payload.pos()
					));
				}
			});
		});
	}
}