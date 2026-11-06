package com.qingming.decompose;

import com.qingming.decompose.command.QmfxCommand;
import com.qingming.decompose.config.TableManager;
import com.qingming.decompose.core.DecomposeService;
import com.qingming.decompose.listener.DecomposeListener;
import com.qingming.decompose.placeholder.QmfxExpansion;
import com.qingming.decompose.util.NbtUtil;
import com.qingming.decompose.util.Text;
import eos.moe.dragoncore.network.PacketSender;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/** Plugin entry point: config, tables, command, listeners. */
public class DecomposePlugin extends JavaPlugin {

    private static DecomposePlugin instance;

    private TableManager tables;
    private DecomposeService service;
    private QmfxExpansion expansion;

    public static DecomposePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        NbtUtil.init(getLogger());

        File tablesDir = new File(getDataFolder(), "tables");
        if (!tablesDir.exists()) {
            tablesDir.mkdirs();
            saveQuietly("tables/示例-灵根.yml");
            saveQuietly("tables/示例-妖丹.yml");
        }

        tables = new TableManager(this);
        tables.load();
        service = new DecomposeService(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new QmfxExpansion(this);
            if (!expansion.register()) {
                getLogger().warning("PlaceholderAPI 变量注册失败，界面可能无法显示状态");
            }
        } else {
            getLogger().warning("未找到 PlaceholderAPI，界面状态变量将不可用");
        }

        if (Bukkit.getPluginManager().getPlugin("DragonCore") == null) {
            getLogger().warning("未找到 DragonCore，界面无法打开");
        }
        checkGuiFile();

        Bukkit.getPluginManager().registerEvents(new DecomposeListener(this), this);

        QmfxCommand command = new QmfxCommand(this);
        if (getCommand("qmfx") != null) {
            getCommand("qmfx").setExecutor(command);
            getCommand("qmfx").setTabCompleter(command);
        }

        getLogger().info("青冥分解台已启用（" + tables.getTables().size() + " 张表）");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    service.returnAll(p);
                } catch (Throwable ignored) {
                }
            }
        }
        instance = null;
    }

    private void saveQuietly(String path) {
        try {
            if (getResource(path) != null) {
                saveResource(path, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean openGui(Player player) {
        String gui = getConfig().getString("gui.name", "青冥分解台");
        if (gui == null || gui.trim().isEmpty()) {
            gui = "青冥分解";
        }
        gui = gui.trim();

        if (gui.toLowerCase().endsWith(".yml")) {
            gui = gui.substring(0, gui.length() - 4);
        }
        if (gui.startsWith("Gui/") || gui.startsWith("gui/")) {
            gui = gui.substring(4);
        }
        try {
            PacketSender.sendOpenGui(player, gui);
            return true;
        } catch (Throwable t) {
            getLogger().warning("打开界面失败（" + gui + "）: " + t);
            player.sendMessage(Text.color("&c界面打开失败，请检查 DragonCore 是否正常加载 " + gui));
            return false;
        }
    }

    private void checkGuiFile() {
        String gui = getConfig().getString("gui.name", "青冥分解台");
        if (gui == null || gui.trim().isEmpty()) {
            gui = "青冥分解";
        }
        gui = gui.trim();
        if (gui.toLowerCase().endsWith(".yml")) {
            gui = gui.substring(0, gui.length() - 4);
        }
        if (gui.startsWith("Gui/") || gui.startsWith("gui/")) {
            gui = gui.substring(4);
        }
        File guiDir = new File(getDataFolder().getParentFile(), "DragonCore/Gui");
        File f = new File(guiDir, gui + ".yml");
        if (!f.isFile()) {
            getLogger().warning("找不到界面文件 " + f.getPath() + "，/qmfx 将无法打开界面");
            StringBuilder sb = new StringBuilder();
            File[] fs = guiDir.listFiles();
            if (fs != null) {
                for (File x : fs) {
                    if (x.getName().toLowerCase().endsWith(".yml")) {
                        sb.append(sb.length() == 0 ? "" : ", ").append(x.getName());
                    }
                }
            }
            if (sb.length() > 0) {
                getLogger().warning("当前可用界面: " + sb);
            }
        } else {
            getLogger().info("界面文件就绪: " + f.getName() + "（打开标识: " + gui + "）");
        }
    }

    public void reload() {
        reloadConfig();
        tables.load();
        if (service != null) {
            service.invalidateAll();
        }
    }

    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, key);
        return Text.color(prefix + body);
    }

    public void send(CommandSender to, String key) {
        if (to != null) {
            to.sendMessage(msg(key));
        }
    }

    public TableManager tables() {
        return tables;
    }

    public DecomposeService service() {
        return service;
    }

    public int maxTimesPerSlot() {
        return Math.max(1, getConfig().getInt("settings.max-times-per-slot", 64));
    }

    public List<String> slots() {
        return service.slots();
    }
}
