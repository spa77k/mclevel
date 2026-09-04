package dev.spa.mclevel;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** Jobs RebornのAPIをコンパイル時依存にせず、正のMoney支払いだけをMcLevelへ渡す。 */
public final class JobsIncomeBridge {
    private static final String PAYMENT_EVENT_CLASS = "com.gamingmesh.jobs.api.JobsPaymentEvent";

    private JobsIncomeBridge() {
    }

    public static void register(JavaPlugin plugin, LevelService levelService) {
        Plugin jobs = plugin.getServer().getPluginManager().getPlugin("Jobs");
        if (jobs == null) {
            plugin.getLogger().warning("Jobsが見つからないため、Jobs収入の計測を無効にします。");
            return;
        }

        try {
            Class<? extends Event> eventClass = Class.forName(
                    PAYMENT_EVENT_CLASS,
                    true,
                    jobs.getClass().getClassLoader()
            ).asSubclass(Event.class);
            Method getPlayer = eventClass.getMethod("getPlayer");
            Method getAmount = eventClass.getMethod("getAmount");
            Listener listener = new Listener() {
            };

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.MONITOR,
                    (ignored, event) -> {
                        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                            return;
                        }
                        try {
                            Object playerValue = getPlayer.invoke(event);
                            Object amountValue = getAmount.invoke(event);
                            if (!(playerValue instanceof OfflinePlayer player)
                                    || !(amountValue instanceof Number number)) {
                                return;
                            }
                            double amount = number.doubleValue();
                            plugin.getServer().getScheduler().runTask(
                                    plugin,
                                    () -> levelService.addSelfIncome(player, amount)
                            );
                        } catch (ReflectiveOperationException | RuntimeException exception) {
                            plugin.getLogger().warning("Jobs収入イベントの読み取りに失敗しました: "
                                    + exception.getMessage());
                        }
                    },
                    plugin
            );
            plugin.getLogger().info("Jobsの正のMoney支払いを累計自力収入として計測します。");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Jobs収入の計測登録に失敗しました: " + exception.getMessage());
        }
    }
}
