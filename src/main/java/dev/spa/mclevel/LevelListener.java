package dev.spa.mclevel;

import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 自発的な操作イベントを {@link ActivityTracker} に伝え、
 * 進捗達成・参加時に昇格判定、退出時に永続化を行う。
 */
public final class LevelListener implements Listener {
    private final LevelService levelService;
    private final ActivityTracker tracker;
    private final LevelCelebration celebration;

    public LevelListener(LevelService levelService, ActivityTracker tracker, LevelCelebration celebration) {
        this.levelService = levelService;
        this.tracker = tracker;
        this.celebration = celebration;
    }

    // --- ライフサイクル ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        levelService.load(player);
        tracker.start(player);
        levelService.evaluate(player);
        levelService.syncPermissions(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        levelService.save(player);
        levelService.unload(player);
        tracker.stop(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        levelService.evaluate(event.getPlayer());
    }

    // --- 自発的操作（アクティブ判定） ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // 視点回転だけでは更新せず、ブロック座標が変わった場合のみアクティブとみなす。
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        tracker.markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        tracker.markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        tracker.markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        tracker.markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            tracker.markActive(player);
        }
    }

    // --- お祝い演出の安全対策 ---

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCelebrationFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(celebration.getCelebrationFireworkKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            tracker.markActive(player);
        }
    }
}
