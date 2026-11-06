package com.qingming.decompose.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds ItemStacks from the YAML item blocks. */
public final class Items {

    private Items() {
    }

    public static ItemStack build(ConfigurationSection sec, int defAmount) {
        if (sec == null) {
            return null;
        }
        String raw = sec.getString("material", sec.getString("type", ""));
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        short data = (short) sec.getInt("data", 0);
        String name = raw.trim().toUpperCase();
        if (name.contains(":")) {
            String[] sp = name.split(":", 2);
            name = sp[0];
            try {
                data = Short.parseShort(sp[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            return null;
        }
        int amount = Math.max(1, Math.min(64 * 64, sec.getInt("amount", defAmount)));
        ItemStack item = new ItemStack(mat, amount, data);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean touched = false;
            String display = sec.getString("name");
            if (display != null) {
                meta.setDisplayName(Text.color(display));
                touched = true;
            }
            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(Text.color(lore));
                touched = true;
            }
            if (sec.getBoolean("glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                touched = true;
            }
            if (sec.getBoolean("unbreakable", false)) {
                try {
                    meta.setUnbreakable(true);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                    touched = true;
                } catch (Throwable ignored) {
                }
            }
            if (touched) {
                item.setItemMeta(meta);
            }
        }

        ConfigurationSection nbt = sec.getConfigurationSection("nbt");
        if (nbt != null) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            for (String key : nbt.getKeys(true)) {
                if (nbt.isConfigurationSection(key)) {
                    continue;
                }
                map.put(key, String.valueOf(nbt.get(key)));
            }
            item = NbtUtil.apply(item, map);
        }
        return item;
    }

    public static String displayName(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String n = Text.plain(meta.getDisplayName());
            return n.isEmpty() ? pretty(item.getType()) : n;
        }
        return pretty(item.getType());
    }

    public static String pretty(Material mat) {
        String n = mat.name().toLowerCase().replace('_', ' ');
        return n.isEmpty() ? mat.name() : n;
    }

    public static List<String> lore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return new ArrayList<String>();
        }
        return Text.plain(meta.getLore());
    }

    public static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
