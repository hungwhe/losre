package com.hwell.hwelladdon.mixin;

import com.hwell.hwelladdon.modules.AutoBuyShop;
import com.hwell.hwelladdon.modules.AutoEnchant;
import com.hwell.hwelladdon.utils.GhostScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
        } catch (Throwable t) {
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

        // ✅ Sử dụng Modules.get() thay vì getInstance()
        AutoBuyShop shopModule = Modules.get().get(AutoBuyShop.class);
        if (shopModule != null && shopModule.isActive() && shopModule.isSilentMode()) {
            if (shopModule.isFreezing()) return null;
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                return getGhostScreen();
            }
        }

        AutoEnchant enchantModule = Modules.get().get(AutoEnchant.class);
        if (enchantModule != null && enchantModule.isActive() && enchantModule.isSilentMode()) {
            if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                return getGhostScreen();
            }
        }

        return null;
    }
}