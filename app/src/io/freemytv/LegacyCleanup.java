package io.freemytv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Pure upgrade decisions, kept separate so unrelated system settings can be tested. */
final class LegacyCleanup {
    private LegacyCleanup() {}
    static boolean ownsDream(String components, String pkg) {
        for (String component : components.split(",")) if (component.trim().startsWith(pkg + "/")) return true;
        return false;
    }
    static String otherDreams(String components, String pkg) {
        List<String> others = new ArrayList<>();
        for (String component : components.split(",")) {
            component = component.trim();
            if (!component.isEmpty() && !component.startsWith(pkg + "/")) others.add(component);
        }
        return String.join(",", others);
    }
    static boolean restoreDreamApp(String dreamPkg, String homePkg, Collection<String> disabled, Collection<String> keptOff) {
        return dreamPkg != null && !dreamPkg.equals(homePkg) && disabled.contains(dreamPkg) && !keptOff.contains(dreamPkg);
    }
    /** null means unchanged; empty means remove only our last service entry. */
    static String withoutService(String current, String ours) {
        if (!Arrays.asList(current.split(":")).contains(ours)) return null;
        List<String> others = new ArrayList<>();
        for (String service : current.split(":")) {
            if (service.isEmpty() || service.equals(ours)) continue;
            if (!service.matches("[\\w.]+/[\\w.$]+")) throw new IllegalArgumentException("辅助功能服务列表格式异常");
            others.add(service);
        }
        return String.join(":", others);
    }
}
