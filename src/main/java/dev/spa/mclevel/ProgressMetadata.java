package dev.spa.mclevel;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * 他プラグイン（Skript など）が次のレベルまでの進み具合を表示できるよう、
 * 累計アクティブ秒 {@value #ACTIVE_SECONDS_KEY}（long）と
 * 実績数 {@value #ACHIEVEMENTS_KEY}（int）をプレイヤーのメタデータとして公開する。
 */
public final class ProgressMetadata {
    public static final String ACTIVE_SECONDS_KEY = "mclevel.activeSeconds";
    public static final String ACHIEVEMENTS_KEY = "mclevel.achievements";
    private final Plugin plugin;
    private final LevelService levelService;

    public ProgressMetadata(Plugin plugin, LevelService levelService) {
        this.plugin = plugin;
        this.levelService = levelService;
    }

    /** 現在の値で書き換える。メインスレッドから呼ぶ。 */
    public void update(Player player) {
        player.setMetadata(ACTIVE_SECONDS_KEY,
                new FixedMetadataValue(plugin, levelService.getActiveSeconds(player)));
        player.setMetadata(ACHIEVEMENTS_KEY,
                new FixedMetadataValue(plugin, levelService.countAchievements(player)));
    }

    /** オンラインの全員を書き換える。アクティブ時間を積算した直後に呼ぶ。 */
    public void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            update(player);
        }
    }

    /** 退出時・無効化時にメタデータを消す。 */
    public void clear(Player player) {
        player.removeMetadata(ACTIVE_SECONDS_KEY, plugin);
        player.removeMetadata(ACHIEVEMENTS_KEY, plugin);
    }
}
