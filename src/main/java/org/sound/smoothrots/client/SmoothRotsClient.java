package org.sound.smoothrots.client;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SmoothRotsClient implements ClientModInitializer {
    public static final String MOD_ID = "smoothrots";
    private static KeyBinding toggleBinding;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(SmoothRotsConfig.class, SmoothRotsConfigSerializer::new);

        toggleBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.smoothrots.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.smoothrots.main"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        while (toggleBinding.wasPressed()) {
            SmoothRotsConfig config = SmoothRotsConfig.get();
            config.enabled = !config.enabled;
            AutoConfig.getConfigHolder(SmoothRotsConfig.class).save();
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        SmoothRotsConfig config = SmoothRotsConfig.get();
        if (!config.enabled) {
            return;
        }

        RotationTarget target = resolveTarget(client, player, config);
        if (target == null) {
            return;
        }

        applyRotation(player, target, config);
    }

    private RotationTarget resolveTarget(MinecraftClient client, ClientPlayerEntity player, SmoothRotsConfig config) {
        if (config.mode == RotationMode.STRICT_DOWN) {
            return new RotationTarget(player.getYaw(), MathHelper.clamp(config.downPitch, -90.0F, 90.0F));
        }

        if (client.crosshairTarget == null) {
            return config.fallbackToDown
                    ? new RotationTarget(player.getYaw(), MathHelper.clamp(config.downPitch, -90.0F, 90.0F))
                    : null;
        }

        Vec3d eyes = player.getEyePos();
        Vec3d targetPos = client.crosshairTarget.getPos();
        Vec3d delta = targetPos.subtract(eyes);

        if (delta.lengthSquared() < 1.0E-6D) {
            return null;
        }

        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0F / Math.PI)) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(delta.y, horizontal) * (180.0F / Math.PI)));
        return new RotationTarget(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F));
    }

    private void applyRotation(ClientPlayerEntity player, RotationTarget target, SmoothRotsConfig config) {
        float currentYaw = player.getYaw();
        float currentPitch = player.getPitch();

        float nextYaw;
        float nextPitch;

        if (config.strictLock) {
            nextYaw = target.yaw();
            nextPitch = target.pitch();
        } else {
            float yawDelta = MathHelper.wrapDegrees(target.yaw() - currentYaw);
            float pitchDelta = target.pitch() - currentPitch;
            float speed = Math.max(config.smoothingPerTick, 0.01F);

            nextYaw = currentYaw + MathHelper.clamp(yawDelta, -speed, speed);
            nextPitch = currentPitch + MathHelper.clamp(pitchDelta, -speed, speed);
        }

        nextPitch = MathHelper.clamp(nextPitch, -90.0F, 90.0F);

        player.setYaw(nextYaw);
        player.setPitch(nextPitch);
        player.setHeadYaw(nextYaw);
        player.setBodyYaw(nextYaw);

        if (config.lockCamera) {
            player.prevYaw = nextYaw;
            player.prevPitch = nextPitch;
            player.prevHeadYaw = nextYaw;
            player.prevBodyYaw = nextYaw;
        }
    }

    private record RotationTarget(float yaw, float pitch) {
    }
}
