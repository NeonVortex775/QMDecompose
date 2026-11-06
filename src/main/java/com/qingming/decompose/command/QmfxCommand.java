package com.qingming.decompose.command;

import com.qingming.decompose.DecomposePlugin;
import com.qingming.decompose.config.DecomposeTable;
import com.qingming.decompose.core.DecomposeService;
import com.qingming.decompose.util.Items;
import com.qingming.decompose.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /qmfx - open the station, reload tables, inspect a match. */
public class QmfxCommand implements CommandExecutor, TabCompleter {

    private final DecomposePlugin plugin;

    public QmfxCommand(DecomposePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return open(sender, null);
        }
        String sub = args[0].toLowerCase();

        if ("open".equals(sub)) {
            String target = args.length >= 2 ? args[1] : null;
            return open(sender, target);
        }
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("qmfx.admin")) {
                plugin.send(sender, "no-permission");
                return true;
            }
            plugin.reload();
            plugin.send(sender, "reloaded");
            sender.sendMessage(Text.color("  &7共 &f" + plugin.tables().getTables().size() + " &7张分解表"));
            return true;
        }
        if ("list".equals(sub)) {
            if (!sender.hasPermission("qmfx.admin")) {
                plugin.send(sender, "no-permission");
                return true;
            }
            sender.sendMessage(Text.color("&8==== &b分解表 &8===="));
            for (DecomposeTable t : plugin.tables().getTables()) {
                sender.sendMessage(Text.color("&f" + t.getId() + " &7| &e" + t.getDisplay()
                        + " &8| 优先级 " + t.getPriority() + " &8| 产出 " + t.getOutputs().size()
                        + " &8| &7" + t.getMatch().describe()));
            }
            return true;
        }
        if ("info".equals(sub)) {
            if (!(sender instanceof Player)) {
                plugin.send(sender, "player-only");
                return true;
            }
            Player p = (Player) sender;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (Items.empty(hand)) {
                p.sendMessage(Text.color("&7你手上没有物品。"));
                return true;
            }
            DecomposeTable t = plugin.tables().find(hand);
            p.sendMessage(Text.color("&8==== &b匹配结果 &8===="));
            p.sendMessage(Text.color("&f物品: &e" + Items.displayName(hand) + " &8x" + hand.getAmount()));
            if (t == null) {
                p.sendMessage(Text.color("&c没有匹配的分解表"));
            } else {
                p.sendMessage(Text.color("&a匹配: &f" + t.getId() + " &7(" + t.getDisplay() + ")"));
                p.sendMessage(Text.color("&7每次消耗: &f" + t.getConsume()
                        + " &7可分解次数: &f" + (hand.getAmount() / t.getConsume())));
                p.sendMessage(Text.color("&7产出: &f" + String.join("&7, &f", t.preview())));
            }
            return true;
        }
        if ("take".equals(sub)) {
            if (!(sender instanceof Player)) {
                plugin.send(sender, "player-only");
                return true;
            }
            Player p = (Player) sender;
            int n = plugin.service().returnAll(p);
            if (n <= 0) {
                plugin.send(sender, "no-item");
            } else {
                p.sendMessage(Text.color(plugin.msg("returned").replace("{amount}", String.valueOf(n))));
                plugin.service().playSound(p, "return-item");
            }
            return true;
        }
        if ("c".equals(sub) || "confirm".equals(sub)) {
            if (!(sender instanceof Player)) {
                plugin.send(sender, "player-only");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("qmfx.use")) {
                plugin.send(sender, "no-permission");
                return true;
            }
            DecomposeService.Result r = plugin.service().decompose(p);
            if (r.ignored) {
                return true;
            }
            if (r.nothing()) {
                plugin.send(sender, "no-item");
                plugin.service().playSound(p, "fail");
                return true;
            }
            if (r.consumed > 0) {
                String m = plugin.msg("success")
                        .replace("{amount}", String.valueOf(r.consumed))
                        .replace("{outputs}", String.valueOf(r.produced));
                p.sendMessage(m);
                plugin.service().playSound(p, "decompose");
            }
            if (r.returned > 0) {
                p.sendMessage(Text.color(plugin.msg("returned")
                        .replace("{amount}", String.valueOf(r.returned))));
            }
            return true;
        }
        if ("close".equals(sub)) {
            if (!(sender instanceof Player)) {
                return true;
            }
            Player p = (Player) sender;

            plugin.service().clearAlive(p);
            int n = plugin.service().returnAll(p);
            if (n > 0) {
                p.sendMessage(Text.color(plugin.msg("returned").replace("{amount}", String.valueOf(n))));
            }
            return true;
        }

        sender.sendMessage(Text.color("&8[&b分解&8] &7/qmfx open [玩家] &8| &7reload &8| &7list &8| &7info &8| &7take"));
        return true;
    }

    private boolean open(CommandSender sender, String targetName) {
        if (targetName != null && !targetName.isEmpty()) {
            if (!sender.hasPermission("qmfx.open")) {
                plugin.send(sender, "no-permission");
                return true;
            }
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage(Text.color("&c找不到玩家 " + targetName));
                return true;
            }
            if (plugin.openGui(target)) {
                plugin.send(sender, "opened");
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(Text.color("&7控制台用法: &f/qmfx open <玩家>"));
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("qmfx.use")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (plugin.openGui(p)) {
            plugin.send(sender, "opened");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<String>();
            for (String s : Arrays.asList("open", "reload", "list", "info", "take", "c", "close")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && "open".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return new ArrayList<String>();
    }
}
