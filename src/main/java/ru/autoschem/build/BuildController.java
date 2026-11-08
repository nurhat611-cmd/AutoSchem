package ru.autoschem.build;

import com.mojang.brigadier.StringReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.client.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
// import net.minecraft.util.hit.BlockRayTraceResult; // REMOVED: 1.21.11 uses BlockHitResult
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import ru.autoschem.builder.InstructionExecutor;
import ru.autoschem.client.ChatMessages;
import ru.autoschem.handler.FurnaceHandler;
import ru.autoschem.log.ErrorLogger;
import ru.autoschem.movement.MovementHandler;
import ru.autoschem.safety.SafetyAnalyzer;
import ru.autoschem.safety.SafetyHandler;
import ru.autoschem.vsch.Instruction;
import ru.autoschem.vsch.VschReader;
import ru.autoschem.vsch.VschStore;

public final class BuildController implements AutoCloseable {
    private final MinecraftClient client;
    private final SafetyHandler safety;
    private final ChatMessages messages;
    private final ErrorLogger errors;
    private final VschStore store;
    private final VschReader reader = new VschReader();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AutoSchem-VschLoader");
        thread.setDaemon(true);
        return thread;
    });
    private final MovementHandler movement = new MovementHandler();
    private final InstructionExecutor executor = new InstructionExecutor(client);
    private final FurnaceHandler furnace = new FurnaceHandler(client);
    private final SafetyAnalyzer safetyAnalyzer = new SafetyAnalyzer(client);
    private Future<?> currentTask;
    private boolean running = false;

    public BuildController(MinecraftClient client, SafetyHandler safety, ChatMessages messages, ErrorLogger errors, VschStore store) {
        this.client = client;
        this.safety = safety;
        this.messages = messages;
        this.errors = errors;
        this.store = store;
    }

    public void start(String schemePath) {
        if (running) return;
        running = true;
        
        currentTask = loader.submit(() -> {
            try {
                Path path = Path.of(schemePath);
                List<Instruction> instructions = reader.read(path);
                
                for (Instruction instruction : instructions) {
                    if (!running) break;
                    
                    // Safety check
                    if (safetyAnalyzer.checkDanger(client.player.getBlockPos())) {
                        messages.danger();
                        safety.block();
                        break;
                    }
                    
                    if (instruction.action() == Instruction.Action.PLACE || instruction.action() == Instruction.Action.BREAK) {
                        executor.execute(instruction);
                    } else if (instruction.action() == Instruction.Action.FURNACE) {
                        // Furnace handling
                        BlockPos origin = instruction.position();
                        // Assume ore and coal are in inventory or provided (simplified)
                        ItemStack ore = client.player.inventory.main.get(0);
                        ItemStack coal = client.player.inventory.main.get(1);
                        furnace.processFurnace(origin, ore, coal);
                    }
                    
                    // Movement logic if needed
                    // movement.moveTo(client.player, instruction.position());
                }
            } catch (IOException | InterruptedException e) {
                errors.log(e);
            } finally {
                running = false;
            }
        });
    }

    public void stop() {
        running = false;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }

    @Override
    public void close() {
        stop();
        loader.shutdown();
    }
}
