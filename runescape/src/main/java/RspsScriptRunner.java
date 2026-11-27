import java.util.List;

/**
 * 78Legacy v4 "Bruine Bonen Technologia": runs a script (RspsScripts) on its hotkey, on the client thread.
 *
 * Order: the steps run in the listed order. Consecutive Wear steps are one gear switch (RspsGearSwitcher.equipItems: only
 * items in the backpack and not worn, clicked as the slot shows them); the steps after it go out once those clicks are out,
 * so a prayer or an attack never jumps ahead of the gear. Everything else is one click each, in the same client cycle.
 * Fairness (unchanged since v1): one key press = the clicks a player makes; nothing without a key press; target steps only
 * ever act on the player you yourself attacked or cast on last; the owner's switches still gate the target steps
 * (allowTargetKeys), step under (allowWalkKey) and scripts that mix kinds of steps (allowPresets).
 */
final class RspsScriptRunner {

    /** Default hotkeys: scripts 1-8 = the v3.2 presets, 9-12 = the v3 Spell A-D keys, 13-15 free. Same as the server. */
    static final String[] DEFAULT_KEYS = {"CTRL+F11", "CTRL+F12", "CTRL+SHIFT+F11", "CTRL+F7", "CTRL+F8", "CTRL+F9", "CTRL+F10", "NONE",
        "CTRL+F2", "CTRL+F3", "CTRL+F4", "CTRL+F5", "NONE", "NONE", "NONE"};

    private static final String ID = RspsPkHotkeys.ID;

    /** The script that still waits for its gear clicks (0 = none), and when it started. Client thread only. */
    private static int running;
    /** Bumped by every new run or cancel: a gear follow-up of an older run never continues. */
    private static int generation;
    /** Status panel line: the last script that ran, e.g. "Script 4 · G maul". Read by the overlay. */
    static volatile String lastRun = "";
    static volatile long lastRunAt;

    /** v5: listeners told (client thread) when script n really starts, e.g. the sidebar card that flashes. */
    private static final java.util.List<java.util.function.IntConsumer> RUN_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<java.util.function.IntConsumer>();
    /** v5 timing: when the current press started (System.nanoTime) and which script, for the "same tick" log line. */
    private static long pressNanos;
    private static int pressScript;

    private RspsScriptRunner() {
    }

    /** v5: [listener] gets the script number each time a script starts (client thread; Swing listeners must invokeLater). */
    static void addRunListener(java.util.function.IntConsumer listener) {
        RUN_LISTENERS.add(listener);
    }

    static String title(int n) {
        String custom = RspsScripts.customName(n);
        return custom.isEmpty() ? "Script " + n : "Script " + n + " · " + custom;
    }

    /** The hotkey of script [n]; false = it does not fit the open prayer book / spellbook (the next action on the key may). */
    static boolean run(int n) {
        if (!RspsScripts.enabled(n)) {
            RspsPkHotkeys.say(title(n) + " is switched off (its box in Script Config).");
            return true;
        }
        List<RspsScripts.Step> steps = RspsScripts.steps(n);
        if (steps.isEmpty()) {
            RspsPkHotkeys.say(title(n) + " is empty - add steps in Script Config.");
            return true;
        }
        if (running == n && RspsHudConfig.bool(ID + ".ignoreDuplicateStarts")) {
            RspsPkHotkeys.log(title(n) + " still running - duplicate start ignored");
            return true;
        }
        boolean gear = false, prayer = false, target = false, other = false, prayerFits = false;
        for (RspsScripts.Step s : steps) {
            String kind = kind(s.type);
            if (kind.equals("gear")) gear = true;
            else if (kind.equals("prayer")) {
                prayer = true;
                // Quick prayers work in both books (Codex review v4 #2).
                if (RspsScripts.QUICK.equals(s.type) || RspsPkHotkeys.inActiveBook(s.arg)) prayerFits = true;
            } else if (kind.equals("target")) target = true;
            else other = true;
        }
        // A prayer-only script whose prayers are all in the other book gives way (e.g. Smite here, Soul Split on a second
        // script with the same key).
        if (prayer && !gear && !target && !other && !prayerFits) {
            return false;
        }
        int kinds = (gear ? 1 : 0) + (prayer ? 1 : 0) + (target ? 1 : 0) + (other ? 1 : 0);
        if (kinds > 1 && !RspsHudConfig.bool(ID + ".allowPresets")) {
            RspsPkHotkeys.say("Scripts with several kinds of steps are switched off on this server.");
            return true;
        }
        // A script of only spells: false when none of them is in the open spellbook (the next action on the key may run).
        boolean spellOnly = !gear && !prayer && !other && onlySpells(steps);
        // A script of only spells that has none in the open spellbook gives way before it cancels anything
        // (Codex review v4 #6): the next action on the key may be the other book's spell.
        if (spellOnly && !anySpellAvailable(steps)) {
            return false;
        }
        // A new press replaces a script (or gear key) that still waits for its gear clicks (Codex review v4 #4).
        RspsGearSwitcher.cancel();
        running = 0;
        generation++;
        lastRun = title(n);
        lastRunAt = System.currentTimeMillis();
        pressNanos = System.nanoTime();
        pressScript = n;
        RspsPkHotkeys.log("run " + title(n) + ": " + RspsScripts.encode(steps));
        for (java.util.function.IntConsumer listener : RUN_LISTENERS) {
            try {
                listener.accept(n);
            } catch (RuntimeException e) {
                // A sidebar listener must never stop a script.
            }
        }
        boolean any = exec(n, steps, 0, generation);
        return !spellOnly || any;
    }

