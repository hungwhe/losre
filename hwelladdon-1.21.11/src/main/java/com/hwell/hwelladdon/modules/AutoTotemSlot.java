package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoTotemSlot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health")
        .description("Ngưỡng máu để chuyển slot totem.")
        .defaultValue(20.0)
        .range(0.0, 50.0)
        .sliderRange(0.0, 50.0)
        .build()
    );

    private final Setting<Integer> preferredSlot = sgGeneral.add(new IntSetting.Builder()
        .name("preferred-slot")
        .description("Slot hotbar ưu tiên (1-9).")
        .defaultValue(8)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Boolean> inventorySafety = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-safety")
        .description("Khi mở inventory, chuyển sang slot totem.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("only-combat")
        .description("Chỉ hoạt động khi có địch.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> switchDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("switch-delay")
        .description("Delay (ms) giữa các lần chuyển slot.")
        .defaultValue(100.0)
        .range(0.0, 500.0)
        .sliderRange(0.0, 500.0)
        .build()
    );

    private int cooldownTicks;

    public AutoTotemSlot() {
        super(HwellAddon.PVP, "auto-totem-slot",
            "Chuyển hotbar slot sang totem. KHÔNG lấy item từ inventory.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // === INVENTORY MỞ ===
        if (inventorySafety.get() && mc.currentScreen instanceof InventoryScreen) {
            int totemSlot = findTotemInHotbar();
            if (totemSlot != -1) {
                int current = mc.player.getInventory().getSelectedSlot();
                if (current != totemSlot) {
                    mc.player.getInventory().setSelectedSlot(totemSlot);
                    cooldownTicks = getDelayTicks();
                }
            }
            // Nếu không có totem trong hotbar → KHÔNG làm gì
            return;
        }

        // === COMBAT CHECK ===
        if (onlyCombat.get() && !isInCombat()) return;

        // === MÁU CHECK ===
        double hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hp > health.get()) return;

        // === SLOT HIỆN TẠI CÓ TOTEM? ===
        int currentSlot = mc.player.getInventory().getSelectedSlot();
        if (isTotem(mc.player.getInventory().getStack(currentSlot))) {
            return; // Đang cầm totem → OK
        }

        // === TÌM VÀ CHUYỂN SANG SLOT TOTEM ===
        int totemSlot = findTotemInHotbar();
        if (totemSlot == -1) return; // Không có totem → không làm gì

        mc.player.getInventory().setSelectedSlot(totemSlot);
        cooldownTicks = getDelayTicks();
    }

    /**
     * Tìm slot hotbar (0-8) có totem.
     * Ưu tiên preferredSlot.
     * KHÔNG tìm trong inventory chính (9-35).
     */
    private int findTotemInHotbar() {
        int preferred = preferredSlot.get() - 1;

        // 1. Check preferred slot
        if (isTotem(mc.player.getInventory().getStack(preferred))) {
            return preferred;
        }

        // 2. Dò slot 0-8
        for (int i = 0; i < 9; i++) {
            if (i == preferred) continue;
            if (isTotem(mc.player.getInventory().getStack(i))) {
                return i;
            }
        }

        // 3. KHÔNG tìm trong inventory (9-35) → trả về -1
        return -1;
    }

    private boolean isTotem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private boolean isInCombat() {
        if (mc.world == null) return false;
        for (var entity : mc.world.getPlayers()) {
            if (entity != mc.player && mc.player.distanceTo(entity) < 6.0) {
                return true;
            }
        }
        return false;
    }

    private int getDelayTicks() {
        double delayMs = switchDelay.get();
        if (delayMs <= 0) return 0;
        return Math.max(1, (int) Math.round(delayMs / 50.0));
    }
}