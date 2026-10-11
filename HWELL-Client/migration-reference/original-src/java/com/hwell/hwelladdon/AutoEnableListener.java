package com.hwell.hwelladdon;

import com.hwell.hwelladdon.modules.AutoBuyShopBase;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoEnableListener {
    private static final Logger LOG = LoggerFactory.getLogger("AutoEnable");
    private int checkCooldown;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (checkCooldown > 0) { checkCooldown--; return; }
        checkCooldown = 20;

        AutoBuyShopBase.checkAllAutoEnable();
    }
}