package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class LegitHoverTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================
    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Slot hotbar (1-9) dùng để chứa Totem.")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> openDelay = sgGeneral.add(new IntSetting.Builder()
        .name("open-delay")
        .description("Thời gian mở Inventory (ms). Để 0.001 để mở và đóng ngay lập tức.")
        .defaultValue(0)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    // ============================================================
    // STATE
    // ============================================================
    private enum State { IDLE, OPENING, SWAPPING, CLOSING }
    
    private State state = State.IDLE;
    private int timer = 0;

    public LegitHoverTotem() {
        super(HwellAddon.PVP_SUPER_LEGIT, "legit-hover-totem", "Tự động mở túi đồ để lấy Totem xuống hotbar.");
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        int targetIndex = totemSlot.get() - 1;
        ItemStack currentStack = mc.player.getInventory().getStack(targetIndex);

        // Nếu slot đã có Totem thì không làm gì cả
        if (currentStack.getItem() == Items.TOTEM_OF_UNDYING) {
            state = State.IDLE;
            return;
        }

        // Nếu có GUI khác đang mở (như Shop, Chest), chờ người chơi đóng GUI
        // Trừ khi đó là InventoryScreen (đang trong quá trình tự mở)
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        switch (state) {
            case IDLE -> {
                // Bắt đầu mở túi đồ
                state = State.OPENING;
                timer = getTicks();
            }
            case OPENING -> {
                // Mở GUI Inventory
                mc.setScreen(new InventoryScreen(mc.player));
                state = State.SWAPPING;
                timer = 1; // Chờ 1 tick để GUI render xong
            }
            case SWAPPING -> {
                // Tìm Totem trong inventory chính (slot 9 đến 35)
                ScreenHandler handler = mc.player.playerScreenHandler;
                int foundSlot = -1;

                for (int i = 9; i <= 35; i++) {
                    if (handler.getSlot(i).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                        foundSlot = i;
                        break;
                    }
                }

                if (foundSlot != -1) {
                    // Gửi packet swap từ inv xuống hotbar
                    // Button 36 + targetIndex để swap với slot hotbar mục tiêu
                    mc.interactionManager.clickSlot(
                        handler.syncId,
                        foundSlot,
                        36 + targetIndex, // 36 = hotbar bắt đầu, + targetIndex để trỏ đúng slot
                        SlotActionType.SWAP,
                        mc.player
                    );
                }

                state = State.CLOSING;
                timer = getTicks();
            }
            case CLOSING -> {
                // Đóng túi đồ
                if (mc.currentScreen instanceof InventoryScreen) {
                    mc.player.closeHandledScreen();
                }
                state = State.IDLE;
            }
        }
    }

    private int getTicks() {
        if (openDelay.get() <= 0) return 0;
        return Math.max(1, openDelay.get() / 50);
    }
}