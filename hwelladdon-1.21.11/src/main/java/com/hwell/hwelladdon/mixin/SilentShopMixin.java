package com.hwell.hwelladdon.mixin;

import com.hwell.hwelladdon.modules.AutoBuyShop;
import com.hwell.hwelladdon.modules.AutoEnchant;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
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

        // ✅ Sử dụng Modules.get() thay vì getInstance()
        AutoBuyShop shopModule = Modules.get().get(AutoBuyShop.class);
        if (shopModule != null && shopModule.isActive() && shopModule.isSilentMode()) {
            ScreenHandlerType<?> type = packet.getScreenHandlerType();
            String rawTitle = packet.getName().getString();
            boolean isShopType = type == ScreenHandlerType.GENERIC_9X3
                || type == ScreenHandlerType.GENERIC_9X4
                || type == ScreenHandlerType.GENERIC_9X5
                || type == ScreenHandlerType.GENERIC_9X6;

            if (isShopType) {
                String lower = rawTitle.toLowerCase();
                boolean titleLooksLikeShop = rawTitle.length() == 4 || lower.startsWith("s") || lower.startsWith("mua") || lower.startsWith("gear");
                if (titleLooksLikeShop) {
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
        }

        // Xử lý cho Auto Enchant
        AutoEnchant enchantModule = Modules.get().get(AutoEnchant.class);
        if (enchantModule != null && enchantModule.isActive() && enchantModule.isSilentMode()) {
            try {
                int syncId = packet.getSyncId();
                ScreenHandler handler = packet.getScreenHandlerType().create(syncId, mc.player.getInventory());
                if (handler != null) {
                    mc.player.currentScreenHandler = handler;
                    ci.cancel();
                }
            } catch (Exception e) {
                LOG.error("[SilentEnchant] Failed", e);
            }
        }
    }
}