package com.armaninyow.beegui.network;

import com.armaninyow.beegui.BeeGUI;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

// 1.21.6_1.21.8
public class BeeGuiPacket {

	public static final Identifier OPEN_GUI_ID = Identifier.of(BeeGUI.MOD_ID, "open_gui");

	public record BeeInfo(String name, boolean hasNectar, boolean isBaby, float health, float maxHealth) {}

	public record Payload(
		boolean isHive,
		int honeyLevel,
		List<BeeInfo> bees,
		BlockPos pos
	) implements CustomPayload {
		public static final Id<Payload> ID = new Id<>(OPEN_GUI_ID);

		public static final PacketCodec<PacketByteBuf, Payload> CODEC = PacketCodec.of(
			(value, buf) -> {
				buf.writeBoolean(value.isHive);
				buf.writeInt(value.honeyLevel);
				buf.writeInt(value.bees.size());
				for (BeeInfo bee : value.bees) {
					buf.writeString(bee.name());
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
						buf.readString(),
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
		public Id<? extends CustomPayload> getId() { return ID; }
	}

	public static void registerServerPackets() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
	}

	public static void sendToClient(ServerPlayerEntity player, BeehiveBlockEntity beehive, BlockState state, BlockPos pos) {
		boolean isHive = state.getBlock() == Blocks.BEEHIVE;

		int honeyLevel = 0;
		try { honeyLevel = state.get(BeehiveBlock.HONEY_LEVEL); } catch (Exception ignored) {}

		ServerWorld world = (ServerWorld) player.getWorld();
		RegistryWrapper.WrapperLookup registries = world.getRegistryManager();

		NbtCompound beehiveNbt = null;
		try { beehiveNbt = beehive.toInitialChunkDataNbt(registries); } catch (Exception ignored) {}
		if (beehiveNbt == null || !beehiveNbt.contains("bees")) {
			try { beehiveNbt = beehive.createNbt(registries); } catch (Exception ignored) {}
		}

		List<BeeInfo> beeInfos = new ArrayList<>();
		if (beehiveNbt != null) {
			NbtList beesList = beehiveNbt.getListOrEmpty("bees");
			for (int i = 0; i < beesList.size(); i++) {
				NbtCompound entry      = beesList.getCompound(i).orElseGet(NbtCompound::new);
				NbtCompound entityData = entry.getCompoundOrEmpty("entity_data");

				boolean hasNectar = entityData.getBoolean("HasNectar", false);
				boolean isBaby    = entityData.getInt("Age", 0) < 0;
				float   health    = entityData.getFloat("Health", 0f);
				float   maxHealth = 10.0f;
				String  name      = isBaby ? "Baby Bee" : "Bee";

				if (entityData.contains("CustomName")) {
					try {
						var parsed = JsonParser.parseString(entityData.getString("CustomName", ""));
						if (parsed.isJsonObject()) {
							JsonObject obj = parsed.getAsJsonObject();
							if (obj.has("text") && !obj.get("text").getAsString().isEmpty())
								name = obj.get("text").getAsString();
						} else if (parsed.isJsonPrimitive() && !parsed.getAsString().isEmpty()) {
							name = parsed.getAsString();
						}
					} catch (Exception ignored) {}
				}

				NbtList attrs = entityData.getListOrEmpty("attributes");
				for (int j = 0; j < attrs.size(); j++) {
					NbtCompound attr = attrs.getCompound(j).orElseGet(NbtCompound::new);
					if ("minecraft:generic.max_health".equals(attr.getString("id", ""))) {
						maxHealth = (float) attr.getDouble("base", 10.0);
						break;
					}
				}

				if (health <= 0) health = maxHealth;
				beeInfos.add(new BeeInfo(name, hasNectar, isBaby, health, maxHealth));
			}
		}

		ServerPlayNetworking.send(player, new Payload(isHive, honeyLevel, beeInfos, pos));
	}
}