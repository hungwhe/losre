package com.hwell.client.core;

public abstract class ClientModule {
    private final String name;
    private final String category;
    private boolean enabled;

    protected ClientModule(String name, String category) { this.name = name; this.category = category; }
    public String name() { return name; }
    public String category() { return category; }
    public boolean enabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }
    public void tick() { if (enabled) onTick(); }
    protected void onEnable() {}
    protected void onDisable() {}
    protected void onTick() {}
}
