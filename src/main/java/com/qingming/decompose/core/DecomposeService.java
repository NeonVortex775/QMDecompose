package com.qingming.decompose.core;

import com.qingming.decompose.DecomposePlugin;
import com.qingming.decompose.config.DecomposeTable;
import com.qingming.decompose.util.Items;
import eos.moe.dragoncore.api.SlotAPI;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The station itself: read the slots, match, consume, pay out, refund. */
public class DecomposeService {

    public static final int INSERT_OK = 0;
    public static final int INSERT_NO_MATCH = 1;
    public static final int INSERT_FULL = 2;

    private final DecomposePlugin plugin;
    private final Map<UUID, long[]> timeCache = new HashMap<UUID, long[]>();
    private final Map<UUID, Long> lastRun = new HashMap<UUID, Long>();
    private final Map<UUID, String[]> snapCache = new HashMap<UUID, String[]>();

    private final Map<UUID, Long> aliveAt = new HashMap<UUID, Long>();

    public DecomposeService(DecomposePlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> slots() {
        return plugin.getConfig().getStringList("slots");
    }

    public ItemStack get(Player player, String slot) {
        try {
            ItemStack it = SlotAPI.getCacheSlotItem(player, slot);
            return Items.empty(it) ? null : it;
        } catch (Throwable t) {
            return null;
        }
    }

    public void set(Player player, String slot, ItemStack item) {
        try {
            SlotAPI.setSlotItem(player, slot, item == null ? new ItemStack(Material.AIR) : item, true);
        } catch (Throwable t) {
            plugin.getLogger().warning("写入槽位 " + slot + " 失败: " + t.getMessage());
        }
    }

    public void invalidate(UUID id) {
        timeCache.remove(id);
        snapCache.remove(id);
    }

    public void invalidateAll() {
        timeCache.clear();
        snapCache.clear();
    }

    public void markAlive(Player player) {
        if (player != null) {
            aliveAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void clearAlive(Player player) {
        if (player != null) {
            aliveAt.remove(player.getUniqueId());
        }
    }

    public boolean guiOpen(Player player) {
        if (player == null) {
            return false;
        }
        Long t = aliveAt.get(player.getUniqueId());
        if (t == null) {
            return false;
        }
        long ms = Math.max(1000L, plugin.getConfig().getLong("settings.gui-alive-ms", 3000L));
        return System.currentTimeMillis() - t <= ms;
    }

    public int shiftInsert(Player player, int invIndex) {
        if (player == null || invIndex < 0) {
            return INSERT_NO_MATCH;
        }
        ItemStack src = player.getInventory().getItem(invIndex);
        if (Items.empty(src)) {
            return INSERT_NO_MATCH;
        }

        if (plugin.getConfig().getBoolean("settings.shift-insert-match-only", true)
                && plugin.tables().find(src) == null) {
            return INSERT_NO_MATCH;
        }

        int max = Math.max(1, src.getMaxStackSize());
        int remain = src.getAmount();

        for (String slot : slots()) {
            if (remain <= 0) {
                break;
            }
            ItemStack cur = get(player, slot);
            if (cur == null || !cur.isSimilar(src)) {
                continue;
            }
            int space = max - cur.getAmount();
            if (space <= 0) {
                continue;
            }
            int n = Math.min(space, remain);
            ItemStack put = cur.clone();
            put.setAmount(cur.getAmount() + n);
            set(player, slot, put);
            remain -= n;
        }

        for (String slot : slots()) {
            if (remain <= 0) {
                break;
            }
            if (get(player, slot) != null) {
                continue;
            }
            int n = Math.min(max, remain);
            ItemStack put = src.clone();
            put.setAmount(n);
            set(player, slot, put);
            remain -= n;
        }

        int moved = src.getAmount() - remain;
        if (moved <= 0) {
            return INSERT_FULL;
        }
        if (remain <= 0) {
            player.getInventory().setItem(invIndex, null);
        } else {
            ItemStack left = src.clone();
            left.setAmount(remain);
            player.getInventory().setItem(invIndex, left);
        }

        player.updateInventory();
        invalidate(player.getUniqueId());
        return INSERT_OK;
    }

    public String[] snapshot(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long[] t = timeCache.get(id);
        String[] cached = snapCache.get(id);
        long ttl = Math.max(0, plugin.getConfig().getInt("settings.placeholder-cache-ms", 180));
        if (t != null && cached != null && now - t[0] < ttl) {
            return cached;
        }

        int filled = 0;
        int okSlots = 0;
        int okItems = 0;
        boolean hasOther = false;
        for (String slot : slots()) {
            ItemStack it = get(player, slot);
            if (it == null) {
                continue;
            }
            filled++;
            DecomposeTable table = plugin.tables().find(it);
            if (table == null) {
                hasOther = true;
            } else {
                okSlots++;
                okItems += it.getAmount();
            }
        }

        String state;
        String msg;
        if (filled == 0) {
            state = "0";
            msg = "§7把要分解的物品放进左边的格子";
        } else if (okSlots == 0) {
            state = "0";
            msg = "§c没有可分解的物品 §8· §7点分解会原样退回背包";
        } else {
            state = "1";
            msg = "§a可分解 " + okSlots + " 格 §8· §f" + okItems + " 件"
                    + (hasOther ? " §8(其余将退回)" : "");
        }

        String[] result = new String[]{state, msg};
        timeCache.put(id, new long[]{now});
        snapCache.put(id, result);
        return result;
    }

    public Result decompose(Player player) {
        Result result = new Result();

        long now = System.currentTimeMillis();
        Long last = lastRun.get(player.getUniqueId());
        if (last != null && now - last < 400L) {
            result.ignored = true;
            return result;
        }
        lastRun.put(player.getUniqueId(), now);
        for (String slot : slots()) {
            ItemStack it = get(player, slot);
            if (it == null) {
                continue;
            }
            DecomposeTable table = plugin.tables().find(it);
            if (table == null) {
                set(player, slot, null);
                result.returned += giveBack(player, it);
                continue;
            }
            int cap = Math.min(table.getMaxTimes(), plugin.maxTimesPerSlot());
            int times = Math.min(it.getAmount() / table.getConsume(), cap);
            if (times <= 0) {
                set(player, slot, null);
                result.returned += giveBack(player, it);
                continue;
            }
            int use = times * table.getConsume();
            int remain = it.getAmount() - use;
            ItemStack rest = null;
            if (remain > 0) {
                rest = it.clone();
                rest.setAmount(remain);
            }
            set(player, slot, rest);
            for (int i = 0; i < times; i++) {
                result.produced += table.giveOutputs(plugin, player);
            }
            result.consumed += use;
            if (!result.tables.contains(table.getDisplay())) {
                result.tables.add(table.getDisplay());
            }
        }
        invalidate(player.getUniqueId());
        return result;
    }

    public int returnAll(Player player) {
        int n = 0;
        for (String slot : slots()) {
            ItemStack it = get(player, slot);
            if (it == null) {
                continue;
            }
            set(player, slot, null);
            n += giveBack(player, it);
        }
        invalidate(player.getUniqueId());
        return n;
    }

    private int giveBack(Player player, ItemStack item) {
        if (Items.empty(item)) {
            return 0;
        }
        int amount = item.getAmount();
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        for (ItemStack l : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), l);
        }
        return amount;
    }

    public void playSound(Player player, String key) {
        String name = plugin.getConfig().getString("sounds." + key);
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(name.trim().toUpperCase());
            player.playSound(player.getLocation(), sound, 0.8F, 1.0F);
        } catch (Throwable ignored) {
        }
    }

    public static class Result {
        public int consumed;
        public int returned;
        public int produced;
        public boolean ignored;
        public final List<String> tables = new ArrayList<String>();

        public boolean nothing() {
            return !ignored && consumed == 0 && returned == 0;
        }
    }
}
