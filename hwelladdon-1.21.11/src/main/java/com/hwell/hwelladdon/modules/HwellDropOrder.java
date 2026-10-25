package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class HwellDropOrder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Delays");

    // ============================================================
    // SETTINGS
    // ============================================================
    // ✅ Đổi StringSetting -> ItemSetting (chọn từ GUI)
    private final Setting<Item> targetItem = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("Chọn item cần dò trong GUI Order.")
        .defaultValue(Items.SAND)
        .build()
    );

    private final Setting<Integer> clickAmount = sgGeneral.add(new IntSetting.Builder()
        .name("click-confirm-amount")
        .description("Số lần click vào ô xác nhận.")
        .defaultValue(100)
        .range(1, 1000000)
        .noSlider()
        .build()
    );

    // Delays
    private final Setting<Double> delayAfterOrder = sgDelays.add(new DoubleSetting.Builder()
        .name("delay-after-order")
        .description("Thời gian chờ (ms) sau khi gõ /order.")
        .defaultValue(1000.0)
        .range(0.0, 3000.0)
        .sliderRange(0.0, 3000.0)
        .build()
    );

    private final Setting<Double> delayAfterOpenGui = sgDelays.add(new DoubleSetting.Builder()
        .name("delay-after-open-gui")
        .description("Thời gian chờ (ms) sau khi click ô mở GUI (Slot 51).")
        .defaultValue(1000.0)
        .range(0.0, 3000.0)
        .sliderRange(0.0, 3000.0)
        .build()
    );

    private final Setting<Double> delayAfterItemClick = sgDelays.add(new DoubleSetting.Builder()
        .name("delay-after-item")
        .description("Thời gian chờ (ms) sau khi click vào item tìm thấy.")
        .defaultValue(1000.0)
        .range(0.0, 3000.0)
        .sliderRange(0.0, 3000.0)
        .build()
    );

    private final Setting<Double> delayBetweenClicks = sgDelays.add(new DoubleSetting.Builder()
        .name("delay-between-clicks")
        .description("Thời gian chờ (ms) giữa mỗi lần click confirm.")
        .defaultValue(100.0)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build()
    );

    // ============================================================
    // STATE & HARDCODE
    // ============================================================
    private static final int OPEN_GUI_SLOT = 51;  // Hàng 6, Cột 7 (Cái rương)
    private static final int CONFIRM_SLOT = 15;   // Hàng 2, Cột 7 (Nút Vứt đồ)

    private enum State {
        SENDING_CMD,
        WAITING_MAIN_GUI,
        WAITING_ORDER_GUI,
        CLICKING_CONFIRM,
        FINISHING
    }

    private State state = State.SENDING_CMD;
    private int timer = 0;
    private int clicksLeft = 0;
    private int closeTicks = 0;
    private boolean hasClickedItem = false;

    public HwellDropOrder() {
        super(HwellAddon.FARM, "hwell-drop-order",
            "Tự động dò item trong GUI Order và click vứt đồ.");
    }

    @Override
    public void onActivate() {
        state = State.SENDING_CMD;
        timer = 0;
        clicksLeft = 0;
        closeTicks = 0;
        hasClickedItem = false;
    }

    @Override
    public void onDeactivate() {
        state = State.SENDING_CMD;
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (state != State.SENDING_CMD && state != State.FINISHING && mc.currentScreen == null) {
            sendMsg("Đã đóng GUI, tự động tắt module.");
            toggle();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean guiOpen = handler != mc.player.playerScreenHandler;

        switch (state) {
            case SENDING_CMD -> {
                mc.player.networkHandler.sendChatCommand("order");
                state = State.WAITING_MAIN_GUI;
                timer = getDelayTicks(delayAfterOrder.get());
            }

            case WAITING_MAIN_GUI -> {
                if (guiOpen && mc.currentScreen instanceof HandledScreen<?>) {
                    if (OPEN_GUI_SLOT < handler.slots.size()) {
                        mc.interactionManager.clickSlot(
                            handler.syncId, OPEN_GUI_SLOT, 0, SlotActionType.PICKUP, mc.player
                        );
                        state = State.WAITING_ORDER_GUI;
                        timer = getDelayTicks(delayAfterOpenGui.get());
                    } else {
                        sendMsg("Không tìm thấy rương ở Slot " + OPEN_GUI_SLOT);
                        toggle();
                    }
                }
            }

            case WAITING_ORDER_GUI -> {
                if (guiOpen) {
                    int totalSlots = handler.slots.size();
                    int playerInvStart = totalSlots - 36;
                    if (playerInvStart < 0) playerInvStart = totalSlots;

                    Item target = targetItem.get();
                    int foundSlot = -1;

                    // ✅ So sánh trực tiếp Item reference
                    for (int i = 0; i < playerInvStart; i++) {
                        ItemStack stack = handler.getSlot(i).getStack();
                        if (stack.isEmpty()) continue;

                        if (stack.getItem() == target) {
                            foundSlot = i;
                            break;
                        }
                    }

                    if (foundSlot != -1) {
                        mc.interactionManager.clickSlot(
                            handler.syncId, foundSlot, 0, SlotActionType.PICKUP, mc.player
                        );

                        hasClickedItem = true;
                        sendMsg("Đã click item tại Slot " + foundSlot + ". Chuẩn bị click xác nhận...");

                        state = State.CLICKING_CONFIRM;
                        timer = getDelayTicks(delayAfterItemClick.get());
                        clicksLeft = clickAmount.get();
                    } else {
                        sendMsg("Không tìm thấy item trong order bạn đã đặt.");
                        toggle();
                    }
                }
            }

            case CLICKING_CONFIRM -> {
                if (guiOpen && clicksLeft > 0 && hasClickedItem) {
                    if (CONFIRM_SLOT < handler.slots.size()) {
                        mc.interactionManager.clickSlot(
                            handler.syncId, CONFIRM_SLOT, 0, SlotActionType.PICKUP, mc.player
                        );
                        clicksLeft--;
                        timer = getDelayTicks(delayBetweenClicks.get());
                    } else {
                        sendMsg("Không tìm thấy nút vứt đồ (Slot 15)!");
                        toggle();
                    }
                }

                if (clicksLeft <= 0) {
                    sendMsg("Đã hoàn tất order. Đang thoát GUI...");
                    state = State.FINISHING;
                    closeTicks = 5;
                    timer = 2;
                }
            }

            case FINISHING -> {
                if (closeTicks > 0) {
                    if (mc.currentScreen != null) {
                        mc.player.closeHandledScreen();
                    }
                    closeTicks--;
                    timer = 2;
                } else {
                    toggle();
                }
            }
        }
    }

    private void sendMsg(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§c[hwelladdon] §6" + message), false);
        }
    }

    private int getDelayTicks(double delayMs) {
        if (delayMs <= 0.001) return 0;
        return (int) Math.round(delayMs / 50.0);
    }
}