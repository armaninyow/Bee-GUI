package com.armaninyow.beegui.mixin;

import com.armaninyow.beegui.BeeGuiScreenTracker;
import com.armaninyow.beegui.network.BeeGuiPacket;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21 / 1.21.1 version of BeehiveBlockEntityMixin.
 * Overrides the common/ copy because NbtCompound.getListOrEmpty() does not
 * exist in 1.21/1.21.1 — use getList(key, type) instead (type 10 = NbtCompound).
 */
@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {

	@Unique private int beegui$lastBeeCount   = -1;
	@Unique private int beegui$lastHoneyLevel = -1;

	@Inject(method = "serverTick", at = @At("TAIL"))
	private static void beegui$onServerTick(
		World world, BlockPos pos, BlockState state,
		BeehiveBlockEntity beehive, CallbackInfo ci
	) {
		if (!(world instanceof ServerWorld serverWorld)) return;
		if (!BeeGuiScreenTracker.openScreenPositions.contains(pos)) return;

		int honeyLevel = 0;
		try { honeyLevel = state.get(BeehiveBlock.HONEY_LEVEL); } catch (Exception ignored) {}

		RegistryWrapper.WrapperLookup registries = serverWorld.getRegistryManager();
		NbtCompound nbt = null;
		try { nbt = beehive.toInitialChunkDataNbt(registries); } catch (Exception ignored) {}
		if (nbt == null || !nbt.contains("bees")) {
			try { nbt = beehive.createNbt(registries); } catch (Exception ignored) {}
		}
		// 1.21/1.21.1: getList(key, type) — type 10 = NbtCompound
		int beeCount = (nbt != null) ? nbt.getList("bees", 10).size() : 0;

		BeehiveBlockEntityMixin self = (BeehiveBlockEntityMixin) (Object) beehive;
		if (beeCount == self.beegui$lastBeeCount && honeyLevel == self.beegui$lastHoneyLevel) {
			return;
		}
		self.beegui$lastBeeCount   = beeCount;
		self.beegui$lastHoneyLevel = honeyLevel;

		for (ServerPlayerEntity player : serverWorld.getPlayers()) {
			BeeGuiPacket.sendToClient(player, beehive, state, pos);
		}
	}
}