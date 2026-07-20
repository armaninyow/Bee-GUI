package com.armaninyow.beegui.network;

import com.armaninyow.beegui.BeeGUI;
import com.armaninyow.beegui.mixin.BeehiveBlockEntityAccessor;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class BeeGuiPacket {

    public static final Identifier OPEN_GUI_ID = Identifier.fromNamespaceAndPath(BeeGUI.MOD_ID, "open_gui");

    public record BeeInfo(String name, boolean hasNectar, boolean isBaby, float health, float maxHealth) {}

    public record Payload(
        boolean isHive,
        int honeyLevel,
        List<BeeInfo> bees,
        BlockPos pos
    ) implements CustomPacketPayload {
        public static final Type<Payload> ID = new Type<>(OPEN_GUI_ID);

        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.isHive);
                buf.writeInt(value.honeyLevel);
                buf.writeInt(value.bees.size());
                for (BeeInfo bee : value.bees) {
                    buf.writeUtf(bee.name());
                    buf.writeBoolean(bee.hasNectar());
                    buf.writeBoolean(bee.isBaby());
                    buf.writeFloat(bee.health());
                    buf.writeFloat(bee.maxHealth());
                }
                buf.writeBlockPos(value.pos);
            },
            buf -> {
                boolean isHive = buf.readBoolean();
                int honeyLevel = buf.readInt();
                int beeCount = buf.readInt();
                List<BeeInfo> bees = new ArrayList<>();
                for (int i = 0; i < beeCount; i++) {
                    bees.add(new BeeInfo(
                        buf.readUtf(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readFloat(),
                        buf.readFloat()
                    ));
                }
                return new Payload(isHive, honeyLevel, bees, buf.readBlockPos());
            }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void registerServerPackets() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.ID, Payload.CODEC);
    }

    public static void sendToClient(ServerPlayer player, BeehiveBlockEntity beehive, BlockState state, BlockPos pos) {
        boolean isHive = state.getBlock() == Blocks.BEEHIVE;

        int honeyLevel = 0;
        try { honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL); } catch (Exception ignored) {}

        List<BeeInfo> beeInfos = new ArrayList<>();

        List<BeehiveBlockEntity.Occupant> occupants = ((BeehiveBlockEntityAccessor) beehive).beegui$getBees();
        for (BeehiveBlockEntity.Occupant data : occupants) {
            CompoundTag entityTag = data.entityData().copyTagWithoutId();

            boolean hasNectar = entityTag.getBooleanOr("HasNectar", false);
            boolean isBaby    = entityTag.getIntOr("Age", 0) < 0;
            float   health    = entityTag.getFloatOr("Health", 0f);
            float   maxHealth = 10.0f;
            String  name      = isBaby ? "Baby Bee" : "Bee";

            if (entityTag.contains("CustomName")) {
                try {
                    String rawName = entityTag.getStringOr("CustomName", "");
                    if (!rawName.isEmpty()) {
                        if (rawName.startsWith("{")) {
                            JsonObject obj = JsonParser.parseString(rawName).getAsJsonObject();
                            if (obj.has("text") && !obj.get("text").getAsString().isEmpty())
                                name = obj.get("text").getAsString();
                        } else {
                            name = rawName;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (entityTag.contains("attributes")) {
                ListTag attrs = entityTag.getListOrEmpty("attributes");
                for (int j = 0; j < attrs.size(); j++) {
                    CompoundTag attr = attrs.getCompoundOrEmpty(j);
                    if ("minecraft:generic.max_health".equals(attr.getStringOr("id", ""))) {
                        maxHealth = (float) attr.getDoubleOr("base", 10.0);
                        break;
                    }
                }
            }

            if (health <= 0) health = maxHealth;
            beeInfos.add(new BeeInfo(name, hasNectar, isBaby, health, maxHealth));
        }

        ServerPlayNetworking.send(player, new Payload(isHive, honeyLevel, beeInfos, pos));
    }
}