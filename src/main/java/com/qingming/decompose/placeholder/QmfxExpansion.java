package com.qingming.decompose.placeholder;

import com.qingming.decompose.DecomposePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** %qmfx_state% and %qmfx_msg% for the screen. */
public class QmfxExpansion extends PlaceholderExpansion {

    private final DecomposePlugin plugin;

    public QmfxExpansion(DecomposePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "qmfx";
    }

    @Override
    public String getAuthor() {
        return "QingMing";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        Player online = player.getPlayer();
        if (online == null) {
            return "";
        }
        String[] snap = plugin.service().snapshot(online);
        if ("state".equalsIgnoreCase(params)) {
            return snap[0];
        }
        if ("msg".equalsIgnoreCase(params)) {
            return snap[1];
        }
        return null;
    }
}
