package ru.autoschem.movement;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class MovementHandler {
    private static final double MOVE_SPEED = 0.15;
    private static final double ARRIVAL_HORIZONTAL = 0.6;
    private static final double ARRIVAL_VERTICAL = 1.0;
    private static final double STATIONARY_DISTANCE_SQUARED = 0.001;
    private static final int STUCK_TICKS_BEFORE_JUMP = 8;

    private Vec3d lastPosition;
    private int stuckTicks;

    public void smoothLook(ClientPlayerEntity player, Vec3d target, float speed) {
        if (player == null || target == null || speed <= 0.0f) {
            return;
        }
        double deltaX = target.x - player.getX();
        double deltaY = target.y - player.getEyePos().y;
        double deltaZ = target.z - player.getZ();
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontal < 1.0E-6 && Math.abs(deltaY) < 1.0E-6) {
            return;
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(deltaY, horizontal));
        player.setYaw(rotateToward(player.getYaw(), targetYaw, speed));
        player.setPitch(MathHelper.clamp(rotateToward(player.getPitch(), targetPitch, speed), -90.0f, 90.0f));
    }

    public void moveTo(ClientPlayerEntity player, BlockPos target) {
        if (player == null || target == null) {
            return;
        }
        double deltaX = target.getX() + 0.5 - player.getX();
        double deltaY = target.getY() - player.getY();
        double deltaZ = target.getZ() + 0.5 - player.getZ();
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        if (horizontal <= ARRIVAL_HORIZONTAL && Math.abs(deltaY) <= ARRIVAL_VERTICAL) {
            stuckTicks = 0;
            lastPosition = position(player);
            return;
        }
        trackStuck(player);
        if (horizontal > 1.0E-6) {
            double scale = MOVE_SPEED / horizontal;
            player.addVelocity(deltaX * scale, 0.0, deltaZ * scale);
        }
        if (player.isOnGround() && (stuckTicks >= STUCK_TICKS_BEFORE_JUMP || deltaY > 0.5)) {
            player.jump();
            stuckTicks = 0;
        }
    }

    private void trackStuck(ClientPlayerEntity player) {
        Vec3d position = position(player);
        if (lastPosition != null) {
            double movedSquared = position.squaredDistanceTo(lastPosition);
            if (movedSquared < STATIONARY_DISTANCE_SQUARED || player.horizontalCollision) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
        }
        lastPosition = position;
    }

    private float rotateToward(float current, float target, float speed) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + MathHelper.clamp(delta, -speed, speed);
    }

    private Vec3d position(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }
}
