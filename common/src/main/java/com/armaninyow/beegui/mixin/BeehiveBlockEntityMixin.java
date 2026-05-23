package com.armaninyow.beegui.mixin;

import com.armaninyow.beegui.BeeGuiScreenTracker;
import com.armaninyow.beegui.network.BeeGuiPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 26.1.x
@Mixin(BeehiveBlockEntity.class)
public class BeehiveBlockEntityMixin {

    @Unique private int beegui$lastBeeCount   = -1;
    @Unique private int beegui$lastHoneyLevel = -1;

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void beegui$onServerTick(
        Level level, BlockPos pos, BlockState state,
        BeehiveBlockEntity beehive, CallbackInfo ci
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!BeeGuiScreenTracker.openScreenPositions.contains(pos)) return;

        int honeyLevel = 0;
        try { honeyLevel = state.getValue(BeehiveBlock.HONEY_LEVEL); } catch (Exception ignored) {}

        int beeCount = beehive.getOccupantCount();

        BeehiveBlockEntityMixin self = (BeehiveBlockEntityMixin) (Object) beehive;
        if (beeCount == self.beegui$lastBeeCount && honeyLevel == self.beegui$lastHoneyLevel) {
            return;
        }
        self.beegui$lastBeeCount   = beeCount;
        self.beegui$lastHoneyLevel = honeyLevel;

        for (ServerPlayer player : serverLevel.players()) {
            BeeGuiPacket.sendToClient(player, beehive, state, pos);
        }
    }
}