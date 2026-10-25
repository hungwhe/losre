package com.hwell.hwelladdon.modules.legitpvp;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

public class NoPractical extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> keepFood = sgGeneral.add(new BoolSetting.Builder()
        .name("food")
        .description("Không bị chậm khi ăn.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepPotion = sgGeneral.add(new BoolSetting.Builder()
        .name("potion")
        .description("Không bị chậm khi uống potion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepShield = sgGeneral.add(new BoolSetting.Builder()
        .name("shield")
        .description("Không bị chậm khi block shield.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepBow = sgGeneral.add(new BoolSetting.Builder()
        .name("bow")
        .description("Không bị chậm khi charge bow.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sprintPacket = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-packet")
        .description("Gửi packet START_SPRINTING mỗi tick (aggressive).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> forceSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("force-sprint")
        .description("Ép setSprinting(true) mỗi tick khi đang dùng item.")
        .defaultValue(true)
        .build()
    );

    public NoPractical() {
        super(HwellAddon.PVP_SUPER_LEGIT,
            "Hwell NoPractical",
            "NoSlow — giữ tốc độ khi ăn/uống/block/charge bow.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isUsingItem()) return;
        if (mc.player.isSneaking() || mc.player.isRiding()) return;
        if (!shouldBypass()) return;

        if (forceSprint.get()) {
            mc.player.setSprinting(true);
            if (mc.options != null && mc.options.sprintKey != null) {
                mc.options.sprintKey.setPressed(true);
            }
        }

        if (sprintPacket.get() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(
                new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING)
            );
        }
    }

    private boolean shouldBypass() {
        if (mc.player == null) return false;
        ItemStack active = mc.player.getActiveItem();
        if (active.isEmpty()) return false;

        // 1.21.x: get(FOOD) trả về FoodComponent hoặc null
        if (active.get(DataComponentTypes.FOOD) != null && keepFood.get()) return true;
        if (active.getItem() == Items.POTION && keepPotion.get()) return true;
        if (active.getItem() == Items.SHIELD && keepShield.get()) return true;
        if (active.getItem() instanceof BowItem && keepBow.get()) return true;

        return false;
    }
}