import java.util.List;

/** GEARSWITCHER-WEG 27-11 (owner 'Alleen de plugin'): no gear-set keys, no set editor, no 'Wear gear set' step; the wear engine stays. */
public final class GearSwitcherRemovedRegressionTest {

    public static void main(String[] args) throws Exception {
        for (String t : RspsScripts.TYPES) require(!RspsScripts.SET.equals(t), "'Wear gear set' is no longer in the + Step list");
        require(RspsScripts.TYPES.length == RspsScripts.TYPE_NAMES.length, "step types and their names stay paired");
        List<RspsScripts.Step> steps = RspsScripts.parse("SET:3;ATTACK");
        for (RspsScripts.Step s : steps) require(!RspsScripts.SET.equals(s.type), "an old SET step is turned into Wear item steps");
        require(steps.size() >= 1 && RspsScripts.ATTACK.equals(steps.get(steps.size() - 1).type), "the steps after an old SET step stay");
        require(RspsScripts.parse("WEAR:4151;PRAYON:N_PIETY").size() == 2, "Wear item steps still parse");
        for (RspsHudConfig.Setting s : RspsHudConfig.SETTINGS) {
            require(!s.plugin.equals("gearswitcher"), "no Gear Switcher setting left on the page: " + s.key);
            require(s.type != RspsHudConfig.GEARSETS, "no gear set editor on the page");
        }
        boolean editor = true;
        try { Class.forName("RspsGearSetEditor"); } catch (ClassNotFoundException gone) { editor = false; }
        require(!editor, "RspsGearSetEditor is gone");
        boolean key = true;
        try { RspsGearSwitcher.class.getDeclaredMethod("key", rs2.client.event.keyboard.KeyLog.class); } catch (NoSuchMethodException gone) { key = false; }
        require(!key, "no SHIFT+F gear keys: RspsGearSwitcher.key is gone");
        RspsGearSwitcher.class.getDeclaredMethod("equipItems", String.class, int[].class, Runnable.class); // the engine stays
        System.out.println("GearSwitcherRemovedRegressionTest OK");
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }
}
