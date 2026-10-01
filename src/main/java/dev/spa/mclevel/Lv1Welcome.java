package dev.spa.mclevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Level;

/**
 * Lv1到達時の報酬付与と、次の目標（職業または土地）の案内を担う。
 * Vault・Jobs・GriefPreventionはコンパイル時依存にせず、リフレクションで読む。
 */
public final class Lv1Welcome {
    static final double REWARD_AMOUNT = 500.0;

    private final JavaPlugin plugin;

    public Lv1Welcome(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 報酬を渡せたら true。経済プラグインが無い・失敗した場合は false。 */
    public boolean giveReward(Player player) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (provider == null) {
                plugin.getLogger().warning("Vaultの経済が見つからないため、Lv1報酬を付与できませんでした。");
                return false;
            }
            Object economy = provider.getProvider();
            Method deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            Object response = deposit.invoke(economy, player, REWARD_AMOUNT);
            boolean success = (boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
            if (!success) {
                plugin.getLogger().warning("Lv1報酬の付与に失敗しました: " + player.getName());
            }
            return success;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Lv1報酬の付与に失敗しました: " + player.getName(), exception);
            return false;
        }
    }

    public void sendRewardMessage(Player player) {
        player.sendMessage(Component.text("◆ 到達ボーナスとして " + (int) REWARD_AMOUNT + "S を受け取りました。",
                NamedTextColor.GREEN));
    }

    /** 職業が未選択なら職業、選択済みで土地が無ければ土地を1つだけ案内する。両方済みなら何も出さない。 */
    public void sendNextGoal(Player player) {
        if (!hasJob(player)) {
            player.sendMessage(goalLine("次は職業を選ぼう。", "[クリックで職業一覧を開く]", "/jobs browse"));
        } else if (!hasClaim(player)) {
            player.sendMessage(goalLine("次は土地を守ろう。", "[クリックで金のシャベルを受け取る]", "/claimshovel"));
        }
    }

    private Component goalLine(String text, String button, String command) {
        return Component.text("▶ " + text + " ", NamedTextColor.YELLOW)
                .append(Component.text(button, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(command))));
    }

    /** 判定できない場合は「選択済み」と見なして職業案内を出さない。 */
    private boolean hasJob(Player player) {
        try {
            Class<?> jobs = Class.forName("com.gamingmesh.jobs.Jobs", true,
                    plugin.getServer().getPluginManager().getPlugin("Jobs").getClass().getClassLoader());
            Object manager = jobs.getMethod("getPlayerManager").invoke(null);
            Object jobsPlayer = manager.getClass().getMethod("getJobsPlayer", Player.class).invoke(manager, player);
            if (jobsPlayer == null) {
                return true;
            }
            Collection<?> progressions = (Collection<?>) jobsPlayer.getClass().getMethod("getJobProgression")
                    .invoke(jobsPlayer);
            for (Object progression : progressions) {
                Object job = progression.getClass().getMethod("getJob").invoke(progression);
                String name = (String) job.getClass().getMethod("getName").invoke(job);
                if (!"none".equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "職業の選択状況を確認できませんでした。", exception);
            return true;
        }
    }

    /** 判定できない場合は「保護済み」と見なして土地案内を出さない。 */
    private boolean hasClaim(Player player) {
        try {
            Object gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gp == null) {
                return true;
            }
            Object dataStore = gp.getClass().getField("dataStore").get(gp);
            Object playerData = dataStore.getClass().getMethod("getPlayerData", java.util.UUID.class)
                    .invoke(dataStore, player.getUniqueId());
            Collection<?> claims = (Collection<?>) playerData.getClass().getMethod("getClaims").invoke(playerData);
            return !claims.isEmpty();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "土地の保護状況を確認できませんでした。", exception);
            return true;
        }
    }
}
