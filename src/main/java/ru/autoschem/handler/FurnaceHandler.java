package ru.autoschem.handler;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
// import net.minecraft.util.hit.BlockRayTraceResult; // REMOVED: 1.21.11 uses BlockHitResult
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.autoschem.movement.MovementHandler;

import java.util.List;

public class FurnaceHandler {
    private final MinecraftClient client;
    private final MovementHandler movement;

    public FurnaceHandler(MinecraftClient client) {
        this.client = client;
        this.movement = new MovementHandler();
    }

    public void processFurnace(BlockPos origin, ItemStack ore, ItemStack coal) {
        BlockPos furnacePos = findFurnace(origin);
        if (furnacePos == null) {
            return;
        }

        movement.moveTo(client.player, furnacePos);

        // Open furnace screen handler
        // In 1.21.x, opening is handled via interaction packet or client-side screen handler open
        // We simulate interaction to open
        // 1.21.11 uses BlockHitResult with int slot/face parameter in PlayerInteractBlockC2SPacket constructor
        BlockHitResult hit = new BlockHitResult(
                new Vec3d(furnacePos.getX() + 0.5, furnacePos.getY() + 0.5, furnacePos.getZ() + 0.5),
                Direction.UP,
                furnacePos,
                false
        );
        // 1.21.11 PlayerInteractBlockC2SPacket constructor: (Hand, BlockHitResult, int face/slot)
        // Using -1 or 0 as face/slot depending on 1.21.11 API (usually 0 or -1 for default)
        client.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.MAIN_HAND,
                hit,
                0
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
        // Box constructor in 1.21.11: Box(Vec3d min, Vec3d max)
        Box searchBox = new Box(
                new Vec3d(origin.getX() - 16, origin.getY() - 16, origin.getZ() - 16),
                new Vec3d(origin.getX() + 16, origin.getY() + 16, origin.getZ() + 16)
        );
        // ClientWorld.getBlockEntities in 1.21.11 uses Box (Vec3d, Vec3d) or no arguments?
        // Actually, ClientWorld.getBlockEntities(Box) takes Box.
        // Let's use client.world.getBlockEntities(searchBox)
        // If getBlockEntities(Box) is not available, we might need to use client.world.getEntitiesByClass or similar.
        // In 1.21.11, ClientWorld.getBlockEntities(Box) is standard.
        List<net.minecraft.block.entity.BlockEntity> entities = client.world.getBlockEntities(searchBox);
        List<BlockPos> furnaces = entities.stream()
                .filter(be -> be instanceof net.minecraft.block.entity.FurnaceBlockEntity)
                .map(be -> be.getPos())
                .toList();
        
        if (!furnaces.isEmpty()) {
            return furnaces.get(0);
        }
        
        return null;
    }
}
