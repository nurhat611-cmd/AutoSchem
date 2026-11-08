package ru.autoschem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import ru.autoschem.build.BuildController;
import ru.autoschem.client.ChatMessages;
import ru.autoschem.command.AutoSchemCommands;
import ru.autoschem.log.ErrorLogger;
import ru.autoschem.safety.SafetyHandler;
import ru.autoschem.scheme.ResourceCalculator;
import ru.autoschem.vsch.VschStore;
import ru.autoschem.watch.SchemeWatcher;

public final class AutoSchemClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        Path config = FabricLoader.getInstance().getConfigDir().resolve("autoschem");
        Path schemes = config.resolve("schemes");
        Path vsch = config.resolve("vsch");
        ChatMessages messages = new ChatMessages();
        ErrorLogger errors = new ErrorLogger(config, messages);
        SafetyHandler safety = new SafetyHandler(messages);
        VschStore store = new VschStore(vsch);
        BuildController builder = new BuildController(client, safety, messages, errors, store);
        ResourceCalculator calculator = new ResourceCalculator();
        SchemeWatcher watcher = new SchemeWatcher(schemes, store,
                result -> messages.enqueue("Схема обработана. Требуется: " + calculator.format(result.resources())
                        + ". Готово к запуску.", Formatting.GREEN),
                (path, error) -> errors.report(error));

        try {
            Files.createDirectories(vsch);
            watcher.start();
        } catch (Exception exception) {
            errors.report(exception);
        }

        AutoSchemCommands.register(builder, errors);
        ClientTickEvents.END_CLIENT_TICK.register(tickingClient -> {
            try {
                errors.updatePosition(tickingClient.player == null ? null : tickingClient.player.getBlockPos());
                messages.flush(tickingClient);
                builder.tick();
            } catch (Exception exception) {
                builder.disconnected();
                errors.report(exception);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, joiningClient) -> {
            if (!joiningClient.isInSingleplayer()) {
                safety.warn(joiningClient);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, disconnectingClient) -> builder.disconnected());
        ClientLifecycleEvents.CLIENT_STOPPING.register(stoppingClient -> {
            builder.close();
            try {
                watcher.close();
            } catch (IOException exception) {
                errors.report(exception);
            }
        });
    }
}
