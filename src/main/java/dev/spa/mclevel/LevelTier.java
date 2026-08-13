package dev.spa.mclevel;

/**
 * フロンティア経済サバイバルのレベル制度（Lv0〜3）。
 * 各レベルは「必要アクティブプレイ時間」と「必要実績数」の両方を満たすと到達できる。
 */
public enum LevelTier {
    LV0(0, 0, 0, "資源サーバーで開始、基本案内のみ"),
    LV1(1, 2 * 3600, 3, "建築サーバー入場、土地保護の利用開始"),
    LV2(2, 50 * 3600, 25, "ショップ上限15個"),
    LV3(3, 100 * 3600, 40, "記念称号");

    private final int value;
    private final long requiredPlaySeconds;
    private final int requiredAchievements;
    private final String unlockDescription;

    LevelTier(int value, long requiredPlaySeconds, int requiredAchievements, String unlockDescription) {
        this.value = value;
        this.requiredPlaySeconds = requiredPlaySeconds;
        this.requiredAchievements = requiredAchievements;
        this.unlockDescription = unlockDescription;
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

    public String getUnlockDescription() {
        return unlockDescription;
    }

    /** このレベルに到達するための条件を、指定のアクティブ秒数・実績数が両方満たすか。 */
    public boolean isSatisfiedBy(long activeSeconds, int achievements) {
        return activeSeconds >= requiredPlaySeconds && achievements >= requiredAchievements;
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
     * プレイ時間 AND 実績数の両方を満たす最大の Tier。
     */
    public static LevelTier highestQualified(long activeSeconds, int achievements) {
        LevelTier best = LV0;
        for (LevelTier tier : values()) {
            if (tier.isSatisfiedBy(activeSeconds, achievements) && tier.value > best.value) {
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
