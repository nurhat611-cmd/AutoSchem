package ru.autoschem.builder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
// import net.minecraft.util.hit.BlockRayTraceResult; // REMOVED: 1.21.11 uses BlockHitResult
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.autoschem.movement.MovementHandler;
import ru.autoschem.vsch.Instruction;

public class InstructionExecutor {
    private final MinecraftClient client;
    private final MovementHandler movement;

    public InstructionExecutor(MinecraftClient client) {
        this.client = client;
        this.movement = new MovementHandler();
    }

    public void execute(Instruction instruction) {
        if (instruction.action() == Instruction.Action.BREAK) {
            BlockPos target = instruction.position();
            // Break block using PlayerActionC2SPacket
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    target,
                    Direction.UP
            ));
            // Wait for delay
            try {
                Thread.sleep(instruction.delay() * 50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            client.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                    target,
                    Direction.UP
            ));
        } else if (instruction.action() == Instruction.Action.PLACE) {
            // Place block using PlayerInteractBlockC2SPacket
            // Minecraft 1.21.x uses PlayerInteractBlockC2SPacket
            BlockPos target = instruction.position();
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                    Direction.UP,
                    target,
                    false
            );
            // 1.21.11 PlayerInteractBlockC2SPacket constructor: (Hand, BlockHitResult, int face/slot)
            client.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.MAIN_HAND,
                    hit,
                    0
            ));
            // Swing hand packet
            client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            
            // Wait for delay
            try {
                Thread.sleep(instruction.delay() * 50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
