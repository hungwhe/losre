package com.hwell.hwelladdon;

import com.hwell.hwelladdon.hud.ArmorHud;
import com.hwell.hwelladdon.hud.InventoryHud;
import com.hwell.hwelladdon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import com.hwell.hwelladdon.modules.AutoCrystal;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class HwellAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // ============================================================
    // 3 CATEGORY CHÍNH
    // ============================================================
    public static final Category PVP = new Category("HWELL PVP",
        new ItemStack(Items.END_CRYSTAL));

    public static final Category MOVEMENT = new Category("HWELL MOVEMENT",
        new ItemStack(Items.FEATHER));

    public static final Category AUTO_SHOP = new Category("HWELL AUTO SHOP",
        new ItemStack(Items.EMERALD));

    public static final HudGroup HUD_GROUP = new HudGroup("HWELL");

    @Override
    public void onInitialize() {
        LOG.info("Initializing HwellAddon");

        // ============================================================
        // MODULES - HWELL PVP
        // ============================================================
        Modules.get().add(new AutoTotemSlot());
        Modules.get().add(new AutoTotemProMax());
        Modules.get().add(new AutoFillTotem());
        Modules.get().add(new AutoCrystal());
        // ============================================================
        // MODULES - HWELL MOVEMENT
        // ============================================================
        // (Trống - thêm sau)
        // ============================================================
        // MODULES - HWELL AUTO SHOP
        // ============================================================
        Modules.get().add(new AutoBuyShop());
        Modules.get().add(new AutoBuyShop2());
        Modules.get().add(new AutoBuyShop3());
        Modules.get().add(new AutoBuyShop4());

        // ============================================================
        // HUD ELEMENTS
        // ============================================================
        Hud.get().register(InventoryHud.INFO);
        Hud.get().register(ArmorHud.INFO);
        LOG.info("Registered HUD elements");

        // Listener cho auto-enable
        MeteorClient.EVENT_BUS.subscribe(new AutoEnableListener());
        LOG.info("AutoEnableListener registered!");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(PVP);
        Modules.registerCategory(MOVEMENT);
        Modules.registerCategory(AUTO_SHOP);
    }

    @Override
    public String getPackage() {
        return "com.hwell.hwelladdon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-hwelladdon");
    }
}