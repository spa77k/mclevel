package dev.spa.mclevel;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class McLevelPlugin extends JavaPlugin {
    private static final long ACTIVITY_TICKS = 20L * 5L;  // 5 秒
    private static final long AUTOSAVE_TICKS = 20L * 300L; // 5 分

    private LevelService levelService;

    @Override
    public void onEnable() {
        LevelDataStore dataStore = new LevelDataStore(this);
        LevelCelebration celebration = new LevelCelebration(this);
        LuckPermsGroupManager groupManager = new LuckPermsGroupManager(this);
        levelService = new LevelService(dataStore, celebration, groupManager);
        ActivityTracker tracker = new ActivityTracker(levelService);

        getServer().getPluginManager().registerEvents(new LevelListener(levelService, tracker, celebration), this);

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
