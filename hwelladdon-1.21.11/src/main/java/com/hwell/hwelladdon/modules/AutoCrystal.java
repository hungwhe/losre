package com.hwell.hwelladdon.modules;

import com.hwell.hwelladdon.HwellAddon;
import com.hwell.hwelladdon.utils.CrystalManager;
import com.hwell.hwelladdon.utils.ExplosionUtility;
import com.hwell.hwelladdon.utils.PredictUtility;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class AutoCrystal extends Module {

    // ============================================================
    // ENUMS
    // ============================================================
    public enum Rotation { OFF, NORMAL, GRIM }
    public enum TargetBy { Distance, Health, Armor }
    public enum Interact { Default, Strict }
    public enum Switch { NONE, NORMAL, SILENT, INVENTORY }
    public enum Swing { Both, Place, Break }

    // ============================================================
    // SETTINGS - MAIN
    // ============================================================
    private final SettingGroup sgMain = settings.getDefaultGroup();

    private final Setting<Rotation> rotate = sgMain.add(new EnumSetting.Builder<Rotation>()
        .name("rotate").description("Chế độ quay.")
        .defaultValue(Rotation.NORMAL).build());

    private final Setting<Boolean> yawStepEnabled = sgMain.add(new BoolSetting.Builder()
        .name("yaw-step").description("Chia nhỏ góc quay.")
        .defaultValue(false).visible(() -> rotate.get() != Rotation.OFF).build());

    private final Setting<Double> yawAngle = sgMain.add(new DoubleSetting.Builder()
        .name("yaw-angle").description("Góc quay tối đa.")
        .defaultValue(180.0).range(1.0, 180.0).sliderRange(1.0, 180.0)
        .visible(() -> rotate.get() != Rotation.OFF && yawStepEnabled.get()).build());

    private final Setting<TargetBy> targetLogic = sgMain.add(new EnumSetting.Builder<TargetBy>()
        .name("target-logic").description("Cách chọn target.")
        .defaultValue(TargetBy.Distance).build());

    private final Setting<Double> targetRange = sgMain.add(new DoubleSetting.Builder()
        .name("target-range").description("Bán kính tìm target.")
        .defaultValue(10.0).range(1.0, 15.0).sliderRange(1.0, 15.0).build());

    private final Setting<Integer> extrapolation = sgMain.add(new IntSetting.Builder()
        .name("extrapolation").description("Dự đoán vị trí target (tick).")
        .defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    // ============================================================
    // SETTINGS - PLACE
    // ============================================================
    private final SettingGroup sgPlace = settings.createGroup("Place");

    private final Setting<Interact> interact = sgPlace.add(new EnumSetting.Builder<Interact>()
        .name("interact").description("Mode đặt crystal.")
        .defaultValue(Interact.Default).build());

    private final Setting<Boolean> strictCenter = sgPlace.add(new BoolSetting.Builder()
        .name("strict-center").description("Đặt crystal ở tâm block.")
        .defaultValue(true).visible(() -> interact.get() == Interact.Strict).build());

    private final Setting<Boolean> rayTraceBypass = sgPlace.add(new BoolSetting.Builder()
        .name("ray-trace-bypass").description("Bypass visibility check.")
        .defaultValue(false).build());

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Delay đặt crystal (tick).")
        .defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Integer> lowPlaceDelay = sgPlace.add(new IntSetting.Builder()
        .name("low-place-delay").description("Delay khi face place.")
        .defaultValue(11).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Bán kính đặt crystal.")
        .defaultValue(5.0).range(1.0, 6.0).sliderRange(1.0, 6.0).build());

    private final Setting<Double> placeWallRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-wall-range").description("Bán kính đặt xuyên tường.")
        .defaultValue(3.5).range(0.0, 6.0).sliderRange(0.0, 6.0).build());

    // ============================================================
    // SETTINGS - BREAK
    // ============================================================
    private final SettingGroup sgBreak = settings.createGroup("Break");

    private final Setting<Boolean> inhibit = sgBreak.add(new BoolSetting.Builder()
        .name("inhibit").description("Không đập crystal đã chết.")
        .defaultValue(true).build());

    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay").description("Delay đập crystal (tick).")
        .defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Integer> lowBreakDelay = sgBreak.add(new IntSetting.Builder()
        .name("low-break-delay").description("Delay khi face place.")
        .defaultValue(11).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Double> explodeRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range").description("Bán kính đập crystal.")
        .defaultValue(5.0).range(1.0, 6.0).sliderRange(1.0, 6.0).build());

    private final Setting<Double> explodeWallRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-wall-range").description("Bán kính đập xuyên tường.")
        .defaultValue(3.5).range(0.0, 6.0).sliderRange(0.0, 6.0).build());

    private final Setting<Integer> crystalAge = sgBreak.add(new IntSetting.Builder()
        .name("crystal-age").description("Tuổi tối thiểu (tick).")
        .defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    // ============================================================
    // SETTINGS - DAMAGE
    // ============================================================
    private final SettingGroup sgDamage = settings.createGroup("Damage");

    private final Setting<Double> minDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("min-damage").description("Damage tối thiểu lên target.")
        .defaultValue(6.0).range(2.0, 20.0).sliderRange(2.0, 20.0).build());

    private final Setting<Double> maxSelfDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Damage tối đa lên self.")
        .defaultValue(10.0).range(2.0, 20.0).sliderRange(2.0, 20.0).build());

    private final Setting<Boolean> protectFriends = sgDamage.add(new BoolSetting.Builder()
        .name("protect-friends").description("Không đập gần bạn bè.")
        .defaultValue(true).build());

    private final Setting<Boolean> sacrificeTotem = sgDamage.add(new BoolSetting.Builder()
        .name("sacrifice-totem").description("Hy sinh totem để giết.")
        .defaultValue(true).build());

    private final Setting<Double> facePlaceHp = sgDamage.add(new DoubleSetting.Builder()
        .name("face-place-hp").description("HP target để face place.")
        .defaultValue(5.0).range(0.0, 20.0).sliderRange(0.0, 20.0).build());

    private final Setting<Boolean> ignoreTerrain = sgDamage.add(new BoolSetting.Builder()
        .name("ignore-terrain").description("Bỏ qua địa hình.")
        .defaultValue(true).build());

    // ============================================================
    // SETTINGS - PAUSE
    // ============================================================
    private final SettingGroup sgPause = settings.createGroup("Pause");

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine").description("Dừng khi đang đào.")
        .defaultValue(true).build());

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-eat").description("Dừng khi đang ăn.")
        .defaultValue(true).build());

    private final Setting<Boolean> pauseOnInv = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-inventory").description("Dừng khi mở inventory.")
        .defaultValue(false).build());

    private final Setting<Double> pauseHP = sgPause.add(new DoubleSetting.Builder()
        .name("pause-hp").description("Dừng khi HP thấp.")
        .defaultValue(6.0).range(2.0, 10.0).sliderRange(2.0, 10.0).build());

    // ============================================================
    // SETTINGS - SWITCH
    // ============================================================
    private final SettingGroup sgSwitch = settings.createGroup("Switch");

    private final Setting<Switch> autoSwitch = sgSwitch.add(new EnumSetting.Builder<Switch>()
        .name("auto-switch").description("Mode chuyển slot crystal.")
        .defaultValue(Switch.SILENT).build());

    private final Setting<Switch> antiWeakness = sgSwitch.add(new EnumSetting.Builder<Switch>()
        .name("anti-weakness").description("Mode chuyển vũ khí khi Weakness.")
        .defaultValue(Switch.SILENT).build());

    // ============================================================
    // SETTINGS - SWING
    // ============================================================
    private final SettingGroup sgSwing = settings.createGroup("Swing");

    private final Setting<Swing> swingMode = sgSwing.add(new EnumSetting.Builder<Swing>()
        .name("swing").description("Mode swing hand.")
        .defaultValue(Swing.Place).build());

    // ============================================================
    // STATE
    // ============================================================
    public static PlayerEntity target;
    private BlockHitResult bestPosition;
    private EndCrystalEntity bestCrystal;

    private int placeTimer;
    private int breakTimer;
    private int rotationTicks;

    private final CrystalManager crystalManager = new CrystalManager();

    public float renderDamage, renderSelfDamage;
    private float rotationYaw, rotationPitch;
    private boolean rotating, facePlacing;

    private final Random random = new Random();

    public AutoCrystal() {
        super(HwellAddon.PVP, "auto-crystal",
            "Auto place + break crystal. Yarn 1.21.11 compatible.");
    }

    @Override
    public void onActivate() { reset(); }

    @Override
    public void onDeactivate() { reset(); }

    private void reset() {
        facePlacing = false;
        renderDamage = 0;
        renderSelfDamage = 0;
        crystalManager.reset();
        placeTimer = 0;
        breakTimer = 0;
        rotationTicks = 0;
        bestCrystal = null;
        bestPosition = null;
        target = null;
        rotating = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;
        if (rotationTicks > 0) rotationTicks--;

        crystalManager.update();

        target = findTarget();
        if (target == null) return;

        if (shouldPause()) return;

        if (rotate.get() != Rotation.OFF) calcRotations();

        if (rotating) {
            boolean hitVisible = bestCrystal == null || canSee(bestCrystal.getEntityPos());
            boolean placeVisible = bestPosition == null || canSee(bestPosition.getPos());

            if (mc.player.age % 5 == 0 && rayTraceBypass.get() && (!hitVisible || !placeVisible)) {
                mc.player.setPitch(-90);
            } else {
                mc.player.setPitch(rotationPitch);
            }
            mc.player.setYaw(rotationYaw);
        }

        bestPosition = findPlacePos();
        bestCrystal = findCrystalToBreak();

        if (bestCrystal != null && breakTimer <= 0) {
            attackCrystal(bestCrystal);
            breakTimer = facePlacing ? lowBreakDelay.get() : breakDelay.get();
        }

        if (bestPosition != null && placeTimer <= 0) {
            placeCrystal(bestPosition);
            placeTimer = facePlacing ? lowPlaceDelay.get() : placeDelay.get();
        }
    }

    // ============================================================
    // TARGET
    // ============================================================
    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestValue = Double.MAX_VALUE;
        double maxR = targetRange.get();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity p)) continue;
            if (p == mc.player || p.isDead() || p.getHealth() <= 0) continue;

            double dist = mc.player.distanceTo(p);
            if (dist > maxR) continue;

            double value = switch (targetLogic.get()) {
                case Distance -> dist;
                case Health -> p.getHealth() + p.getAbsorptionAmount();
                case Armor -> p.getArmor();
            };

            if (value < bestValue) {
                bestValue = value;
                best = p;
            }
        }
        return best;
    }

    // ============================================================
    // ROTATION
    // ============================================================
    private void calcRotations() {
        if (bestPosition == null && bestCrystal == null) {
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
            rotating = false;
            return;
        }

        Vec3d vec = bestPosition == null ? bestCrystal.getEntityPos() : bestPosition.getPos();

        float yawDelta = wrapDegrees((float) wrapDegrees(
            Math.toDegrees(Math.atan2(vec.z - mc.player.getZ(), vec.x - mc.player.getX())) - 90
        ) - rotationYaw);

        float pitchDelta = (float) (-Math.toDegrees(Math.atan2(
            vec.y - (mc.player.getEntityPos().y + mc.player.getEyeHeight(mc.player.getPose())),
            Math.sqrt(Math.pow(vec.x - mc.player.getX(), 2) + Math.pow(vec.z - mc.player.getZ(), 2))
        ))) - rotationPitch;

        float angleToRad = (float) Math.toRadians(27 * (mc.player.age % 30));
        yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + randomRange(-1f, 1f);
        pitchDelta = pitchDelta + randomRange(-0.6f, 0.6f);

        if (yawDelta > 180) yawDelta -= 180;

        float yawStepVal = 180f;
        if (yawStepEnabled.get()) yawStepVal = yawAngle.get().floatValue();

        float clampedYawDelta = MathHelper.clamp(MathHelper.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -45, 45);

        float newYaw = rotationYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = MathHelper.clamp(rotationPitch + clampedPitchDelta, -90.0F, 90.0F);

        double gcdFix = (Math.pow(mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 1.2;

        rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
        rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcdFix);

        rotating = true;
    }

    // ============================================================
    // PLACE
    // ============================================================
    private BlockHitResult findPlacePos() {
        if (target == null || mc.player == null || mc.world == null) return null;

        BlockPos targetPos = target.getBlockPos();
        BlockPos bestPos = null;
        float bestDamage = 0;
        double bestDist = Double.MAX_VALUE;
        int r = (int) Math.ceil(placeRange.get());

        Vec3d predictedPlayer = PredictUtility.predictPosition(mc.player, getSelfPredictTicks());

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!canPlaceCrystal(pos)) continue;

                    double dist = predictedPlayer.distanceTo(Vec3d.ofCenter(pos));
                    if (dist > placeRange.get()) continue;

                    Vec3d crystalVec = Vec3d.ofCenter(pos).add(0, 1, 0);
                    if (!canSee(crystalVec)) continue;

                    float damage = ExplosionUtility.getAutoCrystalDamage(
                        crystalVec, target, extrapolation.get(), ignoreTerrain.get());
                    float selfDamage = ExplosionUtility.getSelfExplosionDamage(
                        crystalVec, getSelfPredictTicks(), ignoreTerrain.get());

                    boolean overrideDamage = shouldOverrideMaxSelfDmg(damage, selfDamage);
                    boolean facePlace = target.getHealth() + target.getAbsorptionAmount() <= facePlaceHp.get();

                    if (!facePlace && damage < minDamage.get()) continue;
                    if (selfDamage > maxSelfDamage.get() && !overrideDamage) continue;

                    if (protectFriends.get() && wouldHitFriend(crystalVec, selfDamage)) continue;

                    if (damage > bestDamage || (damage == bestDamage && dist < bestDist)) {
                        bestDamage = damage;
                        bestDist = dist;
                        bestPos = pos;
                    }
                }
            }
        }

        if (bestPos == null) return null;

        facePlacing = bestDamage < minDamage.get();
        renderDamage = bestDamage;

        Vec3d hitPos = Vec3d.ofCenter(bestPos).add(0, 1, 0);

        // Interact mode
        if (interact.get() == Interact.Strict) {
            return getStrictInteract(bestPos);
        }

        return new BlockHitResult(hitPos, Direction.UP, bestPos, false);
    }

    private BlockHitResult getStrictInteract(BlockPos bp) {
        Direction bestDirection = null;
        float upPoint = strictCenter.get() ? (float) bp.toCenterPos().getY() : bp.up().getY();

        if (mc.player.getEyePos().getY() > upPoint) {
            bestDirection = Direction.UP;
        } else if (mc.player.getEyePos().getY() < bp.getY() && mc.world.isAir(bp.down())) {
            bestDirection = Direction.DOWN;
        } else {
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (!mc.world.getBlockState(bp.offset(dir)).isReplaceable()) continue;
                bestDirection = dir;
                break;
            }
        }

        if (bestDirection == null) bestDirection = Direction.UP;

        Vec3d vec = new Vec3d(
            bp.getX() + 0.5 + bestDirection.getVector().getX() * 0.5,
            bp.getY() + 0.99,
            bp.getZ() + 0.5 + bestDirection.getVector().getZ() * 0.5
        );

        return new BlockHitResult(vec, bestDirection, bp, false);
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        if (mc.world == null) return false;

        Block base = mc.world.getBlockState(pos).getBlock();
        if (base != Blocks.OBSIDIAN && base != Blocks.BEDROCK) return false;
        if (!mc.world.isAir(pos.up())) return false;
        if (!mc.world.isAir(pos.up(2))) return false;

        Box box = new Box(pos.up());
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity) continue;
            if (e.getBoundingBox().intersects(box)) return false;
        }
        return true;
    }

    private void placeCrystal(BlockHitResult bhr) {
        if (mc.player == null || bhr == null) return;

        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        boolean holding = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL || offhand;

        int prevSlot = -1;

        if (autoSwitch.get() != Switch.NONE && !holding) {
            FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (crystal.found()) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                if (autoSwitch.get() == Switch.SILENT) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(crystal.slot()));
                } else if (autoSwitch.get() == Switch.NORMAL) {
                    InvUtils.swap(crystal.slot(), false);
                } else if (autoSwitch.get() == Switch.INVENTORY) {
                    FindItemResult invCrystal = InvUtils.find(Items.END_CRYSTAL);
                    if (invCrystal.found()) {
                        mc.interactionManager.clickSlot(
                            mc.player.currentScreenHandler.syncId,
                            invCrystal.slot(),
                            mc.player.getInventory().getSelectedSlot(),
                            SlotActionType.SWAP,
                            mc.player
                        );
                        prevSlot = invCrystal.slot();
                    }
                }
            } else return;
        }

        if (mc.player.getMainHandStack().getItem() != Items.END_CRYSTAL
            && mc.player.getOffHandStack().getItem() != Items.END_CRYSTAL
            && autoSwitch.get() != Switch.SILENT) return;

        mc.interactionManager.interactBlock(mc.player, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, bhr);
        swingHand(offhand, false);

        crystalManager.addAwaitingPos(bhr.getBlockPos());
        rotationTicks = 10;

        if (prevSlot != -1) {
            if (autoSwitch.get() == Switch.SILENT) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
            } else if (autoSwitch.get() == Switch.INVENTORY) {
                mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    prevSlot,
                    mc.player.getInventory().getSelectedSlot(),
                    SlotActionType.SWAP,
                    mc.player
                );
            }
        }
    }

    // ============================================================
    // BREAK
    // ============================================================
    private EndCrystalEntity findCrystalToBreak() {
        if (target == null || mc.world == null) return null;

        List<EndCrystalEntity> crystals = new ArrayList<>();
        double maxR = explodeRange.get();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c)) continue;
            if (!c.isAlive()) continue;
            if (crystalAge.get() != 0 && c.age < crystalAge.get()) continue;
            if (crystalManager.isDead(c.getId()) && inhibit.get()) continue;

            double dist = mc.player.distanceTo(c);
            if (dist > maxR) continue;
            if (!canSee(c.getEntityPos())) continue;

            float damage = ExplosionUtility.getAutoCrystalDamage(
                c.getEntityPos(), target, extrapolation.get(), ignoreTerrain.get());
            float selfDamage = ExplosionUtility.getSelfExplosionDamage(
                c.getEntityPos(), getSelfPredictTicks(), ignoreTerrain.get());

            boolean overrideDamage = shouldOverrideMaxSelfDmg(damage, selfDamage);

            if (damage < minDamage.get() && !isFacePlace()) continue;
            if (selfDamage > maxSelfDamage.get() && !overrideDamage) continue;
            if (protectFriends.get() && wouldHitFriend(c.getEntityPos(), selfDamage)) continue;

            crystals.add(c);
        }

        if (crystals.isEmpty()) return null;
        crystals.sort((a, b) -> Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)));
        return crystals.get(0);
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (mc.player == null || mc.interactionManager == null) return;

        int prevSlot = -1;

        StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
        StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);

        if (antiWeakness.get() != Switch.NONE
            && weakness != null
            && (strength == null || strength.getAmplifier() < weakness.getAmplifier())) {

            FindItemResult weapon = InvUtils.findInHotbar(this::isWeaponItem);

            if (weapon.found()) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
                if (antiWeakness.get() == Switch.SILENT) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(weapon.slot()));
                } else if (antiWeakness.get() == Switch.NORMAL) {
                    InvUtils.swap(weapon.slot(), false);
                }
            }
        }

        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        swingHand(false, true);

        crystalManager.onAttack(crystal);
        rotationTicks = 10;

        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity other
                && other.squaredDistanceTo(crystal.getX(), crystal.getY(), crystal.getZ()) <= 144) {
                crystalManager.setDead(other.getId(), System.currentTimeMillis());
            }
        }

        if (prevSlot != -1 && antiWeakness.get() == Switch.SILENT) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(prevSlot));
        }
    }

    /**
     * Kiểm tra item có phải vũ khí không (thay thế instanceof SwordItem/PickaxeItem).
     */
    private boolean isWeaponItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        String itemId = item.toString().toLowerCase();
        return itemId.contains("sword")
            || itemId.contains("axe")
            || itemId.contains("pickaxe")
            || itemId.contains("shovel");
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private boolean shouldPause() {
        if (mc.player == null || mc.world == null) return true;

        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        boolean mainHand = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;

        if (pauseOnMine.get() && mc.interactionManager.isBreakingBlock() && !offhand) return true;
        if (autoSwitch.get() == Switch.NONE && !offhand && !mainHand) return true;
        if ((autoSwitch.get() == Switch.SILENT || autoSwitch.get() == Switch.NORMAL)
            && !InvUtils.findInHotbar(Items.END_CRYSTAL).found() && !offhand) return true;
        if (autoSwitch.get() == Switch.INVENTORY
            && !InvUtils.find(Items.END_CRYSTAL).found() && !offhand) return true;
        if (pauseOnEat.get() && mc.player.isUsingItem()) return true;
        if (pauseOnInv.get() && mc.currentScreen != null) return true;
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < pauseHP.get()) return true;

        return false;
    }

    private boolean canSee(Vec3d target) {
        if (mc.player == null || mc.world == null) return false;
        Vec3d eyePos = mc.player.getEyePos();
        BlockHitResult result = mc.world.raycast(new RaycastContext(
            eyePos, target,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        if (result == null) return true;
        double hitDist = result.getPos().distanceTo(eyePos);
        double targetDist = target.distanceTo(eyePos);
        return hitDist >= targetDist - 0.5;
    }

    private boolean isFacePlace() {
        return target != null
            && target.getHealth() + target.getAbsorptionAmount() <= facePlaceHp.get();
    }

    private boolean shouldOverrideMaxSelfDmg(float damage, float selfDamage) {
        if (target == null || mc.player == null) return false;

        boolean targetSafe = target.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING
            || target.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;
        boolean playerSafe = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING
            || mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;

        float targetHp = target.getHealth() + target.getAbsorptionAmount();
        float playerHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        boolean canPop = damage > targetHp && targetSafe;
        boolean canKill = damage > targetHp && !targetSafe;
        boolean canPopSelf = selfDamage > playerHp && playerSafe;
        boolean canKillSelf = selfDamage > playerHp && !playerSafe;

        if (canPopSelf && canKill && sacrificeTotem.get()) return true;

        return selfDamage > maxSelfDamage.get() && (canPop || canKill) && !canKillSelf && !canPopSelf;
    }

    private boolean wouldHitFriend(Vec3d crystalVec, float currentSelfDamage) {
        if (mc.world == null) return false;

        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player) continue;
            if (!Friends.get().isFriend(pl)) continue;

            float friendDamage = ExplosionUtility.getAutoCrystalDamage(
                crystalVec, pl, extrapolation.get(), ignoreTerrain.get());

            if (friendDamage > currentSelfDamage + 2) return true;
        }
        return false;
    }

    private void swingHand(boolean offHand, boolean attack) {
        switch (swingMode.get()) {
            case Both -> mc.player.swingHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
            case Break -> {
                if (attack) mc.player.swingHand(Hand.MAIN_HAND);
                else mc.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(Hand.MAIN_HAND));
            }
            case Place -> {
                if (!attack) mc.player.swingHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
                else mc.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.HandSwingC2SPacket(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND));
            }
        }
    }

    private float randomRange(float min, float max) {
        return min + (max - min) * random.nextFloat();
    }

    private int getSelfPredictTicks() {
        int ping = 0;
        try {
            if (mc.getNetworkHandler() != null && mc.player != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
                ping = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency();
            }
        } catch (Exception ignored) {}
        return (int) Math.ceil((ping * 1.5f) / 50f);
    }
}