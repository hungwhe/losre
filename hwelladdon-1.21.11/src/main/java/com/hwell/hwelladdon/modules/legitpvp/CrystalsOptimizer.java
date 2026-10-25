package com.hwell.hwelladdon.modules.legitpvp;

import com.hwell.hwelladdon.HwellAddon;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

public class CrystalsOptimizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // CRYSTAL OPTIMIZATION
    // ============================================================
    private final Setting<Boolean> instantPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-place")
        .description("Pha lê bạn đặt sẽ xuất hiện ngay lập tức trên màn hình.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-break")
        .description("Pha lê bạn đánh sẽ biến mất ngay lập tức.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> distanceCulling = sgGeneral.add(new BoolSetting.Builder()
        .name("distance-culling")
        .description("Ẩn các pha lê ở xa để tăng FPS.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> staticCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("static-crystals")
        .description("Hiển thị pha lê đứng yên, không xoay và nhấp nhô.")
        .defaultValue(false)
        .build()
    );

    // ============================================================
    // ANCHOR OPTIMIZATION
    // ============================================================
    private final Setting<Boolean> instantAnchorReplace = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-anchor-replace")
        .description("Đặt lại respawn anchor diễn ra tức thì, không bị khựng.")
        .defaultValue(true)
        .build()
    );

    // ============================================================
    // PERFORMANCE
    // ============================================================
    private final Setting<Boolean> reduceParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("reduce-particles")
        .description("Giảm số lượng hạt (particle) để tăng FPS.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableExplosionParticles = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-explosion-particles")
        .description("Tắt hoàn toàn hạt của vụ nổ pha lê.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> particleLimit = sgGeneral.add(new IntSetting.Builder()
        .name("particle-limit")
        .description("Giới hạn tổng số hạt tối đa.")
        .defaultValue(500).range(100, 2000).sliderRange(100, 2000).build()
    );

    public CrystalsOptimizer() {
        super(HwellAddon.PVP_SUPER_LEGIT,
            "Hwell CrystalsOptimizer",
            "Tối ưu hóa Crystal & Anchor PvP, giúp thao tác mượt mà và tăng FPS.");
    }
}