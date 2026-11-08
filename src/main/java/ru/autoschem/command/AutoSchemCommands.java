package ru.autoschem.command;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import ru.autoschem.build.BuildController;
import ru.autoschem.log.ErrorLogger;

public final class AutoSchemCommands {
    private AutoSchemCommands() {
    }

    public static void register(BuildController builder, ErrorLogger errors) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("autoschem")
                        .then(literal("start").executes(context -> {
                            try {
                                return builder.start();
                            } catch (Exception exception) {
                                errors.report(exception);
                                return 0;
                            }
                        }))
                        .then(literal("stop").executes(context -> {
                            try {
                                return builder.stop();
                            } catch (Exception exception) {
                                errors.report(exception);
                                return 0;
                            }
                        }))));
    }
}
