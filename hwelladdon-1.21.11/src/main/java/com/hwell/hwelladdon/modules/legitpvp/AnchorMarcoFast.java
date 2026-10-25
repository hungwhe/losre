package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AnchorMarcoFast extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> anchorSlot = sgGeneral.add(new IntSetting.Builder()
        .name("anchor-slot")
        .description("Slot hotbar chứa Respawn Anchor (1-9).")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> glowstoneSlot = sgGeneral.add(new IntSetting.Builder()
        .name("glowstone-slot")
        .description("Slot hotbar chứa Glowstone (1-9).")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> totemSlot = sgGeneral.add(new IntSetting.Builder()
        .name("totem-slot")
        .description("Slot hotbar chứa Totem (1-9).")
        .defaultValue(3)
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
        .description("Tự động quay lại slot ban đầu sau khi combo xong.")
        .defaultValue(true)
        .build()
    );

    private enum State { IDLE, CHARGE_GLOWSTONE, DETONATE_TOTEM, SWAP_BACK }

    private State state = State.IDLE;
    private int timer = 0;
    private int originalSlot = -1;
    private BlockPos targetPos = null;
    private boolean wasUsePressed = false;

    public AnchorMarcoFast() {
        super(HwellAddon.PVP_SUPER_LEGIT, "anchor-marco-fast", "Tự động nạp Glowstone và kích nổ Anchor bằng Totem.");
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        timer = 0;
        originalSlot = -1;
        targetPos = null;
        wasUsePressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (state == State.IDLE) {
            boolean isUsePressed = mc.options.useKey.isPressed();

            if (isUsePressed && !wasUsePressed) {
                if (mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR) {
                    if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;

                        targetPos = hit.getBlockPos().offset(hit.getSide());
                        originalSlot = mc.player.getInventory().getSelectedSlot();

                        state = State.CHARGE_GLOWSTONE;
                        timer = getTicks();
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
                case CHARGE_GLOWSTONE -> {
                    if (targetPos != null) {
                        InvUtils.swap(glowstoneSlot.get() - 1, false);
                        clickBlock(targetPos);
                        state = State.DETONATE_TOTEM;
                        timer = getTicks();
                    }
                }
                case DETONATE_TOTEM -> {
                    if (targetPos != null) {
                        InvUtils.swap(totemSlot.get() - 1, false);
                        clickBlock(targetPos);
                        state = State.SWAP_BACK;
                        timer = getTicks();
                    }
                }
                case SWAP_BACK -> {
                    if (swapBack.get() && originalSlot != -1) {
                        InvUtils.swap(originalSlot, false);
                    }
                    state = State.IDLE;
                    targetPos = null;
                    originalSlot = -1;
                }
            }
        }
    }

    private void clickBlock(BlockPos pos) {
        Vec3d hitVec = Vec3d.ofCenter(pos).add(0, 0.5, 0);
        BlockHitResult placeHit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int getTicks() {
        if (delay.get() <= 0) return 0;
        return Math.max(1, delay.get() / 50);
    }
}