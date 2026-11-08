package ru.autoschem.vsch;

import java.util.Objects;

public record Instruction(Action action, int x, int y, int z, String blockState, int delay) {
    public static final int MAX_STATE_LENGTH = 4096;
    public static final int MAX_DELAY = 72_000;

    public Instruction {
        Objects.requireNonNull(action);
        Objects.requireNonNull(blockState);
        if (blockState.isBlank() || blockState.length() > MAX_STATE_LENGTH || delay < 0 || delay > MAX_DELAY) {
            throw new IllegalArgumentException("Недопустимая инструкция VSCH");
        }
    }

    public enum Action {
        PLACE,
        BREAK
    }
}
