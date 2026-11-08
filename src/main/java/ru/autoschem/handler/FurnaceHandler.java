package ru.autoschem.handler;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.BlockRayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.autoschem.movement.MovementHandler;

import java.util.List;

public final class FurnaceHandler {
    private final MinecraftClient client;
    private final MovementHandler movement;

    public FurnaceHandler(MinecraftClient client, MovementHandler movement) {
        this.client = client;
        this.movement = movement;
    }

    public void findAndSmelt(BlockPos origin, ItemStack ore, ItemStack coal) {
        if (client.world == null || client.player == null) {
            return;
        }

        // Search furnace in radius 16
        BlockPos furnacePos = findFurnace(origin);
        if (furnacePos == null) {
            return;
        }

        // Move to furnace
        movement.moveTo(client.player, furnacePos);

        // Open furnace screen handler
        // In 1.21.x, opening is handled via interaction packet or client-side screen handler open
        // We simulate interaction to open
        BlockHitResult hit = new BlockRayTraceResult(
                new Vec3d(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5),
                Direction.UP,
                furnacePos,
                false
        );
        client.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND,
                hit
        ));

        // Wait briefly for screen handler to open (handled in tick/update)
        ScreenHandler screen = client.player.currentScreenHandler;
        if (screen instanceof FurnaceScreenHandler furnaceScreen) {
            // Put ore in input slot (slot 0)
            furnaceScreen.slots.get(0).setStack(ore);
            // Put coal in fuel slot (slot 1)
            furnaceScreen.slots.get(1).setStack(coal);
        }
    }

    private BlockPos findFurnace(BlockPos origin) {
        if (client.world == null) {
            return null;
        }
        Box searchBox = new Box(origin.add(-16, -16, -16), origin.add(16, 16, 16));
        List<BlockPos> furnaces = client.world.getBlockEntities(searchBox).stream()
                .filter(be -> be instanceof net.minecraft.block.entity.FurnaceBlockEntity)
                .map(be -> be.getPos())
                .toList();
        
        if (!furnaces.isEmpty()) {
            return furnaces.get(0);
        }
        
        // Fallback: search blocks
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (client.world.getBlockState(pos).isOf(Blocks.FURNACE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
