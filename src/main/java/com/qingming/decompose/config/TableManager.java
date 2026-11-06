package com.qingming.decompose.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Loads tables/. One file is one table; nested roots give one table per section. */
public class TableManager {

    private final JavaPlugin plugin;
    private final File dir;
    private final List<DecomposeTable> tables = new ArrayList<DecomposeTable>();

    public TableManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "tables");
    }

    public File getDir() {
        return dir;
    }

    public List<DecomposeTable> getTables() {
        return tables;
    }

    public int load() {
        tables.clear();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建 tables 目录: " + dir.getAbsolutePath());
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (!f.isFile() || !(n.endsWith(".yml") || n.endsWith(".yaml"))) {
                continue;
            }
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                String base = f.getName().replaceAll("(?i)\\.(yml|yaml)$", "");
                if (yml.isConfigurationSection("match") || yml.contains("outputs")) {
                    DecomposeTable t = DecomposeTable.parse(base, yml);
                    if (t.getOutputs().isEmpty()) {
                        plugin.getLogger().warning("表 " + f.getName() + " 没有任何产出，已跳过");
                        continue;
                    }
                    tables.add(t);
                } else {
                    for (String key : yml.getKeys(false)) {
                        ConfigurationSection sec = yml.getConfigurationSection(key);
                        if (sec == null) {
                            continue;
                        }
                        DecomposeTable t = DecomposeTable.parse(base + "." + key, sec);
                        if (!t.getOutputs().isEmpty()) {
                            tables.add(t);
                        }
                    }
                }
            } catch (Throwable e) {
                plugin.getLogger().warning("载入 " + f.getName() + " 失败: " + e.getMessage());
            }
        }
        Collections.sort(tables, new Comparator<DecomposeTable>() {
            @Override
            public int compare(DecomposeTable a, DecomposeTable b) {
                return Integer.compare(b.getPriority(), a.getPriority());
            }
        });
        plugin.getLogger().info("已载入 " + tables.size() + " 张分解表");
        return tables.size();
    }

    public DecomposeTable find(ItemStack item) {
        if (item == null) {
            return null;
        }
        for (DecomposeTable t : tables) {
            if (t.matches(item)) {
                return t;
            }
        }
        return null;
    }

    public DecomposeTable byId(String id) {
        for (DecomposeTable t : tables) {
            if (t.getId().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }
}
