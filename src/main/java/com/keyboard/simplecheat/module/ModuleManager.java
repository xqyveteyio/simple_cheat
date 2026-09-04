package com.keyboard.simplecheat.module;

import com.keyboard.simplecheat.module.combat.KillAura;
import com.keyboard.simplecheat.module.combat.RangedDefense;
import com.keyboard.simplecheat.module.movement.AutoDodge;
import com.keyboard.simplecheat.module.movement.Scaffold;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    private final KillAura killAura = new KillAura();
    private final RangedDefense rangedDefense = new RangedDefense();
    private final AutoDodge autoDodge = new AutoDodge();
    private final Scaffold scaffold = new Scaffold();

    public ModuleManager() {
        modules.add(killAura);
        modules.add(rangedDefense);
        modules.add(autoDodge);
        modules.add(scaffold);
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

    public AutoDodge getAutoDodge() {
        return autoDodge;
    }

    public Scaffold getScaffold() {
        return scaffold;
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
