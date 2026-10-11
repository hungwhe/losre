package com.hwell.client.gui;

import com.hwell.client.HwellClient;
import com.hwell.client.core.ClientModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 230;
    private static final int ROW_H = 24;
    private int panelX, panelY;

    public ClickGuiScreen() { super(Text.literal("HWELL Client")); }
    @Override protected void init() { panelX = (width - PANEL_W) / 2; panelY = (height - 80) / 2; }

    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Avoid vanilla blur: it can crash when another screen/mod already blurred this frame.
        ctx.fill(0, 0, width, height, 0x99000000);
        int panelH = 42 + HwellClient.MODULES.all().size() * ROW_H;
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE101014);
        ctx.fill(panelX, panelY, panelX + PANEL_W, panelY + 3, 0xFFE21B2D);
        ctx.drawText(textRenderer, "HWELL CLIENT", panelX + 12, panelY + 12, 0xFFFFFFFF, false);
        int y = panelY + 34;
        for (ClientModule module : HwellClient.MODULES.all()) {
            boolean hovered = mouseX >= panelX && mouseX <= panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_H;
            ctx.fill(panelX + 6, y, panelX + PANEL_W - 6, y + ROW_H - 2,
                hovered ? 0xFF303039 : 0xFF1C1C23);
            ctx.drawText(textRenderer, module.name(), panelX + 14, y + 7, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, module.enabled() ? "ON" : "OFF", panelX + PANEL_W - 36,
                y + 7, module.enabled() ? 0xFF55FF88 : 0xFFAAAAAA, false);
            y += ROW_H;
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            int y = panelY + 34;
            for (ClientModule module : HwellClient.MODULES.all()) {
                if (mouseX >= panelX && mouseX <= panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_H) {
                    module.toggle(); return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(input);
    }
    @Override public boolean shouldPause() { return false; }
}
