package dev.spa.mclevel;

/**
 * フロンティア経済サバイバルのレベル制度（Lv0〜3）。
 * 各レベルは必要なアクティブプレイ時間・実績数・累計自力収入の条件を満たすと到達できる。
 */
public enum LevelTier {
    LV0(0, 0, 0, 0),
    LV1(1, 30 * 60, 5, 0),
    LV2(2, 25 * 3600, 25, 5_000L * 100L),
    LV3(3, 75 * 3600, 40, 5_000L * 100L);

    private final int value;
    private final long requiredPlaySeconds;
    private final int requiredAchievements;
    private final long requiredSelfIncomeCents;

    LevelTier(int value, long requiredPlaySeconds, int requiredAchievements,
              long requiredSelfIncomeCents) {
        this.value = value;
        this.requiredPlaySeconds = requiredPlaySeconds;
        this.requiredAchievements = requiredAchievements;
        this.requiredSelfIncomeCents = requiredSelfIncomeCents;
    }

    public int getValue() {
        return value;
    }

    public long getRequiredPlaySeconds() {
        return requiredPlaySeconds;
    }

    public int getRequiredAchievements() {
        return requiredAchievements;
    }

    public long getRequiredSelfIncomeCents() {
        return requiredSelfIncomeCents;
    }

    /** このレベルに到達するための条件を、指定のアクティブ秒数・実績数・累計自力収入が満たすか。 */
    public boolean isSatisfiedBy(long activeSeconds, int achievements, long selfIncomeCents) {
        return activeSeconds >= requiredPlaySeconds
                && achievements >= requiredAchievements
                && selfIncomeCents >= requiredSelfIncomeCents;
    }

    /** 最大レベルか。 */
    public boolean isMax() {
        return this == max();
    }

    public static LevelTier fromValue(int value) {
        for (LevelTier tier : values()) {
            if (tier.value == value) {
                return tier;
            }
        }
        return LV0;
    }

    /**
     * 指定のアクティブ秒数・実績数で到達できる最高レベルを返す。
     * プレイ時間・実績数・累計自力収入を満たす最大の Tier。
     */
    public static LevelTier highestQualified(long activeSeconds, int achievements, long selfIncomeCents) {
        LevelTier best = LV0;
        for (LevelTier tier : values()) {
            if (tier.isSatisfiedBy(activeSeconds, achievements, selfIncomeCents) && tier.value > best.value) {
                best = tier;
            }
        }
        return best;
    }

    /** 次のレベル（最大なら null）。 */
    public LevelTier next() {
        return isMax() ? null : fromValue(value + 1);
    }

    private static LevelTier max() {
        return LV3;
    }

    public static int maxValue() {
        return max().value;
    }
}
