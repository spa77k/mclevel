package dev.spa.mclevel;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class McLevelPlugin extends JavaPlugin {
    private static final long SECOND_TICKS = 20L;          // 1 秒
    private static final long AUTOSAVE_TICKS = 20L * 300L; // 5 分

    private LevelService levelService;

    @Override
    public void onEnable() {
        LevelDataStore dataStore = new LevelDataStore(this);
        levelService = new LevelService(dataStore);
        ActivityTracker tracker = new ActivityTracker(levelService);

        getServer().getPluginManager().registerEvents(new LevelListener(levelService, tracker), this);

        PluginCommand levelCommand = getCommand("level");
        if (levelCommand != null) {
            LevelCommand executor = new LevelCommand(levelService);
            levelCommand.setExecutor(executor);
            levelCommand.setTabCompleter(executor);
        } else {
            getLogger().warning("level コマンドの登録に失敗しました。plugin.yml を確認してください。");
        }

        // 既にオンラインのプレイヤー（/reload 時など）を読み込む。
        for (Player player : getServer().getOnlinePlayers()) {
            levelService.load(player);
            tracker.start(player);
        }

        // 1 秒ごとのアクティブ時間積算・昇格判定。
        getServer().getScheduler().runTaskTimer(this, tracker::tick, SECOND_TICKS, SECOND_TICKS);
        // 定期オートセーブ。
        getServer().getScheduler().runTaskTimer(this, levelService::saveAll, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (levelService != null) {
            levelService.saveAll();
        }
    }
}
