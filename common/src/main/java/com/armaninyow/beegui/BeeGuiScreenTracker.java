package com.armaninyow.beegui;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BeeGuiScreenTracker {
    private BeeGuiScreenTracker() {}

    public static final Set<BlockPos> openScreenPositions =
        Collections.newSetFromMap(new ConcurrentHashMap<>());
}