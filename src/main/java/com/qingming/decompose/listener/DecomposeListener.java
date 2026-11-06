package com.qingming.decompose.listener;

import com.qingming.decompose.DecomposePlugin;
import com.qingming.decompose.core.DecomposeService;
import com.qingming.decompose.util.Items;
import eos.moe.dragoncore.api.event.PlayerSlotUpdateEvent;
import eos.moe.dragoncore.api.event.SyncPlaceholderEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Hands the slot contents back when a player quits. */
public class DecomposeListener implements Listener {

    private final DecomposePlugin plugin;

    public DecomposeListener(DecomposePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSlotUpdate(PlayerSlotUpdateEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        String id = event.getIdentifier();
        if (id == null) {
            return;
        }
        if (plugin.slots().contains(id)) {
            plugin.service().invalidate(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSyncPlaceholder(SyncPlaceholderEvent event) {
        Player p = event.getPlayer();
        if (p == null) {
            return;
        }
        Map<String, String> map = event.getPlaceholders();
        if (map == null || map.isEmpty()) {
            return;
        }
        for (String key : map.keySet()) {
            if (key != null && key.toLowerCase().contains("qmfx")) {
                plugin.service().markAlive(p);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShiftClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getWhoClicked();
        if (!event.isShiftClick()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("settings.shift-insert", true)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (!(clicked instanceof PlayerInventory)) {
            return;
        }
        if (!plugin.service().guiOpen(p)) {
            return;
        }

        int invIndex = event.getSlot();
        if (invIndex < 0 || invIndex > 35) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (Items.empty(item)) {
            return;
        }

        ItemStack slotItem = p.getInventory().getItem(invIndex);
        if (Items.empty(slotItem) || !slotItem.isSimilar(item)) {
            return;
        }

        event.setCancelled(true);
        int result = plugin.service().shiftInsert(p, invIndex);
        if (result == DecomposeService.INSERT_OK) {
            plugin.service().playSound(p, "insert");
        } else if (result == DecomposeService.INSERT_FULL) {
            p.sendMessage(plugin.msg("shift-full"));
            plugin.service().playSound(p, "fail");
        } else {
            p.sendMessage(plugin.msg("shift-no-match"));
            plugin.service().playSound(p, "fail");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.service().clearAlive(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().clearAlive(event.getPlayer());
        if (!plugin.getConfig().getBoolean("settings.quit-return", true)) {
            return;
        }
        try {
            plugin.service().returnAll(event.getPlayer());
        } catch (Throwable ignored) {
        }
    }
}
