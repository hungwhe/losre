package com.hwell.client;

import com.hwell.client.core.ModuleManager;
import com.hwell.client.gui.ClickGuiScreen;
import com.hwell.client.modules.ExampleModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class HwellClient implements ClientModInitializer {
    public static final String NAME = "HWELL";
    public static final ModuleManager MODULES = new ModuleManager();
    private KeyBinding openGuiKey;
    private static final KeyBinding.Category HWELL_CATEGORY =
        KeyBinding.Category.create(Identifier.of("hwellclient", "main"));

    @Override public void onInitializeClient() {
        // Framework bootstrap. Existing Meteor module logic is retained in migration-reference;
        // each module must be ported from Meteor APIs before it can be registered here.
        MODULES.register(new ExampleModule());
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hwellclient.clickgui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
            HWELL_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClickGuiScreen());
            }
            MODULES.tick();
        });
    }
}
