package dev.spa.mclevel;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

/**
 * 自発的な操作イベントを {@link ActivityTracker} に伝え、
 * 進捗達成・参加時に昇格判定、退出時に永続化を行う。
 */
public final class LevelListener implements Listener {
    private final LevelService levelService;
    private final ActivityTracker tracker;
    private final LevelCelebration celebration;
    private final LastActiveMetadata lastActive;

    public LevelListener(LevelService levelService, ActivityTracker tracker, LevelCelebration celebration,
                         LastActiveMetadata lastActive) {
        this.levelService = levelService;
        this.tracker = tracker;
        this.celebration = celebration;
        this.lastActive = lastActive;
    }

    /** プレイ時間の加算と、最後に操作した時刻の両方へ記録する。 */
    private void markActive(Player player) {
        tracker.markActive(player);
        lastActive.mark(player);
    }

    // --- ライフサイクル ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        levelService.load(player);
        tracker.start(player);
        lastActive.mark(player);
        levelService.evaluate(player);
        levelService.syncPermissions(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        levelService.save(player);
        levelService.unload(player);
        tracker.stop(player);
        lastActive.clear(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        levelService.refreshAchievements(event.getPlayer());
        levelService.evaluate(event.getPlayer());
    }

    // --- 自発的操作（アクティブ判定） ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            markActive(player);
        }
    }

    // --- 最後に操作した時刻だけに数える操作（プレイ時間には加算しない） ---

    /** 視点を動かしたか、乗り物・水流に頼らず別のブロックへ歩いたときに数える。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean walked = event.hasChangedBlock() && !player.isInsideVehicle() && !player.isInWater();
        if (event.hasChangedOrientation() || walked) {
            lastActive.mark(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        lastActive.markLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        lastActive.mark(event.getPlayer());
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
            markActive(player);
        }
    }
}
