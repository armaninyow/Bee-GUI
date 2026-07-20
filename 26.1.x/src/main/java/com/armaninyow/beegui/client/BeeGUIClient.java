package com.armaninyow.beegui.client;

import com.armaninyow.beegui.network.BeeGuiPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class BeeGUIClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BeeGuiPacket.Payload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft client = context.client();
                if (client.screen instanceof BeeManagementScreen screen
                    && screen.getPos().equals(payload.pos())) {
                    screen.updateData(payload.isHive(), payload.honeyLevel(), payload.bees());
                } else {
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