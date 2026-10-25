package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class HwellDropOrder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelays = settings.createGroup("Delays");

    // ============================================================
    // SETTINGS
    // ============================================================
    private final Setting<String> itemId = sgGeneral.add(new StringSetting.Builder()
        .name("item-id")
        .description("ID của item cần dò (VD: minecraft:sand).")
        .defaultValue("minecraft:sand")
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

    private final Setting<Double> delayAfterSlot52 = sgDelays.add(new DoubleSetting.Builder()
        .name("delay-after-slot51")
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
    // Tọa độ cố định
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
    
    // Đánh dấu đã click item chưa (chỉ click 1 lần)
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
                    // Click vào rương (Slot 51) để mở GUI Order
                    if (OPEN_GUI_SLOT < handler.slots.size()) {
                        mc.interactionManager.clickSlot(
                            handler.syncId, OPEN_GUI_SLOT, 0, SlotActionType.PICKUP, mc.player
                        );
                        state = State.WAITING_ORDER_GUI;
                        timer = getDelayTicks(delayAfterSlot52.get());
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

                    String target = itemId.get().toLowerCase().trim();
                    int foundSlot = -1;

                    // Dò item
                    for (int i = 0; i < playerInvStart; i++) {
                        ItemStack stack = handler.getSlot(i).getStack();
                        if (stack.isEmpty()) continue;

                        Identifier id = Registries.ITEM.getId(stack.getItem());
                        if (id.toString().equals(target) || id.getPath().equals(target)) {
                            foundSlot = i;
                            break;
                        }
                    }

                    if (foundSlot != -1) {
                        // CLICK ITEM (Chỉ 1 lần)
                        mc.interactionManager.clickSlot(
                            handler.syncId, foundSlot, 0, SlotActionType.PICKUP, mc.player
                        );
                        
                        hasClickedItem = true; 
                        sendMsg("Đã click item tại Slot " + foundSlot + ". Chuẩn bị click xác nhận...");
                        
                        state = State.CLICKING_CONFIRM; 
                        timer = getDelayTicks(delayAfterItemClick.get());
                        clicksLeft = clickAmount.get();
                        
                    } else {
                        sendMsg("khong tim thay item trong order ban da dat");
                        toggle();
                    }
                }
            }

            case CLICKING_CONFIRM -> {
                if (guiOpen && clicksLeft > 0 && hasClickedItem) {
                    if (CONFIRM_SLOT < handler.slots.size()) {
                        // Click vào nút Vứt (Slot 15)
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