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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Random;

public class AutoFillTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================
    private final Setting<Integer> preferredSlot = sgGeneral.add(new IntSetting.Builder()
        .name("preferred-slot")
        .description("Slot hotbar ưu tiên chứa totem (1-9).")
        .defaultValue(8)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Double> fillDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-delay")
        .description("Delay (ms) giữa các lần fill totem.")
        .defaultValue(200.0)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build()
    );

    private final Setting<Boolean> workInShop = sgGeneral.add(new BoolSetting.Builder()
        .name("work-in-shop")
        .description("Vẫn fill totem khi đang mở shop.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fillOnlyPreferred = sgGeneral.add(new BoolSetting.Builder()
        .name("fill-only-preferred")
        .description("Chỉ fill totem vào slot preferred. Nếu tắt, fill bất kỳ slot hotbar trống.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxTotemsInHotbar = sgGeneral.add(new IntSetting.Builder()
        .name("max-totems-in-hotbar")
        .description("Số totem tối đa trong hotbar. Đặt 0 để không giới hạn.")
        .defaultValue(1)
        .range(0, 9)
        .sliderRange(0, 9)
        .build()
    );

    // ============================================================
    // STATE
    // ============================================================
    private int cooldownTicks;
    private final Random random = new Random();

    public AutoFillTotem() {
        super(HwellAddon.PVP, "auto-fill-totem",
            "Tự động lấy totem từ inventory xuống hotbar. Vẫn hoạt động trong shop.");
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

        // === KIỂM TRA SHOP ===
        ScreenHandler handler = mc.player.currentScreenHandler;
        boolean guiOpen = handler != mc.player.playerScreenHandler;

        if (guiOpen && !workInShop.get()) {
            return; // Đang mở GUI và tắt work-in-shop
        }

        // === KIỂM TRA SỐ TOTEM TRONG HOTBAR ===
        int currentTotemsInHotbar = countTotemsInHotbar();
        int maxAllowed = maxTotemsInHotbar.get();

        if (maxAllowed > 0 && currentTotemsInHotbar >= maxAllowed) {
            return; // Đã đủ totem trong hotbar
        }

        // === TÌM SLOT CẦN FILL ===
        int targetSlot = findTargetSlot();
        if (targetSlot == -1) return; // Không có slot cần fill

        // === TÌM TOTEM TRONG INVENTORY ===
        int totemSlot = findTotemInInventory();
        if (totemSlot == -1) return; // Không có totem trong inventory

        // === FILL TOTEM ===
        boolean didFill = fillTotem(handler, totemSlot, targetSlot);

        if (didFill) {
            cooldownTicks = getRandomDelay(fillDelay.get());
        }
    }

    /**
     * Tìm slot hotbar cần fill.
     * @return slot index (0-8), hoặc -1 nếu không có.
     */
    private int findTargetSlot() {
        int preferred = preferredSlot.get() - 1;

        if (fillOnlyPreferred.get()) {
            // Chỉ fill vào slot preferred nếu slot đó trống
            if (mc.player.getInventory().getStack(preferred).isEmpty()) {
                return preferred;
            }
            return -1;
        } else {
            // Fill vào bất kỳ slot hotbar trống
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isEmpty()) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * Tìm slot totem trong inventory chính (9-35).
     * KHÔNG tìm trong hotbar (0-8).
     * @return slot index (9-35), hoặc -1 nếu không tìm thấy.
     */
    private int findTotemInInventory() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Đếm số totem trong hotbar (0-8).
     */
    private int countTotemsInHotbar() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Fill totem từ inventory xuống hotbar slot.
     * Dùng clickSlot với SlotActionType.SWAP (button = slot hotbar 0-8).
     *
     * @param handler ScreenHandler hiện tại
     * @param invSlot slot inventory (9-35)
     * @param hotbarSlot slot hotbar (0-8)
     * @return true nếu fill thành công
     */
    private boolean fillTotem(ScreenHandler handler, int invSlot, int hotbarSlot) {
        try {
            if (handler == mc.player.playerScreenHandler) {
                // Không mở GUI: dùng playerScreenHandler
                // Slot inventory chính trong playerScreenHandler = invSlot
                // Button = hotbarSlot (0-8) để swap
                mc.interactionManager.clickSlot(
                    handler.syncId,
                    invSlot,        // Slot totem trong inventory
                    hotbarSlot,     // Button = slot hotbar mục tiêu (0-8)
                    SlotActionType.SWAP,
                    mc.player
                );
                return true;
            } else {
                // Đang mở shop: dùng shop container
                // Cần tính slot của inventory trong shop container
                int totalSlots = handler.slots.size();
                int playerInvStart = totalSlots - 41;
                if (playerInvStart < 0) playerInvStart = 0;

                // Slot inventory chính (9-35) trong playerScreenHandler tương ứng
                // slot nào trong shop container?
                // Layout shop: [shop slots] [9 hotbar] [27 inv chính] [4 armor] [1 offhand]
                // Nhưng layout có thể khác.
                //
                // Cách chắc chắn: dùng reference so sánh
                int shopSlot = findSlotByReference(handler, mc.player.getInventory().getStack(invSlot));
                if (shopSlot == -1) return false;

                mc.interactionManager.clickSlot(
                    handler.syncId,
                    shopSlot,
                    hotbarSlot,     // Button = slot hotbar mục tiêu
                    SlotActionType.SWAP,
                    mc.player
                );
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tìm slot trong handler có cùng ItemStack reference với stack cho trước.
     * Đây là cách chắc chắn nhất để map slot giữa các container khác nhau.
     */
    private int findSlotByReference(ScreenHandler handler, ItemStack target) {
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack slotStack = handler.getSlot(i).getStack();
            if (slotStack == target) return i;
        }
        return -1;
    }

    private int getRandomDelay(double baseDelayMs) {
        if (baseDelayMs <= 0) return 0;
        double jitter = baseDelayMs * 0.2 * (random.nextDouble() * 2 - 1);
        return Math.max(0, (int) Math.round((baseDelayMs + jitter) / 50.0));
    }
}