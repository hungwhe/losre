package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class EasyDTap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> obiSlot = sgGeneral.add(new IntSetting.Builder()
        .name("obsidian-slot")
        .description("Slot hotbar chứa Obsidian (1-9).")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> crystalSlot = sgGeneral.add(new IntSetting.Builder()
        .name("crystal-slot")
        .description("Slot hotbar chứa End Crystal (1-9).")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay giữa các hành động (ms). Để 0 để nhanh nhất.")
        .defaultValue(0)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Tự động quay lại slot ban đầu sau khi đặt xong.")
        .defaultValue(true)
        .build()
    );

    private enum State { IDLE, PLACING_OBSIDIAN, PLACING_CRYSTAL }

    private State state = State.IDLE;
    private int timer = 0;
    private int originalSlot = -1;
    private BlockPos targetPos = null;
    private Direction targetSide = null;
    private boolean wasUsePressed = false;

    public EasyDTap() {
        super(HwellAddon.PVP_SUPER_LEGIT, "easy-dtap", "Tự động đặt obsidian và crystal khi click chuột phải.");
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        timer = 0;
        originalSlot = -1;
        targetPos = null;
        targetSide = null;
        wasUsePressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (state == State.IDLE) {
            boolean isUsePressed = mc.options.useKey.isPressed();

            if (isUsePressed && !wasUsePressed) {
                if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;

                    boolean holdingCrystal = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
                    boolean clickingObsidian = mc.world.getBlockState(hit.getBlockPos()).getBlock() == Blocks.OBSIDIAN
                                            || mc.world.getBlockState(hit.getBlockPos()).getBlock() == Blocks.BEDROCK;

                    if (holdingCrystal && clickingObsidian) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    } else {
                        if (!mc.player.getMainHandStack().isEmpty()) {
                            targetPos = hit.getBlockPos();
                            targetSide = hit.getSide();
                            originalSlot = mc.player.getInventory().getSelectedSlot();

                            InvUtils.swap(obiSlot.get() - 1, false);
                            state = State.PLACING_OBSIDIAN;
                            timer = getTicks();
                        }
                    }
                }
            }
            wasUsePressed = isUsePressed;
        } else {
            if (timer > 0) {
                timer--;
                return;
            }

            switch (state) {
                case PLACING_OBSIDIAN -> {
                    if (targetPos != null && targetSide != null) {
                        Vec3d hitVec = Vec3d.ofCenter(targetPos).add(Vec3d.of(targetSide.getVector()).multiply(0.5));
                        BlockHitResult placeHit = new BlockHitResult(hitVec, targetSide, targetPos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
                        mc.player.swingHand(Hand.MAIN_HAND);

                        InvUtils.swap(crystalSlot.get() - 1, false);
                        state = State.PLACING_CRYSTAL;
                        timer = getTicks();
                    }
                }
                case PLACING_CRYSTAL -> {
                    if (targetPos != null && targetSide != null) {
                        BlockPos obsidianPos = targetPos.offset(targetSide);
                        Vec3d hitVec = Vec3d.ofCenter(obsidianPos);
                        BlockHitResult placeHit = new BlockHitResult(hitVec, Direction.UP, obsidianPos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
                        mc.player.swingHand(Hand.MAIN_HAND);

                        if (swapBack.get() && originalSlot != -1) {
                            InvUtils.swap(originalSlot, false);
                        }

                        state = State.IDLE;
                        targetPos = null;
                        targetSide = null;
                        originalSlot = -1;
                    }
                }
            }
        }
    }

    private int getTicks() {
        if (delay.get() <= 0) return 0;
        return Math.max(1, delay.get() / 50);
    }
}