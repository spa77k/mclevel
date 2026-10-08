package dev.spa.mclevel;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 他プラグイン（Skript など）が放置を判定できるよう、最後に操作した時刻を
 * プレイヤーのメタデータ {@value #METADATA_KEY}（UNIX ミリ秒の long）として公開する。
 *
 * プレイ時間の加算（{@link ActivityTracker}）とは別の記録で、
 * 歩行・視点移動・チャット・コマンドも操作として数える。
 */
public final class LastActiveMetadata {
    public static final String METADATA_KEY = "mclevel.lastActive";
    /** 移動イベントのたびに書き換えないよう、更新は 1 秒に 1 回までにする。 */
    private static final long UPDATE_INTERVAL_MILLIS = 1_000L;
    private final Plugin plugin;
    private final Map<UUID, Long> lastUpdatedMillis = new ConcurrentHashMap<>();

    public LastActiveMetadata(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 操作があったことを記録する。メインスレッドから呼ぶ。 */
    public void mark(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUpdated = lastUpdatedMillis.get(uuid);
        if (lastUpdated != null && now - lastUpdated < UPDATE_INTERVAL_MILLIS) {
            return;
        }
        lastUpdatedMillis.put(uuid, now);
        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, now));
    }

    /** 非同期スレッド（チャットなど）から、次の tick に記録する。 */
    public void markLater(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                mark(player);
            }
        });
    }

    /** 退出時・無効化時にメタデータを消す。 */
    public void clear(Player player) {
        lastUpdatedMillis.remove(player.getUniqueId());
        player.removeMetadata(METADATA_KEY, plugin);
    }
}
