package dev.spa.mclevel;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * プレイヤーごとの level と activeSeconds（アクティブプレイ秒）を data.yml に永続化する。
 * 実績数はバニラ進捗から都度導出するため保存しない。
 */
public final class LevelDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    public LevelDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("プラグインデータフォルダの作成に失敗しました。");
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public int getLevel(UUID uuid) {
        int level = config.getInt(path(uuid, "level"), 0);
        return Math.max(0, Math.min(level, LevelTier.maxValue()));
    }

    public long getActiveSeconds(UUID uuid) {
        long activeSeconds = config.getLong(path(uuid, "activeSeconds"), 0L);
        return Math.max(0L, activeSeconds);
    }

    /** メモリ上の値を config に反映する（ファイル保存は行わない）。 */
    public void put(UUID uuid, String playerName, int level, long activeSeconds) {
        config.set(path(uuid, "name"), playerName);
        config.set(path(uuid, "level"), level);
        config.set(path(uuid, "activeSeconds"), activeSeconds);
    }

    /** config をファイルへ書き出す。 */
    public void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("data.yml の保存に失敗しました: " + exception.getMessage());
        }
    }

    private String path(UUID uuid, String key) {
        return "players." + uuid + "." + key;
    }
}
