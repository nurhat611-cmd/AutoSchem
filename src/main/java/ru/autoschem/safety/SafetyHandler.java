package ru.autoschem.safety;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import ru.autoschem.client.ChatMessages;

public final class SafetyHandler {
    private final ChatMessages messages;

    public SafetyHandler(ChatMessages messages) {
        this.messages = messages;
    }

    public boolean check(MinecraftClient client) {
        if (!client.isInSingleplayer() || (client.getServer() != null && client.getServer().isRemote())) {
            warn(client);
            return false;
        }
        if (client.player == null || client.world == null || client.getServer() == null) {
            messages.send(client, "Сначала откройте одиночный мир.", Formatting.RED);
            return false;
        }
        return true;
    }

    public void warn(MinecraftClient client) {
        messages.send(client, "Мод отключен на серверах.", Formatting.RED);
    }
}
