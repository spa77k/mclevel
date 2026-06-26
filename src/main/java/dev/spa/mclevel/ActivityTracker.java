package dev.spa.mclevel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「自発的に行動しているとき」だけプレイ時間をカウントするための仕組み。
 *
 * 各プレイヤーの最後の操作時刻を記録し、1 秒ごとのタスクで
 * 「最後の操作から AFK_THRESHOLD_MILLIS 以内」のプレイヤーにのみ 1 秒を加算する。
 * しきい値（1 分）は猶予期間として働き、1 分に 1 回以上操作していれば連続アクティブ扱いになる。
 */
public final class ActivityTracker {
    /** 無操作がこの時間続いたら「停止中」とみなしカウントを止める（1 分）。 */
    private static final long AFK_THRESHOLD_MILLIS = 60_000L;
    /** カウント中、評価（昇格判定）を行う間隔（秒）。負荷軽減のためまとめて評価する。 */
    private static final int EVALUATE_INTERVAL_SECONDS = 5;

    private final LevelService levelService;
    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public ActivityTracker(LevelService levelService) {
        this.levelService = levelService;
    }

    /** 自発的な操作があったことを記録する。 */
    public void markActive(Player player) {
        lastActivityMillis.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** 参加時に基準時刻を初期化する。 */
    public void start(Player player) {
        lastActivityMillis.put(player.getUniqueId(), System.currentTimeMillis() - AFK_THRESHOLD_MILLIS);
    }

    /** 退出時にトラッキングを終了する。 */
    public void stop(Player player) {
        lastActivityMillis.remove(player.getUniqueId());
    }

    /** 1 秒ごとに呼ばれる本体。アクティブなプレイヤーに時間を加算し、定期的に昇格判定する。 */
    public void tick() {
        long now = System.currentTimeMillis();
        boolean evaluate = (++tickCounter % EVALUATE_INTERVAL_SECONDS) == 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Long last = lastActivityMillis.get(player.getUniqueId());
            if (last == null || now - last > AFK_THRESHOLD_MILLIS) {
                continue;
            }
            levelService.addActiveSecond(player);
            if (evaluate) {
                levelService.evaluate(player);
            }
        }
    }
}
