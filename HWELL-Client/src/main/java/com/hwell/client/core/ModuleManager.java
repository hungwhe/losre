package com.hwell.client.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private final List<ClientModule> modules = new ArrayList<>();
    public void register(ClientModule module) { modules.add(module); }
    public List<ClientModule> all() { return Collections.unmodifiableList(modules); }
    public void tick() { for (ClientModule module : modules) module.tick(); }
}
