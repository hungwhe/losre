package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotemProMax extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> health = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Máu còn lại để tự động trang bị totem.")
        .defaultValue(10.0)
        .range(0.0, 50.0)
        .noSlider()
        .build()
    );

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("Thời gian chờ (ms) giữa các lần trang bị totem.")
        .defaultValue(200.0)
        .range(0.0, 1000.0)
        .noSlider()
        .build()
    );

    private final Setting<Boolean> workInShop = sgGeneral.add(new BoolSetting.Builder()
        .name("work-in-shop")
        .description("Cho phép auto totem hoạt động khi mở shop.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;

    public AutoTotemProMax() {
        super(HwellAddon.PVP, "auto-totem-pro-max",
            "Tự động trang bị totem. CHỈ lấy từ túi bạn, không lấy từ shop.");
    }

    @Override
    public void onActivate() {
        cooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Offhand đã có totem → không làm gì
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        // Check ngưỡng máu
        double currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (currentHealth > health.get()) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean guiOpen = handler != mc.player.playerScreenHandler;

        // Nếu đang mở shop và workInShop = false → không làm gì
        if (guiOpen && !workInShop.get()) return;

        // === Bước 1: Tìm totem trong TÚI BẠN (mc.player.getInventory) ===
        int invSlot = findTotemInPlayerInv();
        if (invSlot == -1) return; // Không có totem trong túi → bó tay

        // === Bước 2: Map slot túi bạn → slot trong GUI hiện tại ===
        int guiSlot = mapInvSlotToGuiSlot(handler, invSlot);
        if (guiSlot == -1) return; // Không map được

        // === Bước 3: Gửi packet swap với offhand (button 40) ===
        mc.interactionManager.clickSlot(
            handler.syncId,
            guiSlot,
            40,                  // Button 40 = swap với offhand
            SlotActionType.SWAP,
            mc.player
        );

        cooldownTicks = (int) Math.round(delay.get() / 50.0);
    }

    /**
     * Tìm totem trong túi đồ thật của bạn (36 slot chính: 0-35).
     */
    private int findTotemInPlayerInv() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Map slot túi đồ của bạn (0-35) sang slot index trong GUI hiện tại.
     *
     * Cách an toàn: so khớp ItemStack identity giữa slot GUI và slot túi bạn.
     * Nếu không khớp được (do item trùng nhau), dùng heuristic layout.
     *
     * @param handler ScreenHandler hiện tại
     * @param invSlot slot trong mc.player.getInventory() (0-35)
     * @return slot index trong GUI (dùng cho clickSlot), hoặc -1 nếu không tìm thấy.
     */
    private int mapInvSlotToGuiSlot(ScreenHandler handler, int invSlot) {
        ItemStack invStack = mc.player.getInventory().getStack(invSlot);

        // === Cách 1: So khớp ItemStack reference (nhanh, chính xác) ===
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.hasStack()) continue;
            if (slot.getStack() == invStack) {
                return i;
            }
        }

        // === Cách 2: Fallback - dùng heuristic layout ===
        // Nếu GUI đang mở là shop:
        //   totalSlots = shopSlots + 36 (inv) [+ 4 armor + 1 offhand nếu có]
        // Thử cả 2 layout:
        int totalSlots = handler.slots.size();

        // Layout A: có armor + offhand (chuẩn vanilla)
        int playerInvStartA = totalSlots - 41;
        if (playerInvStartA >= 0) {
            int candidate = playerInvStartA + invSlot;
            if (candidate < totalSlots && handler.getSlot(candidate).getStack() == invStack) {
                return candidate;
            }
        }

        // Layout B: không có armor + offhand (nhiều server VN)
        int playerInvStartB = totalSlots - 36;
        if (playerInvStartB >= 0) {
            int candidate = playerInvStartB + invSlot;
            if (candidate < totalSlots && handler.getSlot(candidate).getStack() == invStack) {
                return candidate;
            }
        }

        // === Cách 3: Fallback cuối - dùng Layout B cho shop kiểu VN ===
        // Dựa vào ảnh bạn gửi: shop 6 hàng, KHÔNG có armor/offhand
        if (totalSlots == 54) {
            // 27 slot shop + 27 slot túi chính
            if (invSlot < 27) {
                return 27 + invSlot;
            } else {
                // Hotbar: slot 36-44 trong player inv → slot 54-9=45 đến 53
                return invSlot - 27 + 45;
            }
        }

        return -1;
    }
}