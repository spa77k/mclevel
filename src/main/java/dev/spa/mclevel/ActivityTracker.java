package dev.spa.mclevel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「自発的に行動しているとき」だけプレイ時間をカウントするための仕組み。
 *
 * 各プレイヤーのアクティブ期限を記録し、5 秒ごとのタスクで
 * アクティブだった経過時間をまとめて加算する。
 * しきい値（1 分）は猶予期間として働き、1 分に 1 回以上操作していれば連続アクティブ扱いになる。
 */
public final class ActivityTracker {
    /** 無操作がこの時間続いたら「停止中」とみなしカウントを止める（1 分）。 */
    private static final long AFK_THRESHOLD_MILLIS = 60_000L;
    private final LevelService levelService;
    private final Map<UUID, Long> activeUntilMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> countedUntilMillis = new ConcurrentHashMap<>();

    public ActivityTracker(LevelService levelService) {
        this.levelService = levelService;
    }

    /** 自発的な操作があったことを記録する。 */
    public void markActive(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long activeUntil = activeUntilMillis.get(uuid);

        // AFK後の操作は新しい計測区間として扱い、AFK中の時間を加算しない。
        if (activeUntil == null || activeUntil <= now) {
            countedUntilMillis.put(uuid, now);
        }
        activeUntilMillis.put(uuid, now + AFK_THRESHOLD_MILLIS);
    }

    /** 参加時に基準時刻を初期化する。 */
    public void start(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        activeUntilMillis.put(uuid, now);
        countedUntilMillis.put(uuid, now);
    }

    /** 退出時にトラッキングを終了する。 */
    public void stop(Player player) {
        UUID uuid = player.getUniqueId();
        activeUntilMillis.remove(uuid);
        countedUntilMillis.remove(uuid);
    }

    /** 5 秒ごとに呼ばれる本体。アクティブ時間をまとめて加算し、昇格判定する。 */
    public void tick() {
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long activeUntil = activeUntilMillis.get(uuid);
            Long countedUntil = countedUntilMillis.get(uuid);
            if (activeUntil == null || countedUntil == null) {
                continue;
            }

            long activeEnd = Math.min(now, activeUntil);
            long elapsedMillis = activeEnd - countedUntil;
            long activeSeconds = elapsedMillis / 1_000L;
            if (activeSeconds <= 0) {
                continue;
            }

            countedUntilMillis.put(uuid, countedUntil + activeSeconds * 1_000L);
            levelService.addActiveSeconds(player, activeSeconds);
            levelService.evaluate(player);
        }
    }
}
