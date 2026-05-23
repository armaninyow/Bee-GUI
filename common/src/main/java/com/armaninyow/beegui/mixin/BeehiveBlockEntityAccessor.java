package com.armaninyow.beegui.mixin;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Exposes BeehiveBlockEntity.getBees() (private) which returns
 * List<Occupant> — the public Occupant record we can read safely.
 */
@Mixin(BeehiveBlockEntity.class)
public interface BeehiveBlockEntityAccessor {

    @Invoker("getBees")
    List<BeehiveBlockEntity.Occupant> beegui$getBees();
}