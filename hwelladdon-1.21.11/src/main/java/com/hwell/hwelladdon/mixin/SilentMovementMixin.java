package com.hwell.hwelladdon.mixin;

import com.hwell.hwelladdon.utils.GhostScreen;
import com.hwell.hwelladdon.modules.AutoBuyShopBase;
import com.hwell.hwelladdon.utils.GhostScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.ScreenHandler;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public class SilentMovementMixin {

    private static final Logger LOG = LoggerFactory.getLogger("SilentMovement");
    private static Screen ghostScreen = null;
    private static boolean ghostFailed = false;

    private static Screen getGhostScreen() {
        if (ghostScreen != null) return ghostScreen;
        if (ghostFailed) return null;

        try {
            ghostScreen = new GhostScreen();
            LOG.info("[SilentMovement] GhostScreen created");
        } catch (Throwable t) {
            LOG.error("[SilentMovement] GhostScreen creation failed", t);
            ghostFailed = true;
            return null;
        }
        return ghostScreen;
    }

    @Redirect(
        method = "tickMovement",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;",
            opcode = Opcodes.GETFIELD
        )
    )
    private Screen redirectCurrentScreen(MinecraftClient mc) {
        Screen original = mc.currentScreen;
        if (original != null) return original;
        if (mc.player == null) return null;

        AutoBuyShopBase active = AutoBuyShopBase.getActive();
        if (active == null || !active.isActive() || !active.isSilentMode()) return null;

        // Freeze mode → block movement
        if (active.isFreezing()) return null;

        // Cho phép movement khi có container handler
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler != mc.player.playerScreenHandler) {
            Screen ghost = getGhostScreen();
            if (ghost != null) return ghost;
        }
        return null;
    }
}