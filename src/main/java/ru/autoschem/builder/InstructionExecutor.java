package ru.autoschem.builder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerActionC2SPacket;
import net.minecraft.client.network.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.DirectionFacingBlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.autoschem.movement.MovementHandler;
import ru.autoschem.vsch.Instruction;

public final class InstructionExecutor {
    private final MinecraftClient client;
    private final MovementHandler movement;

    public InstructionExecutor(MinecraftClient client, MovementHandler movement) {
        this.client = client;
        this.movement = movement;
    }

    public void execute(Instruction instruction, BlockPos origin) {
        BlockPos target = origin.add(instruction.x(), instruction.y(), instruction.z());
        
        // SafetyAnalyzer check before execution
        if (!ru.autoschem.safety.SafetyAnalyzer.isSafe(client, target)) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("[AutoSchem] Опасность! Обхожу препятствие.")
                            .formatted(net.minecraft.util.Formatting.RED),
                    false);
            return;
        }

        // Move to target using MovementHandler
        movement.moveTo(client.player, target);

        if (instruction.action() == Instruction.Action.BREAK) {
            // Break block using PlayerActionC2SPacket
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    target,
                    Direction.UP
            ));
            // Continue breaking (STOP_DESTROY_BLOCK after delay or tick management)
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    target,
                    Direction.UP
            ));
        } else if (instruction.action() == Instruction.Action.PLACE) {
            // Place block using PlayerInteractBlockC2SPacket
            // Minecraft 1.21.x uses PlayerInteractBlockC2SPacket
            BlockHitResult hit = new DirectionFacingBlockHitResult(
                    new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                    Direction.UP,
                    target,
                    false
            );
            client.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND,
                    hit
            ));
            // Swing hand packet
            client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }
}
