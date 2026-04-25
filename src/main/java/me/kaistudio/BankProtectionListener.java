package me.kaistudio;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class BankProtectionListener implements Listener {

    private static final int MIN_X = 1548;
    private static final int MAX_X = 1582;
    private static final int MIN_Y = 63;
    private static final int MAX_Y = 79;
    private static final int MIN_Z = -3052;
    private static final int MAX_Z = -3011;

    private final AnnouncePlugin plugin;

    public BankProtectionListener(AnnouncePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inZone(Block block) {
        if (block.getWorld().getEnvironment() != World.Environment.NORMAL) return false;
        int x = block.getX(), y = block.getY(), z = block.getZ();
        return x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y && z >= MIN_Z && z <= MAX_Z;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!inZone(event.getBlock())) return;
        if (plugin.getMarketManager().isBuildModeEnabled()) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("The bank is protected.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!inZone(event.getBlock())) return;
        if (plugin.getMarketManager().isBuildModeEnabled()) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("The bank is protected.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::inZone);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::inZone);
    }
}
