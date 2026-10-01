package dev.spa.mclevel;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class McLevelPlugin extends JavaPlugin {
    private static final long ACTIVITY_TICKS = 20L * 5L;  // 5 秒
    private static final long AUTOSAVE_TICKS = 20L * 300L; // 5 分

    private LevelService levelService;

    /**
     * 他プラグイン向けの累計アクティブ秒取得API。サーバーのメインスレッドから呼ぶ。
     * 5秒ごとの積算済み値を返し、未保存のアクティブ時間も含む。バニラ統計は使わない。
     */
    public long getActiveSeconds(Player player) {
        if (!isEnabled() || levelService == null || !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("McLevel active time requires an enabled plugin and the main thread");
        }
        return levelService.getActiveSeconds(java.util.Objects.requireNonNull(player, "player"));
    }

    @Override
    public void onEnable() {
        LevelDataStore dataStore = new LevelDataStore(this);
        LevelCelebration celebration = new LevelCelebration(this);
        LuckPermsGroupManager groupManager = new LuckPermsGroupManager(this);
        levelService = new LevelService(dataStore, celebration, groupManager, new Lv1Welcome(this));
        ActivityTracker tracker = new ActivityTracker(levelService);

        getServer().getPluginManager().registerEvents(new LevelListener(levelService, tracker, celebration), this);
        JobsIncomeBridge.register(this, levelService);

        PluginCommand levelCommand = getCommand("level");
        if (levelCommand != null) {
            LevelCommand executor = new LevelCommand(levelService);
            levelCommand.setExecutor(executor);
            levelCommand.setTabCompleter(executor);
        } else {
            getLogger().warning("level コマンドの登録に失敗しました。plugin.yml を確認してください。");
        }

        PluginCommand adminCommand = getCommand("mclevel");
        if (adminCommand != null) {
            AdminCommand executor = new AdminCommand(levelService);
            adminCommand.setExecutor(executor);
            adminCommand.setTabCompleter(executor);
        } else {
            getLogger().warning("mclevel コマンドの登録に失敗しました。plugin.yml を確認してください。");
        }

        // 既にオンラインのプレイヤー（/reload 時など）を読み込む。
        for (Player player : getServer().getOnlinePlayers()) {
            levelService.load(player);
            tracker.start(player);
            levelService.syncPermissions(player);
        }

        // 5 秒ごとのアクティブ時間積算・昇格判定。
        getServer().getScheduler().runTaskTimer(this, tracker::tick, ACTIVITY_TICKS, ACTIVITY_TICKS);
        // 定期オートセーブ。
        getServer().getScheduler().runTaskTimer(this, levelService::saveAll, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (levelService != null) {
            levelService.saveAll();
        }
    }
}
