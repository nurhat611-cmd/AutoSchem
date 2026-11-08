package ru.autoschem.safety;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class SafetyAnalyzer {
    private SafetyAnalyzer() {
    }

    public static boolean isSafe(MinecraftClient client, BlockPos pos) {
        if (client.world == null || client.player == null) {
            return true;
        }
        
        // Check lava or fire at target
        if (client.world.getBlockState(pos).isOf(Blocks.LAVA) || client.world.getBlockState(pos).isOf(Blocks.FIRE)) {
            return false;
        }
        
        // Check void/empty below
        BlockPos below = pos.down();
        if (below.getY() < 0 || (client.world.getBlockState(below).isAir() && client.world.getBlockState(below.down()).isAir())) {
            // Consider void or dangerous drop
            return false;
        }
        
        // Check lava/fire below
        if (client.world.getBlockState(below).isOf(Blocks.LAVA) || client.world.getBlockState(below).isOf(Blocks.FIRE)) {
            return false;
        }
        
        return true;
    }
}
