package com.armaninyow.beegui.network;

import com.armaninyow.beegui.BeeGUI;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Sent client→server to request a fresh BeeGuiPacket.Payload for the given pos.
 * The screen sends this every second while it is open.
 */
public record BeeGuiRefreshPacket(BlockPos pos) implements CustomPayload {

	public static final Identifier REFRESH_ID = Identifier.of(BeeGUI.MOD_ID, "refresh_gui");
	public static final Id<BeeGuiRefreshPacket> ID = new Id<>(REFRESH_ID);

	public static final PacketCodec<PacketByteBuf, BeeGuiRefreshPacket> CODEC = PacketCodec.of(
		(value, buf) -> buf.writeBlockPos(value.pos),
		buf -> new BeeGuiRefreshPacket(buf.readBlockPos())
	);

	@Override
	public Id<? extends CustomPayload> getId() { return ID; }
}