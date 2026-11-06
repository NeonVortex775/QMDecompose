package com.qingming.decompose.config;

import com.qingming.decompose.match.MatchRule;
import com.qingming.decompose.util.Items;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** One table: a match rule plus the outputs it yields. */
public class DecomposeTable {

    private final String id;
    private final String display;
    private final int priority;
    private final MatchRule match;
    private final int consume;
    private final int maxTimes;
    private final List<Output> outputs = new ArrayList<Output>();

    public DecomposeTable(String id, String display, int priority, MatchRule match,
                          int consume, int maxTimes, List<Output> outputs) {
        this.id = id;
        this.display = display == null ? id : display;
        this.priority = priority;
        this.match = match;
        this.consume = Math.max(1, consume);
        this.maxTimes = Math.max(1, maxTimes);
        this.outputs.addAll(outputs);
    }

    public static DecomposeTable parse(String id, ConfigurationSection sec) {
        MatchRule rule = MatchRule.parse(sec.getConfigurationSection("match"));
        String display = sec.getString("display", id);
        int priority = sec.getInt("priority", 0);
        int consume = sec.getInt("consume", 1);
        int maxTimes = sec.getInt("max-times", 64);

        List<Output> outs = new ArrayList<Output>();
        List<?> list = sec.getList("outputs");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ConfigurationSection) {
                    Output out = Output.parse((ConfigurationSection) o);
                    if (!out.isEmpty()) {
                        outs.add(out);
                    }
                } else if (o instanceof java.util.Map) {
                    ConfigurationSection ms = new org.bukkit.configuration.MemoryConfiguration().createSection("o");
                    for (java.util.Map.Entry<?, ?> e : ((java.util.Map<?, ?>) o).entrySet()) {
                        ms.set(String.valueOf(e.getKey()), e.getValue());
                    }
                    Output out = Output.parse(ms);
                    if (!out.isEmpty()) {
                        outs.add(out);
                    }
                } else if (o != null) {

                    Output out = parseRaw(String.valueOf(o));
                    if (out != null) {
                        outs.add(out);
                    }
                }
            }
        }
        ConfigurationSection single = sec.getConfigurationSection("outputs");
        if (single != null) {
            Output out = Output.parse(single);
            if (!out.isEmpty()) {
                outs.add(out);
            }
        }
        return new DecomposeTable(id, display, priority, rule, consume, maxTimes, outs);
    }

    private static Output parseRaw(String raw) {
        org.bukkit.configuration.MemoryConfiguration sec = new org.bukkit.configuration.MemoryConfiguration();
        sec.set("command", raw);
        Output out = Output.parse(sec);
        return out.isEmpty() ? null : out;
    }

    public int giveOutputs(Plugin plugin, Player player) {
        int n = 0;
        for (Output out : outputs) {
            if (!out.roll()) {
                continue;
            }
            n += out.give(plugin, player);
        }
        return n;
    }

    public List<String> preview() {
        List<String> list = new ArrayList<String>();
        for (Output out : outputs) {
            String l = out.label();
            if (!l.isEmpty()) {
                list.add(l);
            }
        }
        return list;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public int getPriority() {
        return priority;
    }

    public MatchRule getMatch() {
        return match;
    }

    public int getConsume() {
        return consume;
    }

    public int getMaxTimes() {
        return maxTimes;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

    public boolean matches(org.bukkit.inventory.ItemStack item) {
        return !Items.empty(item) && match.matches(item);
    }
}
