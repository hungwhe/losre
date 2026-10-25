package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class AutoEnchant extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SETTINGS
    // ============================================================
    private final Setting<String> itemId = sgGeneral.add(new StringSetting.Builder()
        .name("item-id").description("ID item cần enchant (VD: minecraft:diamond_axe).")
        .defaultValue("minecraft:diamond_axe").build());

    private final Setting<String> ec1Id = sgGeneral.add(new StringSetting.Builder()
        .name("ec1-id").description("ID hoặc tên phù phép 1 (VD: mending).")
        .defaultValue("mending").build());

    private final Setting<String> ec2Id = sgGeneral.add(new StringSetting.Builder()
        .name("ec2-id").description("ID hoặc tên phù phép 2 (VD: unbreaking).")
        .defaultValue("unbreaking").build());

    private final Setting<String> ec3Id = sgGeneral.add(new StringSetting.Builder()
        .name("ec3-id").description("ID hoặc tên phù phép 3 (VD: efficiency).")
        .defaultValue("efficiency").build());

    private final Setting<Integer> batchAmount = sgGeneral.add(new IntSetting.Builder()
        .name("batch-amount").description("Số item lấy ra mỗi lần (1-7).")
        .defaultValue(4).range(1, 7).sliderRange(1, 7).build());

    private final Setting<Integer> batchCount = sgGeneral.add(new IntSetting.Builder()
        .name("batch-count").description("Số lần lặp lại quy trình (1-100).")
        .defaultValue(1).range(1, 100).sliderRange(1, 100).build());

    private final Setting<Integer> delayMs = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Delay giữa các thao tác (ms).")
        .defaultValue(150).range(50, 1000).sliderRange(50, 1000).build());

    private final Setting<Boolean> silentMode = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-mode")
        .description("Không hiện GUI khi enchant.")
        .defaultValue(false)
        .build());

    // ============================================================
    // STATE MACHINE
    // ============================================================
    private enum State {
        SEARCHING_CHEST,
        OPENING_CHEST,
        WITHDRAWING,
        SEARCHING_ANVIL,
        OPENING_ANVIL,
        ENCHANTING,
        FINISHING
    }

    private State state = State.SEARCHING_CHEST;
    private int timer = 0;
    
    private int currentCycle = 1;
    private int totalCycles = 1;
    private int targetPerCycle = 1;
    private int enchantedThisCycle = 0;
    
    private int enchantPhase = 1;
    private int actionStep = 0;
    
    private BlockPos currentTargetPos = null;
    private final Set<BlockPos> visitedChests = new HashSet<>();

    public AutoEnchant() {
        super(HwellAddon.FARM, "Hwell Auto Enchant", "Tự động lấy đồ từ rương và enchant ở anvil.");
    }

    public boolean isSilentMode() { return silentMode.get(); }

    @Override
    public void onActivate() {
        totalCycles = batchCount.get();
        targetPerCycle = batchAmount.get();
        currentCycle = 1;
        enchantedThisCycle = 0;
        
        state = State.SEARCHING_CHEST;
        timer = 0;
        currentTargetPos = null;
        visitedChests.clear();
        enchantPhase = 1;
        actionStep = 0;

        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§c[hwelladdon] §6đã bật auto enchant made by hwell tư duy , logic , tối ưu về mọi mặt bọn ngu thích skid ý tưởng chan đi"), false);
        }
    }

    @Override
    public void onDeactivate() {
        state = State.SEARCHING_CHEST;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        // 1. Kiểm tra đe vỡ
        if ((state == State.ENCHANTING || state == State.OPENING_ANVIL) && currentTargetPos != null) {
            if (!(mc.world.getBlockState(currentTargetPos).getBlock() instanceof AnvilBlock)) {
                state = State.SEARCHING_ANVIL;
                actionStep = 0; 
                timer = getTicks();
                return;
            }
        }

        // 2. Kiểm tra bị đánh hoặc ấn ESC
        if (isInterrupted()) {
            toggle();
            return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        
        // ✅ Đã sửa: Luôn kiểm tra bằng handler, không phụ thuộc vào GUI hiển thị
        boolean guiOpen = handler != mc.player.playerScreenHandler;

        switch (state) {
            case SEARCHING_CHEST -> {
                currentTargetPos = findNearbyUnvisitedChest(5);
                if (currentTargetPos != null) {
                    rotateTo(currentTargetPos);
                    state = State.OPENING_CHEST;
                    timer = getTicks();
                } else {
                    toggle(); 
                }
            }

            case OPENING_CHEST -> {
                if (!guiOpen) {
                    interactBlock(currentTargetPos);
                    timer = getTicks();
                } else {
                    state = State.WITHDRAWING;
                    timer = getTicks();
                }
            }

            case WITHDRAWING -> {
                if (guiOpen) {
                    int invItem = countInInventory(itemId.get());
                    int invEc1 = countInInventory(ec1Id.get());
                    int invEc2 = countInInventory(ec2Id.get());
                    int invEc3 = countInInventory(ec3Id.get());

                    if (invItem >= targetPerCycle && invEc1 >= targetPerCycle && invEc2 >= targetPerCycle && invEc3 >= targetPerCycle) {
                        mc.player.closeHandledScreen();
                        state = State.SEARCHING_ANVIL;
                        timer = getTicks();
                        return;
                    }

                    boolean withdrewSomething = false;
                    int chestSize = handler.slots.size() - 36; 

                    for (int i = 0; i < chestSize; i++) {
                        ItemStack stack = handler.getSlot(i).getStack();
                        if (stack.isEmpty()) continue;

                        if (matchesItem(stack, itemId.get()) && invItem < targetPerCycle) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            withdrewSomething = true;
                            break;
                        } else if (matchesItem(stack, ec1Id.get()) && invEc1 < targetPerCycle) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            withdrewSomething = true;
                            break;
                        } else if (matchesItem(stack, ec2Id.get()) && invEc2 < targetPerCycle) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            withdrewSomething = true;
                            break;
                        } else if (matchesItem(stack, ec3Id.get()) && invEc3 < targetPerCycle) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                            withdrewSomething = true;
                            break;
                        }
                    }

                    if (withdrewSomething) {
                        timer = getTicks();
                    } else {
                        visitedChests.add(currentTargetPos); 
                        mc.player.closeHandledScreen();
                        state = State.SEARCHING_CHEST;
                        timer = getTicks();
                    }
                } else {
                    // Rương bị đóng (out of range) -> Tìm lại
                    state = State.SEARCHING_CHEST;
                    timer = getTicks();
                }
            }

            case SEARCHING_ANVIL -> {
                currentTargetPos = findNearbyBlock(AnvilBlock.class, 5);
                if (currentTargetPos != null) {
                    rotateTo(currentTargetPos);
                    state = State.OPENING_ANVIL;
                    timer = getTicks();
                } else {
                    timer = 40; 
                }
            }

            case OPENING_ANVIL -> {
                if (!guiOpen) {
                    interactBlock(currentTargetPos);
                    timer = getTicks();
                } else {
                    state = State.ENCHANTING;
                    actionStep = 0;
                    timer = getTicks();
                }
            }

            case ENCHANTING -> {
                if (guiOpen) {
                    ItemStack slot0 = handler.getSlot(0).getStack();
                    ItemStack slot1 = handler.getSlot(1).getStack();
                    ItemStack slot2 = handler.getSlot(2).getStack();

                    if (actionStep == 0) {
                        if (slot0.isEmpty()) {
                            if (!moveItemToSlot(itemId.get(), 0)) {
                                timer = getTicks(); 
                            } else {
                                timer = getTicks();
                            }
                        } else if (matchesItem(slot0, itemId.get())) {
                            boolean hasEc1 = hasEnchantment(slot0, ec1Id.get());
                            boolean hasEc2 = hasEnchantment(slot0, ec2Id.get());
                            boolean hasEc3 = hasEnchantment(slot0, ec3Id.get());

                            if (hasEc1 && hasEc2 && hasEc3) {
                                mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.THROW, mc.player);
                                finishCurrentItem();
                            } else if (hasEc1 && hasEc2) {
                                enchantPhase = 3;
                                actionStep = 1;
                                timer = getTicks();
                            } else if (hasEc1) {
                                enchantPhase = 2;
                                actionStep = 1;
                                timer = getTicks();
                            } else {
                                enchantPhase = 1;
                                actionStep = 1;
                                timer = getTicks();
                            }
                        } else {
                            mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
                            timer = getTicks();
                        }
                    } else if (actionStep == 1) {
                        String targetBook = (enchantPhase == 1) ? ec1Id.get() : (enchantPhase == 2) ? ec2Id.get() : ec3Id.get();
                        if (matchesItem(slot1, targetBook)) {
                            actionStep = 2;
                            timer = getTicks();
                        } else if (slot1.isEmpty()) {
                            if (!moveItemToSlot(targetBook, 1)) {
                                timer = getTicks(); 
                            } else {
                                timer = getTicks();
                            }
                        } else {
                            mc.interactionManager.clickSlot(handler.syncId, 1, 0, SlotActionType.QUICK_MOVE, mc.player);
                            timer = getTicks();
                        }
                    } else if (actionStep == 2) {
                        if (!slot2.isEmpty()) {
                            actionStep = 3;
                            timer = getTicks();
                        } else {
                            timer = getTicks(); 
                        }
                    } else if (actionStep == 3) {
                        if (!slot2.isEmpty()) {
                            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
                            actionStep = 4;
                            timer = 1; 
                        } else {
                            actionStep = 2;
                            timer = getTicks();
                        }
                    } else if (actionStep == 4) {
                        if (slot2.isEmpty()) {
                            mc.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, mc.player);
                            actionStep = 0;
                            timer = getTicks();
                        } else {
                            if (mc.player != null) {
                                mc.player.sendMessage(Text.literal("§c[hwelladdon] §6dcu exp thiếu kìa enchant cc j nữa"), false);
                            }
                            actionStep = 3;
                            timer = 40; 
                        }
                    }
                } else {
                    // Đe bị đóng (out of range) -> Tìm lại
                    state = State.SEARCHING_ANVIL;
                    timer = getTicks();
                }
            }

            case FINISHING -> {
                if (guiOpen) mc.player.closeHandledScreen();
                toggle();
            }
        }
    }

    private void finishCurrentItem() {
        enchantedThisCycle++;
        
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§c[hwelladdon] §6xog rồi đáy ku e (" + enchantedThisCycle + "/" + targetPerCycle + ")"), false);
        }

        if (enchantedThisCycle >= targetPerCycle) {
            if (currentCycle >= totalCycles) {
                state = State.FINISHING;
                mc.player.closeHandledScreen();
                toggle();
            } else {
                currentCycle++;
                enchantedThisCycle = 0;
                mc.player.closeHandledScreen();
                state = State.SEARCHING_CHEST;
                timer = getTicks();
            }
        } else {
            enchantPhase = 1;
            actionStep = 0;
            timer = getTicks();
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private boolean hasEnchantment(ItemStack stack, String target) {
        if (stack.isEmpty()) return false;
        String t = target.toLowerCase().trim().replace(" ", "_");
        if (!t.contains(":")) t = "minecraft:" + t;

        ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (enchants != null) {
            for (RegistryEntry<Enchantment> enchant : enchants.getEnchantments()) {
                String enchantId = enchant.getIdAsString();
                if (enchantId.equals(t) || enchantId.replace("minecraft:", "").equals(t.replace("minecraft:", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesItem(ItemStack stack, String target) {
        String t = target.toLowerCase().trim().replace(" ", "_");
        if (!t.contains(":")) t = "minecraft:" + t;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id.toString().equals(t) || id.getPath().equals(t.replace("minecraft:", ""))) return true;

        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (enchants != null) {
                for (RegistryEntry<Enchantment> enchant : enchants.getEnchantments()) {
                    String enchantId = enchant.getIdAsString();
                    if (enchantId.equals(t) || enchantId.replace("minecraft:", "").equals(t.replace("minecraft:", ""))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean moveItemToSlot(String targetId, int targetSlot) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        int totalSlots = handler.slots.size();
        int playerInvStart = totalSlots - 36;

        for (int i = playerInvStart; i < totalSlots; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;

            if (matchesItem(stack, targetId)) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(handler.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
                return true;
            }
        }
        return false;
    }

    private int countInInventory(String targetId) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (matchesItem(stack, targetId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private BlockPos findNearbyUnvisitedChest(int radius) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, radius, radius, radius)) {
            Block block = mc.world.getBlockState(pos).getBlock();
            if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
                BlockPos immutablePos = pos.toImmutable();
                if (!visitedChests.contains(immutablePos)) {
                    return immutablePos;
                }
            }
        }
        return null;
    }

    private BlockPos findNearbyBlock(Class<? extends Block> blockClass, int radius) {
        BlockPos playerPos = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, radius, radius, radius)) {
            if (blockClass.isInstance(mc.world.getBlockState(pos).getBlock())) {
                return pos.toImmutable();
            }
        }
        return null;
    }

    private void rotateTo(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(center.z - mc.player.getZ(), center.x - mc.player.getX())) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(center.y - mc.player.getEyeY(), Math.sqrt(Math.pow(center.x - mc.player.getX(), 2) + Math.pow(center.z - mc.player.getZ(), 2))));
        Rotations.rotate(yaw, pitch);
    }

    private void interactBlock(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    private boolean isInterrupted() {
        if (mc.player == null) return true;
        
        // Chỉ tắt khi ấn ESC mà đe vẫn còn (không áp dụng khi silent mode vì không thể biết)
        if (!isSilentMode() && mc.currentScreen == null && state == State.ENCHANTING) {
            if (currentTargetPos != null && mc.world.getBlockState(currentTargetPos).getBlock() instanceof AnvilBlock) {
                return true;
            }
        }
        
        if (mc.player.hurtTime > 0) return true; 
        return false;
    }

    private int getTicks() {
        return Math.max(1, delayMs.get() / 50);
    }
}