package com.qingming.decompose.listener;

import com.qingming.decompose.DecomposePlugin;
import eos.moe.dragoncore.api.event.PlayerSlotUpdateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("settings.quit-return", true)) {
            return;
        }
        try {
            plugin.service().returnAll(event.getPlayer());
        } catch (Throwable ignored) {
        }
    }
}
