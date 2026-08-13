package dev.spa.mclevel;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レベル制度の中核。アクティブプレイ秒の積算・実績数のカウント・昇格判定を担う。
 * オンライン中はメモリ上の {@link PlayerState} で値を保持し、退出・オートセーブ時に永続化する。
 */
public final class LevelService {
    private final LevelDataStore dataStore;
    private final LevelCelebration celebration;
    private final LuckPermsGroupManager groupManager;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public LevelService(LevelDataStore dataStore, LevelCelebration celebration, LuckPermsGroupManager groupManager) {
        this.dataStore = dataStore;
        this.celebration = celebration;
        this.groupManager = groupManager;
    }

    /** 参加時にメモリへ状態を読み込む。 */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        states.put(uuid, new PlayerState(dataStore.getLevel(uuid), dataStore.getActiveSeconds(uuid)));
    }

    /** 退出時にメモリから状態を取り除く（事前に save しておくこと）。 */
    public void unload(Player player) {
        states.remove(player.getUniqueId());
    }

    private PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerState(dataStore.getLevel(uuid), dataStore.getActiveSeconds(uuid)));
    }

    public int getLevel(Player player) {
        return state(player).level;
    }

    public long getActiveSeconds(Player player) {
        return state(player).activeSeconds;
    }

    /** 現在レベルをLuckPermsのMcLevel用グループへ反映する。 */
    public void syncPermissions(Player player) {
        groupManager.syncPlayer(player, getLevel(player));
    }

    /** アクティブと判定された 1 秒を加算する。 */
    public void addActiveSecond(Player player) {
        state(player).activeSeconds++;
    }

    /** バニラ進捗のうちレシピ解禁を除いた達成数を数える。 */
    public int countAchievements(Player player) {
        int count = 0;
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement advancement = it.next();
            if (!"minecraft".equals(advancement.getKey().getNamespace())
                    || advancement.getKey().getKey().startsWith("recipes/")) {
                continue;
            }
            if (player.getAdvancementProgress(advancement).isDone()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 現在のアクティブ秒と実績数から、プレイ時間 AND 実績数の両方を満たす最高レベルへ昇格させる。
     * 現在レベルより上がる場合のみ通知する。
     */
    public void evaluate(Player player) {
        PlayerState state = state(player);
        LevelTier qualified = LevelTier.highestQualified(state.activeSeconds, countAchievements(player));
        if (qualified.getValue() > state.level) {
            state.level = qualified.getValue();
            save(player);
            groupManager.syncPlayer(player, state.level);
            celebration.celebrate(player, qualified);
        }
    }

    /** 管理者がレベルを強制設定し、即座に保存する。お祝い演出も発火する。 */
    public void setLevel(Player player, int level) {
        state(player).level = level;
        save(player);
        groupManager.syncPlayer(player, level);
        celebration.celebrate(player, LevelTier.fromValue(level));
    }

    /** オンラインプレイヤー全員のメモリ状態を config に反映して保存する。 */
    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            save(player);
        }
        dataStore.save();
    }

    /** 1 プレイヤーのメモリ状態を config に反映する（ファイル書き出しは呼び出し側）。 */
    public void flush(Player player) {
        PlayerState state = state(player);
        dataStore.put(player.getUniqueId(), player.getName(), state.level, state.activeSeconds);
    }

    /** 1 プレイヤーを反映してファイルへ保存する。 */
    public void save(Player player) {
        flush(player);
        dataStore.save();
    }

    private static final class PlayerState {
        private int level;
        private long activeSeconds;

        private PlayerState(int level, long activeSeconds) {
            this.level = level;
            this.activeSeconds = activeSeconds;
        }
    }
}
