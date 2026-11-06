package com.qingming.decompose.config;

import com.qingming.decompose.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** A single output - an item, a command, or both, with an optional chance. */
public class Output {

    private ItemStack item;
    private final List<String> commands = new ArrayList<String>();
    private double chance = 100.0D;

    public static Output parse(ConfigurationSection sec) {
        Output out = new Output();
        out.chance = sec.getDouble("chance", 100.0D);

        ConfigurationSection itemSec = sec.getConfigurationSection("item");
        if (itemSec != null) {
            out.item = Items.build(itemSec, 1);
        } else if (sec.isString("material") || sec.isString("type")) {
            out.item = Items.build(sec, 1);
        }

        if (sec.isList("commands")) {
            out.commands.addAll(sec.getStringList("commands"));
        } else if (sec.isString("commands")) {
            out.commands.add(sec.getString("commands", ""));
        }
        if (sec.isList("command")) {
            out.commands.addAll(sec.getStringList("command"));
        } else if (sec.isString("command")) {
            out.commands.add(sec.getString("command", ""));
        }
        return out;
    }

    public boolean isEmpty() {
        return item == null && commands.isEmpty();
    }

    public boolean roll() {
        if (chance >= 100.0D) {
            return true;
        }
        if (chance <= 0.0D) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() * 100.0D < chance;
    }

    public int give(Plugin plugin, Player player) {
        int n = 0;
        if (item != null) {
            ItemStack it = item.clone();
            Map<Integer, ItemStack> left = player.getInventory().addItem(it);
            for (ItemStack l : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), l);
            }
            n++;
        }
        for (String raw : commands) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String cmd = replace(raw.trim(), player).replaceFirst("^/", "");
            if (cmd.isEmpty()) {
                continue;
            }
            if (cmd.startsWith("server:")) {
                cmd = cmd.substring("server:".length()).trim();
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            n++;
        }
        return n;
    }

    private String replace(String s, Player p) {
        Location loc = p.getLocation();
        String out = s.replace("%player%", p.getName())
                .replace("%uuid%", p.getUniqueId().toString())
                .replace("%world%", p.getWorld().getName())
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
        return out;
    }

    public String label() {
        if (item != null) {
            String name = Items.displayName(item);
            String amount = item.getAmount() > 1 ? " ×" + item.getAmount() : "";
            return name + amount;
        }
        if (!commands.isEmpty()) {
            String c = commands.get(0);
            return c.replace("ni giveslient ", "NI:").replace("ni give ", "NI:");
        }
        return "";
    }

    public static Map<String, String> sample() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("item", "material: DIAMOND / amount: 1 / name / lore / nbt");
        m.put("command", "ni giveslient %player% <物品ID> 1");
        return m;
    }
}
