package dev.spa.mclevel;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 参加時にメモリへ状態と進捗数を読み込む。 */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        states.put(uuid, new PlayerState(
                dataStore.getLevel(uuid),
                dataStore.getActiveSeconds(uuid),
                dataStore.getSelfIncomeCents(uuid),
                countAchievementsFromServer(player)
        ));
    }

    /** 退出時にメモリから状態を取り除く（事前に save しておくこと）。 */
    public void unload(Player player) {
        states.remove(player.getUniqueId());
    }

    private PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerState(
                        dataStore.getLevel(uuid),
                        dataStore.getActiveSeconds(uuid),
                        dataStore.getSelfIncomeCents(uuid),
                        countAchievementsFromServer(player)
                ));
    }

    public int getLevel(Player player) {
        return state(player).level;
    }

    public long getActiveSeconds(Player player) {
        return state(player).activeSeconds;
    }

    public long getSelfIncomeCents(Player player) {
        return state(player).selfIncomeCents;
    }

    /** 現在レベルをLuckPermsのMcLevel用グループへ反映する。 */
    public void syncPermissions(Player player) {
        groupManager.syncPlayer(player, getLevel(player));
    }

    /** アクティブと判定された経過秒数を加算する。 */
    public void addActiveSeconds(Player player, long activeSeconds) {
        if (activeSeconds > 0) {
            state(player).activeSeconds += activeSeconds;
        }
    }

    /** Jobsまたはデイリークエストによる正の収入だけを累計し、昇格判定を行う。 */
    public void addSelfIncome(OfflinePlayer player, double amount) {
        long amountCents = toCents(amount);
        if (player == null || amountCents <= 0) {
            return;
        }

        Player onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
        if (onlinePlayer != null) {
            PlayerState state = state(onlinePlayer);
            state.selfIncomeCents = safeAdd(state.selfIncomeCents, amountCents);
            evaluate(onlinePlayer);
            return;
        }

        dataStore.addSelfIncomeCents(player.getUniqueId(), player.getName(), amountCents);
        dataStore.save();
    }

    /** キャッシュ済みのバニラ進捗達成数を返す。 */
    public int countAchievements(Player player) {
        return state(player).achievements;
    }

    /** 進捗達成イベント後に、キャッシュだけを更新する。 */
    public void refreshAchievements(Player player) {
        state(player).achievements = countAchievementsFromServer(player);
    }

    /** バニラ進捗のうちレシピ解禁を除いた達成数を数える。参加時と進捗イベント時だけ呼ぶ。 */
    private int countAchievementsFromServer(Player player) {
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
        LevelTier qualified = LevelTier.highestQualified(
                state.activeSeconds,
                countAchievements(player),
                state.selfIncomeCents
        );
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
            flush(player);
        }
        dataStore.save();
    }

    /** 1 プレイヤーのメモリ状態を config に反映する（ファイル書き出しは呼び出し側）。 */
    public void flush(Player player) {
        PlayerState state = state(player);
        dataStore.put(
                player.getUniqueId(),
                player.getName(),
                state.level,
                state.activeSeconds,
                state.selfIncomeCents
        );
    }

    /** 1 プレイヤーを反映してファイルへ保存する。 */
    public void save(Player player) {
        flush(player);
        dataStore.save();
    }

    private static final class PlayerState {
        private int level;
        private long activeSeconds;
        private long selfIncomeCents;
        private int achievements;

        private PlayerState(int level, long activeSeconds, long selfIncomeCents, int achievements) {
            this.level = level;
            this.activeSeconds = activeSeconds;
            this.selfIncomeCents = selfIncomeCents;
            this.achievements = achievements;
        }
    }

    private long toCents(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return 0L;
        }
        try {
            return BigDecimal.valueOf(amount)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private long safeAdd(long current, long amount) {
        return Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
    }
}
