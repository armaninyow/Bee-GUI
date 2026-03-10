package com.armaninyow.beegui;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which hive positions currently have a BeeManagementScreen open on this client.
 * Written by the client, read by the server-side mixin through a shared static field
 * (safe because client and server run in the same JVM in singleplayer/LAN, and on a
 * dedicated server the mixin's player loop guards the actual send anyway).
 *
 * On a dedicated server this class is never written to, so the set stays empty and
 * the mixin falls back to the 1-second polling refresh already in the screen.
 */
public final class BeeGuiScreenTracker {
	private BeeGuiScreenTracker() {}

	/** Positions of hives whose GUI is currently open on this client. */
	public static final Set<BlockPos> openScreenPositions =
		Collections.newSetFromMap(new ConcurrentHashMap<>());
}