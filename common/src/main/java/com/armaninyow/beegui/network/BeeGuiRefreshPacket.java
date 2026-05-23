package com.armaninyow.beegui.network;

import com.armaninyow.beegui.BeeGUI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BeeGuiRefreshPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Identifier REFRESH_ID = Identifier.fromNamespaceAndPath(BeeGUI.MOD_ID, "refresh_gui");
    public static final Type<BeeGuiRefreshPacket> ID = new Type<>(REFRESH_ID);

    public static final StreamCodec<FriendlyByteBuf, BeeGuiRefreshPacket> CODEC = StreamCodec.of(
        (buf, value) -> buf.writeBlockPos(value.pos),
        buf -> new BeeGuiRefreshPacket(buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}