    /** True when at least one spell step's spell is in the spellbook that is open now (same test as RspsPkTarget). */
    private static boolean anySpellAvailable(List<RspsScripts.Step> steps) {
        for (RspsScripts.Step s : steps) {
            int i = RspsPkData.spell(s.arg);
            if (i >= 0 && RspsPkHotkeys.isOpen(RspsPkData.SPELL_BOOK[i])
                && InterfaceList.getComponent((RspsPkData.SPELL_BOOK[i] << 16) | RspsPkData.SPELL_COMPONENT[i], -1) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean onlySpells(List<RspsScripts.Step> steps) {
        for (RspsScripts.Step s : steps) {
            if (!RspsScripts.CAST.equals(s.type) && !RspsScripts.SELECT.equals(s.type)) return false;
        }
        return true;
    }

    static String kind(String type) {
        if (RspsScripts.WEAR.equals(type) || RspsScripts.SET.equals(type)) return "gear";
        if (RspsScripts.PRAY_ON.equals(type) || RspsScripts.PRAY_OFF.equals(type) || RspsScripts.PRAY_TOGGLE.equals(type)
            || RspsScripts.QUICK.equals(type)) return "prayer";
        if (RspsScripts.CAST.equals(type) || RspsScripts.SELECT.equals(type) || RspsScripts.ATTACK.equals(type)
            || RspsScripts.STEP.equals(type)) return "target";
        return "other";
    }

    /** Runs steps [from..] of script [n]; returns true when a spell step went out (for the spell-only fallback). */
    private static boolean exec(final int n, final List<RspsScripts.Step> steps, int from, final int gen) {
        boolean spellOut = false;
        int i = from;
        while (i < steps.size()) {
            RspsScripts.Step s = steps.get(i);
            if (kind(s.type).equals("gear")) {
                java.util.List<Integer> ids = new java.util.ArrayList<Integer>();
                int j = i;
                while (j < steps.size() && kind(steps.get(j).type).equals("gear")) {
                    RspsScripts.Step g = steps.get(j);
                    if (RspsScripts.WEAR.equals(g.type)) {
                        ids.add(g.intArg());
                    } else {
                        int set = g.intArg();
                        if (set >= 1 && set <= RspsGearSets.COUNT) {
                            for (int id : RspsGearSets.items(set)) ids.add(id);
                        }
                    }
                    j++;
                }
                int[] items = new int[ids.size()];
                for (int k = 0; k < items.length; k++) items[k] = ids.get(k);
                final int next = j;
                RspsGearSwitcher.equipItems(title(n), items, new Runnable() {
                    @Override
                    public void run() {
                        if (gen != generation) {
                            return; // a newer press or a cancel replaced this run
                        }
                        running = 0;
                        exec(n, steps, next, gen);
                    }
                });
                // Still waiting for the backpack display: this script runs on (duplicate starts are ignored meanwhile).
                if (gen == generation && RspsGearSwitcher.busy()) {
                    running = n;
                }
                return spellOut;
            }
            spellOut |= step(n, s);
            i++;
        }
        if (gen == generation) {
            running = 0;
            if (pressScript == n) {
                // v5 timing: key press to the last step's click, in the client log (all in one client cycle = same tick).
                RspsPkHotkeys.log(String.format(java.util.Locale.ROOT, "%s: %d steps sent %.2f ms after the key", title(n), steps.size(), (System.nanoTime() - pressNanos) / 1e6));
                pressScript = 0;
            }
        }
        return spellOut;
    }

    /** One non-gear step; true when it was a spell that went out. */
    private static boolean step(int n, RspsScripts.Step s) {
        String t = s.type;
        if (RspsScripts.PRAY_ON.equals(t) || RspsScripts.PRAY_OFF.equals(t) || RspsScripts.PRAY_TOGGLE.equals(t)) {
            prayerStep(t, s.arg);
        } else if (RspsScripts.QUICK.equals(t)) {
            RspsPkHotkeys.quickPrayers();
        } else if (RspsScripts.SPEC.equals(t)) {
            RspsPkHotkeys.special();
        } else if (RspsScripts.EAT.equals(t)) {
            RspsPkHotkeys.eat();
        } else if (RspsScripts.BREW.equals(t)) {
            RspsPkHotkeys.drink("brew", "No brew left.");
        } else if (RspsScripts.RESTORE.equals(t)) {
            RspsPkHotkeys.drink("restore", "No restore left.");
        } else if (RspsScripts.VENG.equals(t)) {
            RspsPkHotkeys.vengeance();
        } else if (RspsScripts.STEP.equals(t)) {
            if (!RspsHudConfig.bool(ID + ".allowWalkKey")) {
                RspsPkHotkeys.say("The walk hotkey is switched off on this server.");
            } else {
                RspsPkTarget.stepUnder();
            }
        } else if (RspsScripts.ATTACK.equals(t)) {
            if (!RspsHudConfig.bool(ID + ".allowTargetKeys")) {
                RspsPkHotkeys.say("Target hotkeys are switched off on this server.");
            } else {
                RspsPkTarget.attack();
            }
        } else if (RspsScripts.CAST.equals(t) || RspsScripts.SELECT.equals(t)) {
            int i = RspsPkData.spell(s.arg);
            if (i < 0) {
                return false;
            }
            if (RspsScripts.CAST.equals(t)) {
                if (!RspsHudConfig.bool(ID + ".allowTargetKeys")) {
                    RspsPkHotkeys.say("Target hotkeys are switched off on this server.");
                    return true;
                }
                if (RspsPkTarget.cast(RspsPkData.SPELL_BOOK[i], RspsPkData.SPELL_COMPONENT[i], RspsPkData.SPELL_NAMES[i])) {
                    return true;
                }
            } else if (RspsPkTarget.select(RspsPkData.SPELL_BOOK[i], RspsPkData.SPELL_COMPONENT[i], RspsPkData.SPELL_NAMES[i])) {
                return true;
            }
            RspsPkHotkeys.say(title(n) + ": " + RspsPkData.SPELL_NAMES[i] + " is not in your open spellbook.");
        }
        return false;
    }

    /**
     * Prayer ON / OFF / toggle: one click on the prayer, only when it changes something. The state used is the one the
     * prayer WILL have once the clicks already sent arrive (RspsPrayerModel, incl. the prayers the server switches off),
     * so several prayer steps in one press end where the last step says (Codex review v4 #5/#7, review 3 B1-B3).
     */
    private static void prayerStep(String type, String id) {
        int i = RspsPkData.prayer(id);
        if (i < 0) {
            return;
        }
        if (!RspsPkHotkeys.inActiveBook(id)) {
            RspsPkHotkeys.say(RspsPkData.PRAYER_NAMES[i] + " is not in your active prayer book - skipped.");
            return;
        }
        if (RspsPrayerModel.quickPending()) {
            // The orb just changed a set of prayers this client does not know yet: no guessing (no retry either).
            RspsPkHotkeys.say(RspsPkData.PRAYER_NAMES[i] + " skipped: quick prayers were just switched - press again.");
            return;
        }
        int on = RspsPrayerModel.state(i);
        if (on < 0 && !RspsScripts.PRAY_TOGGLE.equals(type)) {
            RspsPkHotkeys.say(RspsPkData.PRAYER_NAMES[i] + ": state unknown, not clicked.");
            return;
        }
        int want = RspsScripts.PRAY_ON.equals(type) ? 1 : RspsScripts.PRAY_OFF.equals(type) ? 0 : (on == 1 ? 0 : 1);
        if (on == want) {
            return; // already as asked
        }
        RspsPkHotkeys.prayerSlot(RspsPkData.PRAYER_SLOT[i]); // counts itself in RspsPrayerModel when sent
    }

    /** RspsGearSwitcher.cancel: a waiting gear batch was dropped (plugin off, left the game, new press). */
    static void gearCancelled() {
        running = 0;
    }

    /** "Cancel running script": drops a script that still waits for its gear clicks. */
    static void cancel() {
        generation++;
        if (running != 0) {
            RspsPkHotkeys.say(title(running) + " cancelled.");
        } else if (!RspsGearSwitcher.busy()) {
            RspsPkHotkeys.say("Nothing to cancel."); // feedback for the cancel key (live test 2026-10-12)
        }
        running = 0;
        RspsGearSwitcher.cancel();
    }

    // ---- one-time move of the v3.2 presets and Spell A-D keys into scripts ---------------------------------------------

    /** Client thread, every cycle: once all old settings arrived from the server, turns them into scripts (once). */
    static void tick() {
        RspsPrayerModel.reconcile();
        if (RspsScripts.isMigrated() || !RspsHudConfig.receivedLegacy()) {
            return;
        }
        migrate();
    }

    static void migrate() {
        for (int n = 1; n <= RspsPkHotkeys.PRESETS; n++) {
            if (!RspsScripts.steps(n).isEmpty() || !RspsScripts.customName(n).isEmpty()) continue;
            String p = ID + ".preset" + n;
            java.util.List<RspsScripts.Step> list = new java.util.ArrayList<RspsScripts.Step>();
            int[] own = RspsGearSets.presetItems(n);
            String gear = RspsHudConfig.get(p + "Gear");
            String name = "";
            if (own.length > 0) {
                for (int id : own) list.add(new RspsScripts.Step(RspsScripts.WEAR, Integer.toString(id)));
            } else if (gear.matches("\\d{1,2}")) {
                int g = Integer.parseInt(gear);
                // GEARSWITCHER-WEG 27-11: the preset's gear set becomes its Wear item steps (no gear-set steps any more).
                if (g >= 1 && g <= RspsGearSets.COUNT) for (int id : RspsGearSets.items(g)) list.add(new RspsScripts.Step(RspsScripts.WEAR, Integer.toString(id)));
                if (g >= 1 && g <= RspsGearSets.COUNT && !RspsGearSets.name(g).equals("Set " + g)) name = RspsGearSets.name(g);
            }
            String type = RspsHudConfig.bool(p + "PrayerToggle") ? RspsScripts.PRAY_TOGGLE : RspsScripts.PRAY_ON;
            String one = RspsHudConfig.get(p + "PrayerOne");
            String two = RspsHudConfig.get(p + "PrayerTwo");
            if (RspsPkData.prayer(one) >= 0) list.add(new RspsScripts.Step(type, one));
            if (RspsPkData.prayer(two) >= 0 && !two.equals(one)) list.add(new RspsScripts.Step(type, two));
            if (name.isEmpty() && own.length == 0 && !gear.matches("\\d{1,2}") && RspsPkData.prayer(one) >= 0) {
                name = RspsPkData.PRAYER_NAMES[RspsPkData.prayer(one)];
            }
            if (RspsHudConfig.bool(p + "Target")) {
                String doWhat = RspsHudConfig.get(p + "TargetDo");
                String spell = RspsHudConfig.get(p + "Spell");
                if ("CAST".equals(doWhat)) list.add(new RspsScripts.Step(RspsScripts.CAST, spell));
                else if ("SELECT".equals(doWhat)) list.add(new RspsScripts.Step(RspsScripts.SELECT, spell));
                else if ("STEP".equals(doWhat)) list.add(new RspsScripts.Step(RspsScripts.STEP, ""));
                else list.add(new RspsScripts.Step(RspsScripts.ATTACK, ""));
            }
            if (!RspsHudConfig.bool(p + "On")) RspsScripts.setEnabled(n, false);
            if (!name.isEmpty()) RspsScripts.setName(n, name);
            RspsScripts.setSteps(n, list);
        }
        String[] letters = {"A", "B", "C", "D"};
        for (int k = 0; k < 4; k++) {
            int n = 9 + k;
            if (!RspsScripts.steps(n).isEmpty() || !RspsScripts.customName(n).isEmpty()) continue;
            String spell = RspsHudConfig.get(ID + ".spell" + letters[k]);
            if (RspsPkData.spell(spell) < 0) continue;
            boolean select = "SELECT".equals(RspsHudConfig.get(ID + ".spell" + letters[k] + "Mode"));
            RspsScripts.setName(n, RspsPkData.SPELL_NAMES[RspsPkData.spell(spell)]);
            java.util.List<RspsScripts.Step> list = new java.util.ArrayList<RspsScripts.Step>();
            list.add(new RspsScripts.Step(select ? RspsScripts.SELECT : RspsScripts.CAST, spell));
            RspsScripts.setSteps(n, list);
        }
        RspsScripts.markMigrated();
        RspsPkHotkeys.log("v4: presets and spell keys moved into scripts");
    }
}
