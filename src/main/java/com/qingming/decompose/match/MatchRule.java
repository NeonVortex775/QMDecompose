package com.qingming.decompose.match;

import com.qingming.decompose.util.Items;
import com.qingming.decompose.util.NbtUtil;
import com.qingming.decompose.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Match conditions for a table. Everything has to pass. */
public class MatchRule {

    private final List<Material> materials = new ArrayList<Material>();
    private final List<Short> datas = new ArrayList<Short>();
    private final List<String> namePatterns = new ArrayList<String>();
    private final List<String> nameContains = new ArrayList<String>();
    private final List<String> loreExact = new ArrayList<String>();
    private final List<String> loreContains = new ArrayList<String>();
    private final Map<String, String> nbt = new LinkedHashMap<String, String>();

    public static MatchRule parse(ConfigurationSection sec) {
        MatchRule r = new MatchRule();
        if (sec == null) {

            return r;
        }
        for (String s : names(sec, "material")) {
            String n = s.trim().toUpperCase();
            short data = 0;
            if (n.contains(":")) {
                String[] sp = n.split(":", 2);
                n = sp[0];
                try {
                    data = Short.parseShort(sp[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            Material m = Material.matchMaterial(n);
            if (m != null) {
                r.materials.add(m);
                r.datas.add(data);
            }
        }
        r.namePatterns.addAll(names(sec, "name"));
        r.nameContains.addAll(names(sec, "name-contains"));
        r.loreExact.addAll(names(sec, "lore"));
        r.loreContains.addAll(names(sec, "lore-contains"));

        ConfigurationSection nbtSec = sec.getConfigurationSection("nbt");
        if (nbtSec != null) {
            for (String key : nbtSec.getKeys(false)) {
                if (nbtSec.isConfigurationSection(key)) {
                    ConfigurationSection child = nbtSec.getConfigurationSection(key);
                    for (String k : child.getKeys(false)) {
                        if (!child.isConfigurationSection(k)) {
                            r.nbt.put(key + "." + k, String.valueOf(child.get(k)));
                        }
                    }
                } else {
                    r.nbt.put(key, String.valueOf(nbtSec.get(key)));
                }
            }
        }
        return r;
    }

    private static List<String> names(ConfigurationSection sec, String key) {
        List<String> out = new ArrayList<String>();
        if (sec.isList(key)) {
            out.addAll(sec.getStringList(key));
        } else if (sec.isString(key)) {
            out.add(sec.getString(key, ""));
        }
        return out;
    }

    public boolean isEmpty() {
        return materials.isEmpty() && namePatterns.isEmpty() && nameContains.isEmpty()
                && loreExact.isEmpty() && loreContains.isEmpty() && nbt.isEmpty();
    }

    public boolean matches(ItemStack item) {
        if (Items.empty(item)) {
            return false;
        }

        if (!materials.isEmpty()) {
            boolean ok = false;
            for (int i = 0; i < materials.size(); i++) {
                if (materials.get(i) == item.getType()) {
                    short want = datas.get(i);
                    if (want == 0 || want == item.getDurability()) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                return false;
            }
        }

        String name = Items.displayName(item);
        if (!namePatterns.isEmpty()) {
            boolean ok = false;
            for (String p : namePatterns) {
                if (Text.wildcard(Text.plain(p), name)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        for (String p : nameContains) {
            if (!name.contains(Text.plain(p))) {
                return false;
            }
        }

        List<String> lore = Items.lore(item);
        for (String want : loreExact) {
            boolean ok = false;
            String w = Text.plain(want);
            for (String line : lore) {
                if (line.equals(w)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        for (String want : loreContains) {
            boolean ok = false;
            String w = Text.plain(want);
            for (String line : lore) {
                if (line.contains(w)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }

        if (!nbt.isEmpty()) {
            if (!NbtUtil.available()) {
                return false;
            }
            for (Map.Entry<String, String> e : nbt.entrySet()) {
                String actual = NbtUtil.read(item, e.getKey());
                if (!compareNbt(e.getValue(), actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean compareNbt(String expect, String actual) {
        if (actual == null) {
            return false;
        }
        String e = expect.trim();
        String[] ops = {">=", "<=", "!=", ">", "<", "="};
        for (String op : ops) {
            if (e.startsWith(op)) {
                String num = e.substring(op.length()).trim();
                try {
                    double a = Double.parseDouble(actual);
                    double b = Double.parseDouble(num);
                    if (">=".equals(op)) {
                        return a >= b;
                    }
                    if ("<=".equals(op)) {
                        return a <= b;
                    }
                    if (">".equals(op)) {
                        return a > b;
                    }
                    if ("<".equals(op)) {
                        return a < b;
                    }
                    if ("!=".equals(op)) {
                        return a != b;
                    }
                    return a == b;
                } catch (NumberFormatException ex) {
                    return op.equals("=") && actual.equalsIgnoreCase(num);
                }
            }
        }
        if (e.contains("*")) {
            return Text.wildcard(e, actual);
        }
        return e.equalsIgnoreCase(actual);
    }

    public Map<String, String> getNbt() {
        return nbt;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (!materials.isEmpty()) {
            sb.append("material=").append(materials).append(' ');
        }
        if (!namePatterns.isEmpty()) {
            sb.append("name=").append(namePatterns).append(' ');
        }
        if (!nameContains.isEmpty()) {
            sb.append("name~").append(nameContains).append(' ');
        }
        if (!loreExact.isEmpty()) {
            sb.append("lore=").append(loreExact).append(' ');
        }
        if (!loreContains.isEmpty()) {
            sb.append("lore~").append(loreContains).append(' ');
        }
        if (!nbt.isEmpty()) {
            sb.append("nbt=").append(nbt).append(' ');
        }
        return sb.toString().trim();
    }
}
