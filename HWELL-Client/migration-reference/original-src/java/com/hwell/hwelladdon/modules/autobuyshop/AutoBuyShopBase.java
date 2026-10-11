package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class AutoBuyShopBase extends Module {
    // ============================================================
    // STATIC REGISTRY
    // ============================================================
    private static final List<AutoBuyShopBase> ALL = new ArrayList<>();
    private static AutoBuyShopBase active = null;

    public static AutoBuyShopBase getActive() { return active; }
    public static boolean isAnyActive() { return active != null && active.isActive(); }

    public static void checkAllAutoEnable() {
        for (AutoBuyShopBase m : ALL) {
            if (!m.isActive()) m.checkAutoEnable();
        }
    }

    // ============================================================
    // SETTINGS
    // ============================================================
    protected final SettingGroup sgGeneral = settings.getDefaultGroup();
    protected final SettingGroup sgDelay = settings.createGroup("Delay");
    protected final SettingGroup sgSilent = settings.createGroup("Silent Mode");

    protected final Setting<String> shopCommand = sgGeneral.add(new StringSetting.Builder()
        .name("shop-command").description("Lệnh mở shop.")
        .defaultValue("/shop").build());

    protected final Setting<String> targetItem = sgGeneral.add(new StringSetting.Builder()
        .name("target-item")
        .description("Tên item để đếm (VD: totem, crystal, pearl).")
        .defaultValue("totem")
        .build());

    protected final Setting<Integer> firstClickSlot;
    protected final Setting<Integer> secondClickSlot;
    protected final Setting<Integer> confirmClickSlot;

    // ============================================================
    // DELAY SETTINGS (5 delay riêng biệt)
    // ============================================================
    protected final Setting<Double> shopOpenDelay = sgDelay.add(new DoubleSetting.Builder()
        .name("shop-open-delay")
        .description("Delay chờ GUI shop mở (ms). 0.001 = ngay tick sau.")
        .defaultValue(0.001)
        .range(0.0, 3000.0)
        .sliderRange(0.0, 3000.0)
        .build());

    protected final Setting<Double> delayFirst = sgDelay.add(new DoubleSetting.Builder()
        .name("delay-first")
        .description("Delay trước click FIRST (sau khi shop mở).")
        .defaultValue(0.001)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build());

    protected final Setting<Double> delayBetween = sgDelay.add(new DoubleSetting.Builder()
        .name("delay-between-first-second")
        .description("Delay giữa click FIRST và click SECOND (ms).")
        .defaultValue(0.001)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build());

    protected final Setting<Double> delaySecond = sgDelay.add(new DoubleSetting.Builder()
        .name("delay-second")
        .description("Delay giữa click SECOND và click CONFIRM (ms).")
        .defaultValue(0.001)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build());

    protected final Setting<Double> delayConfirm = sgDelay.add(new DoubleSetting.Builder()
        .name("delay-confirm")
        .description("Delay giữa click CONFIRM và click FIRST tiếp theo (ms).")
        .defaultValue(0.001)
        .range(0.0, 1000.0)
        .sliderRange(0.0, 1000.0)
        .build());

    // ============================================================
    // GENERAL SETTINGS
    // ============================================================
    protected final Setting<Integer> maxItemAmount = sgGeneral.add(new IntSetting.Builder()
        .name("max-item-amount")
        .description("Số item tối đa (1 stack = 64). 0 = đến khi đầy.")
        .defaultValue(64)
        .range(0, 100000)
        .sliderRange(0, 10000)
        .build());

    protected final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable").description("Tự tắt khi mua xong.")
        .defaultValue(true).build());

    protected final Setting<Boolean> autoEnable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enable").description("Tự bật khi item ≤ ngưỡng.")
        .defaultValue(false).build());

    protected final Setting<Integer> autoEnableThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("auto-enable-threshold").description("Ngưỡng item để tự bật.")
        .defaultValue(10).range(0, 10000).sliderRange(0, 1000)
        .visible(autoEnable::get).build());

    // ============================================================
    // SILENT MODE
    // ============================================================
    protected final Setting<Boolean> silentMode = sgSilent.add(new BoolSetting.Builder()
        .name("silent-mode").description("⚠️ Mua không hiện GUI.")
        .defaultValue(false).build());

    protected final Setting<Boolean> freezeWhileBuying = sgSilent.add(new BoolSetting.Builder()
        .name("freeze-while-buying").description("Đứng yên khi đang mua.")
        .defaultValue(true).visible(silentMode::get).build());

    // ============================================================
    // STATE
    // ============================================================
    protected enum State { OPENING_SHOP, FIRST_CLICK, SECOND_CLICK, CONFIRMING, FINISHED }

    protected State state = State.OPENING_SHOP;
    protected int clickCooldownTicks;
    protected int totalBought;
    protected boolean shouldDisable;
    protected int initialItemCount;
    protected boolean shopCommandSent = false;
    protected final Random random = new Random();

    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public AutoBuyShopBase(String name, String description,
                          int firstDefault, int secondDefault, int confirmDefault) {
        super(HwellAddon.AUTO_SHOP, name, description);
        ALL.add(this);

        this.firstClickSlot = sgGeneral.add(new IntSetting.Builder()
            .name("first-click-slot").description("Slot click GUI 1.")
            .defaultValue(firstDefault).range(0, 53).sliderRange(0, 53).build());

        this.secondClickSlot = sgGeneral.add(new IntSetting.Builder()
            .name("second-click-slot").description("Slot click GUI 2.")
            .defaultValue(secondDefault).range(0, 53).sliderRange(0, 53).build());

        this.confirmClickSlot = sgGeneral.add(new IntSetting.Builder()
            .name("confirm-click-slot").description("Slot xác nhận.")
            .defaultValue(confirmDefault).range(0, 53).sliderRange(0, 53).build());
    }

    // ============================================================
    // GETTERS
    // ============================================================
    public boolean isSilentMode() { return silentMode.get(); }

    public boolean isFreezing() {
        if (!silentMode.get() || !freezeWhileBuying.get()) return false;
        return state == State.FIRST_CLICK || state == State.SECOND_CLICK || state == State.CONFIRMING;
    }

    // ============================================================
    // ĐẾM ITEM
    // ============================================================
    public int countTargetItems() {
        if (mc.player == null) return 0;
        String target = targetItem.get().toLowerCase().trim();

        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (target.isEmpty()) {
                count += stack.getCount();
            } else if (matchesItem(stack, target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    protected boolean matchesItem(ItemStack stack, String target) {
        String displayName = stack.getName().getString().toLowerCase();
        if (displayName.contains(target)) return true;

        var item = stack.getItem();
        if (target.contains("totem") && item == Items.TOTEM_OF_UNDYING) return true;
        if (target.contains("crystal") && item == Items.END_CRYSTAL) return true;
        if (target.contains("obsidian") && item == Items.OBSIDIAN) return true;
        if (target.contains("crying") && item == Items.CRYING_OBSIDIAN) return true;
        if (target.contains("gapple") && item == Items.GOLDEN_APPLE) return true;
        if (target.contains("egap") && item == Items.ENCHANTED_GOLDEN_APPLE) return true;
        if (target.contains("pearl") && item == Items.ENDER_PEARL) return true;
        if (target.contains("exp") && item == Items.EXPERIENCE_BOTTLE) return true;

        return false;
    }

    public boolean hasReachedMaxItems() {
        if (maxItemAmount.get() <= 0) return false;
        int current = countTargetItems();
        int bought = current - initialItemCount;
        return bought >= maxItemAmount.get();
    }

    // ============================================================
    // AUTO ENABLE
    // ============================================================
    public void checkAutoEnable() {
        if (!autoEnable.get()) return;
        if (isActive() || mc.player == null) return;
        if (countTargetItems() <= autoEnableThreshold.get()) toggle();
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================
    @Override
    public void onActivate() {
        for (AutoBuyShopBase other : ALL) {
            if (other != this && other.isActive()) other.toggle();
        }
        active = this;

        clickCooldownTicks = 0;
        totalBought = 0;
        shouldDisable = false;
        state = State.OPENING_SHOP;
        shopCommandSent = false;
        initialItemCount = countTargetItems();

        if (mc.player != null) ChatUtils.sendPlayerMsg(shopCommand.get());
    }

    @Override
    public void onDeactivate() {
        if (active == this) active = null;

        if (mc.player != null && mc.currentScreen instanceof HandledScreen<?>)
            mc.player.closeHandledScreen();
        if (silentMode.get() && mc.player != null
            && mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            mc.player.closeHandledScreen();

        state = State.OPENING_SHOP;
    }

    // ============================================================
    // TICK
    // ============================================================
    @EventHandler
    protected void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (shouldDisable) {
            shouldDisable = false;
            if (autoDisable.get()) toggle();
            return;
        }

        if (clickCooldownTicks > 0) { clickCooldownTicks--; return; }
        if (state == State.FINISHED) return;

        if (isInventoryFull()) {
            state = State.FINISHED;
            finishAndClose();
            return;
        }

        if (hasReachedMaxItems()) {
            state = State.FINISHED;
            finishAndClose();
            return;
        }

        if (silentMode.get()) { handleSilentMode(); return; }

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            if (state == State.OPENING_SHOP && !shopCommandSent) {
                ChatUtils.sendPlayerMsg(shopCommand.get());
                shopCommandSent = true;
                clickCooldownTicks = getDelayTicks(shopOpenDelay.get());
            }
            return;
        }

        shopCommandSent = false;
        handleClick(screen.getScreenHandler(), screen.getScreenHandler().syncId);
    }

    protected void handleSilentMode() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == mc.player.playerScreenHandler) {
            if (state == State.OPENING_SHOP && !shopCommandSent) {
                ChatUtils.sendPlayerMsg(shopCommand.get());
                shopCommandSent = true;
                clickCooldownTicks = getDelayTicks(shopOpenDelay.get());
            }
            return;
        }

        shopCommandSent = false;
        handleClick(handler, handler.syncId);
    }

    // ============================================================
    // CLICK LOGIC
    // ============================================================
    protected void handleClick(ScreenHandler handler, int syncId) {
        switch (state) {
            case OPENING_SHOP -> {
                state = State.FIRST_CLICK;
                clickCooldownTicks = getDelayTicks(delayFirst.get());
            }

            case FIRST_CLICK -> {
                if (firstClickSlot.get() < handler.slots.size()) {
                    clickSlot(syncId, firstClickSlot.get());
                    state = State.SECOND_CLICK;
                    clickCooldownTicks = getDelayTicks(delayBetween.get());
                }
            }

            case SECOND_CLICK -> {
                if (secondClickSlot.get() < handler.slots.size()) {
                    clickSlot(syncId, secondClickSlot.get());
                    state = State.CONFIRMING;
                    clickCooldownTicks = getDelayTicks(delaySecond.get());
                }
            }

            case CONFIRMING -> {
                if (confirmClickSlot.get() < handler.slots.size()) {
                    clickSlot(syncId, confirmClickSlot.get());
                    totalBought++;

                    if (hasReachedMaxItems() || isInventoryFull()) {
                        state = State.FINISHED;
                        finishAndClose();
                        return;
                    }

                    clickCooldownTicks = getDelayTicks(delayConfirm.get());
                }
            }
        }
    }

    // ============================================================
    // FINISH & UTILS
    // ============================================================
    protected void finishAndClose() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            mc.player.closeHandledScreen();

        int bought = countTargetItems() - initialItemCount;
        info("Auto Shop §a[ DONE ] §7- Bought §f" + bought + "§7 items");

        shouldDisable = true;
    }

    protected void clickSlot(int syncId, int slot) {
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    protected boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++)
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        return true;
    }

    protected int getDelayTicks(double delayMs) {
        if (delayMs <= 0.001) return 0;
        return (int) Math.round(delayMs / 50.0);
    }
}