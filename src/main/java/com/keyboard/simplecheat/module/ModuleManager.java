package com.keyboard.simplecheat.module;

import com.keyboard.simplecheat.module.combat.KillAura;
import com.keyboard.simplecheat.module.combat.RangedDefense;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    private final KillAura killAura = new KillAura();
    private final RangedDefense rangedDefense = new RangedDefense();

    public ModuleManager() {
        modules.add(killAura);
        modules.add(rangedDefense);
    }

    public List<Module> getModules() {
        return modules;
    }

    public KillAura getKillAura() {
        return killAura;
    }

    public RangedDefense getRangedDefense() {
        return rangedDefense;
    }

    public Module getById(String id) {
        for (Module module : modules) {
            if (module.getId().equals(id)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> getEnabled() {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.isEnabled()) {
                result.add(module);
            }
        }
        return result;
    }

    public void onTick() {
        for (Module module : modules) {
            if (module.isEnabled()) {
                module.onTick();
            }
        }
    }
}
