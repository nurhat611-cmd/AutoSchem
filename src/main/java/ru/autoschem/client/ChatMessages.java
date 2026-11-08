package ru.autoschem.client;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatMessages {
    private final Queue<Message> pending = new ConcurrentLinkedQueue<>();

    public void enqueue(String message, Formatting color) {
        pending.add(new Message(message, color));
    }

    public void flush(MinecraftClient client) {
        if (client.player == null || !client.isInSingleplayer()) {
            return;
        }
        Message message;
        while ((message = pending.poll()) != null) {
            send(client, message.text(), message.color());
        }
    }

    public void send(MinecraftClient client, String message, Formatting color) {
        Text text = Text.literal("[AutoSchem] " + message).formatted(color);
        if (client.player != null) {
            client.player.sendMessage(text, false);
        } else {
            client.inGameHud.getChatHud().addMessage(text);
        }
    }

    private record Message(String text, Formatting color) {
    }
}
