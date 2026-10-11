package com.hwell.hwelladdon.mixin;

import com.hwell.hwelladdon.modules.AutoBuyShopBase;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SilentShopMixin {
    private static final Logger LOG = LoggerFactory.getLogger("SilentShop");

    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        AutoBuyShopBase active = AutoBuyShopBase.getActive();
        if (active == null || !active.isActive() || !active.isSilentMode()) return;

        ScreenHandlerType<?> type = packet.getScreenHandlerType();
        String rawTitle = packet.getName().getString();

        boolean isShopType = type == ScreenHandlerType.GENERIC_9X3
            || type == ScreenHandlerType.GENERIC_9X4
            || type == ScreenHandlerType.GENERIC_9X5
            || type == ScreenHandlerType.GENERIC_9X6;

        if (!isShopType) return;

        String lower = rawTitle.toLowerCase();
        boolean titleLooksLikeShop = rawTitle.length() == 4
            || lower.startsWith("s")
            || lower.startsWith("mua")
            || lower.startsWith("gear");

        if (!titleLooksLikeShop) return;

        try {
            int syncId = packet.getSyncId();
            ScreenHandler handler = type.create(syncId, mc.player.getInventory());
            if (handler != null) {
                mc.player.currentScreenHandler = handler;
                ci.cancel();
            }
        } catch (Exception e) {
            LOG.error("[SilentShop] Failed", e);
        }
    }
}