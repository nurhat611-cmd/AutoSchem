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
import net.minecraft.util.hit.BlockRayTraceResult;
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
    private final InstructionExecutor executor;
    private final FurnaceHandler furnace;
    private Run current;

    public BuildController(MinecraftClient client, SafetyHandler safety, ChatMessages messages,
            ErrorLogger errors, VschStore store) {
        this.client = client;
        this.safety = safety;
        this.messages = messages;
        this.errors = errors;
        this.store = store;
        this.executor = new InstructionExecutor(client, movement);
        this.furnace = new FurnaceHandler(client, movement);
    }

    public int start() {
        if (!safety.check(client)) {
            return 0;
        }
        if (current != null) {
            messages.send(client, "Строительство уже запущено. Сначала используйте /autoschem stop.", Formatting.YELLOW);
            return 0;
        }
        if (client.world.getDifficulty() != Difficulty.PEACEFUL) {
            messages.send(client, "Для строительства включите мирную сложность.", Formatting.RED);
            return 0;
        }
        try {
            Optional<Path> latest = store.latest();
            if (latest.isEmpty()) {
                messages.send(client, "Готовая схема не найдена в config/autoschem/vsch.", Formatting.RED);
                return 0;
            }
            BlockPos origin = findOrigin();
            Run run = new Run(client.getServer(), client.player.getUuid(), client.world.getRegistryKey(), origin);
            current = run;
            messages.send(client, "Подготовка: " + latest.get().getFileName() + ", начало "
                    + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
                    + ". Блоки будут заменены, предметы не расходуются.", Formatting.YELLOW);
            run.loading = loader.submit(() -> load(run, latest.get()));
            return 1;
        } catch (Exception exception) {
            cancelCurrent();
            errors.report(exception);
            return 0;
        }
    }

    public int stop() {
        if (!safety.check(client)) {
            return 0;
        }
        if (current == null) {
            messages.send(client, "Строительство не запущено.", Formatting.YELLOW);
            return 0;
        }
        cancelCurrent();
        messages.send(client, "Строительство остановлено; изменённые блоки сохранены.", Formatting.YELLOW);
        return 1;
    }

    public void tick() {
        Run run = current;
        if (run == null) {
            return;
        }
        if (!sameSession(run)) {
            cancelCurrent();
            return;
        }
        if (client.world.getDifficulty() != Difficulty.PEACEFUL) {
            cancelCurrent();
            messages.send(client, "Строительство остановлено: сложность больше не мирная.", Formatting.RED);
            return;
        }
        if (run.instructions == null || run.inFlight) {
            return;
        }
        if (run.index >= run.instructions.size()) {
            finish(run);
            return;
        }
        if (run.delay > 0) {
            run.delay--;
            return;
        }
        Instruction instruction = run.instructions.get(run.index);
        run.inFlight = true;
        try {
            run.server.execute(() -> apply(run, instruction));
        } catch (Exception exception) {
            fail(run, exception, run.origin);
        }
    }

    public void disconnected() {
        cancelCurrent();
    }

    private BlockPos findOrigin() {
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos().offset(hit.getSide());
        }
        return client.player.getBlockPos().offset(client.player.getHorizontalFacing(), 2);
    }

    private void load(Run run, Path file) {
        try {
            List<Instruction> instructions = reader.read(file);
            if (run.cancelled) {
                return;
            }
            client.execute(() -> {
                if (!isCurrent(run) || !sameSession(run)) {
                    return;
                }
                run.inFlight = true;
                try {
                    run.server.execute(() -> prepare(run, instructions));
                } catch (Exception exception) {
                    fail(run, exception, run.origin);
                }
            });
        } catch (Exception exception) {
            client.execute(() -> fail(run, exception, run.origin));
        }
    }

    private void prepare(Run run, List<Instruction> instructions) {
        BlockPos target = run.origin;
        try {
            if (run.cancelled) {
                return;
            }
            ServerWorld world = getWorld(run);
            var registry = world.createCommandRegistryWrapper(RegistryKeys.BLOCK);
            Map<String, BlockState> states = new HashMap<>();
            for (Instruction instruction : instructions) {
                if (run.cancelled) {
                    return;
                }
                target = position(run.origin, instruction);
                if (!world.isInBuildLimit(target) || !world.getWorldBorder().contains(target)) {
                    throw new IOException("Инструкция вне границ мира: " + target);
                }
                if (instruction.action() == Instruction.Action.PLACE && !states.containsKey(instruction.blockState())) {
                    StringReader input = new StringReader(instruction.blockState());
                    BlockState state = BlockArgumentParser.block(registry, input, false).blockState();
                    if (input.canRead() || state.isAir()) {
                        throw new IOException("Некорректное состояние блока: " + instruction.blockState());
                    }
                    states.put(instruction.blockState(), state);
                }
            }
            Map<String, BlockState> parsedStates = Map.copyOf(states);
            client.execute(() -> {
                if (isCurrent(run) && sameSession(run)) {
                    run.instructions = instructions;
                    run.states = parsedStates;
                    run.inFlight = false;
                    messages.send(client, "Строительство начато: " + instructions.size() + " инструкций.", Formatting.GREEN);
                }
            });
        } catch (Exception exception) {
            BlockPos failedAt = target;
            client.execute(() -> fail(run, exception, failedAt));
        }
    }

    private void apply(Run run, Instruction instruction) {
        BlockPos target = run.origin;
        try {
            target = position(run.origin, instruction);
            
            // SafetyAnalyzer check
            if (!SafetyAnalyzer.isSafe(client, target)) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("[AutoSchem] Опасность! Обхожу препятствие.")
                                .formatted(net.minecraft.util.Formatting.RED),
                        false);
                synchronized (run.lock) {
                    run.index++;
                    run.delay = Math.max(0, instruction.delay() - 1);
                    run.inFlight = false;
                }
                return;
            }

            // Move to target
            movement.moveTo(client.player, target);

            // Execute instruction using packets
            executor.execute(instruction, run.origin);

            synchronized (run.lock) {
                run.index++;
                run.delay = Math.max(0, instruction.delay() - 1);
                run.inFlight = false;
                if (run.index >= run.instructions.size()) {
                    finish(run);
                }
            }
        } catch (Exception exception) {
            BlockPos failedAt = target;
            client.execute(() -> fail(run, exception, failedAt));
        }
    }

    private ServerWorld getWorld(Run run) throws IOException {
        if (run.server.isRemote()) {
            throw new IOException("Мир открыт для сетевой игры");
        }
        ServerPlayerEntity player = run.server.getPlayerManager().getPlayer(run.playerId);
        ServerWorld world = run.server.getWorld(run.dimension);
        if (world == null || player == null || player.getEntityWorld() != world) {
            throw new IOException("Одиночный мир или игрок больше недоступны");
        }
        if (world.getDifficulty() != Difficulty.PEACEFUL) {
            throw new IOException("Сложность мира больше не мирная");
        }
        return world;
    }

    private BlockPos position(BlockPos origin, Instruction instruction) {
        return new BlockPos(Math.addExact(origin.getX(), instruction.x()),
                Math.addExact(origin.getY(), instruction.y()), Math.addExact(origin.getZ(), instruction.z()));
    }

    private boolean sameSession(Run run) {
        return client.isInSingleplayer() && client.getServer() == run.server && !run.server.isRemote()
                && client.player != null && client.world != null && client.player.getUuid().equals(run.playerId)
                && client.world.getRegistryKey().equals(run.dimension);
    }

    private boolean isCurrent(Run run) {
        return current == run && !run.cancelled;
    }

    private void finish(Run run) {
        if (!isCurrent(run)) {
            return;
        }
        cancelCurrent();
        messages.send(client, "Строительство завершено.", Formatting.GREEN);
    }

    private void fail(Run run, Throwable exception, BlockPos target) {
        if (!isCurrent(run)) {
            return;
        }
        cancelCurrent();
        errors.report(exception, target);
    }

    private void cancelCurrent() {
        Run run = current;
        if (run != null) {
            synchronized (run.lock) {
                run.cancelled = true;
            }
            if (run.loading != null) {
                run.loading.cancel(true);
            }
            current = null;
        }
    }

    @Override
    public void close() {
        cancelCurrent();
        loader.shutdownNow();
    }

    private static final class Run {
        private final Object lock = new Object();
        private final IntegratedServer server;
        private final UUID playerId;
        private final RegistryKey<World> dimension;
        private final BlockPos origin;
        private volatile boolean cancelled;
        private Future<?> loading;
        private List<Instruction> instructions;
        private Map<String, BlockState> states;
        private int index;
        private int delay;
        private boolean inFlight;

        private Run(IntegratedServer server, UUID playerId, RegistryKey<World> dimension, BlockPos origin) {
            this.server = server;
            this.playerId = playerId;
            this.dimension = dimension;
            this.origin = origin.toImmutable();
        }
    }
}
