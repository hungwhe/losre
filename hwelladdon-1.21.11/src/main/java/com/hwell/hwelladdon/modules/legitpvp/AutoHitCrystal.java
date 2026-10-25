package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;

public class AutoHitCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Khoảng cách tối đa để đập crystal.")
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay giữa các lần đập crystal (ms).")
        .defaultValue(0)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Tự động quay đầu về phía crystal trước khi đập.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;

    public AutoHitCrystal() {
        super(HwellAddon.PVP_SUPER_LEGIT, "auto-hit-crystal", "Tự động đập crystal ngay khi nó xuất hiện.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        EndCrystalEntity targetCrystal = null;
        double bestDistance = range.get();

        // Quét tất cả các thực thể trong thế giới
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal) {
                if (!crystal.isAlive()) continue;

                double dist = mc.player.distanceTo(crystal);
                if (dist <= bestDistance) {
                    bestDistance = dist;
                    targetCrystal = crystal;
                }
            }
        }

        // Nếu tìm thấy crystal trong tầm
        if (targetCrystal != null) {
            // Quay đầu về phía crystal
            if (rotate.get()) {
                float yaw = (float) Math.toDegrees(Math.atan2(targetCrystal.getZ() - mc.player.getZ(), targetCrystal.getX() - mc.player.getX())) - 90;
                float pitch = (float) -Math.toDegrees(Math.atan2(targetCrystal.getY() + 0.5 - mc.player.getEyeY(), Math.sqrt(Math.pow(targetCrystal.getX() - mc.player.getX(), 2) + Math.pow(targetCrystal.getZ() - mc.player.getZ(), 2))));
                Rotations.rotate(yaw, pitch);
            }

            // Đập crystal
            mc.interactionManager.attackEntity(mc.player, targetCrystal);
            mc.player.swingHand(Hand.MAIN_HAND);

            // Đặt lại cooldown
            cooldownTicks = getTicks();
        }
    }

    private int getTicks() {
        if (delay.get() <= 0) return 0;
        return Math.max(1, delay.get() / 50);
    }
}