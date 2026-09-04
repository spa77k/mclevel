package dev.spa.mclevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /level — 現在のレベルと次レベルへの進捗（プレイ時間・実績数）を表示する。
 */
public final class LevelCommand implements CommandExecutor, TabCompleter {
    private final LevelService levelService;

    public LevelCommand(LevelService levelService) {
        this.levelService = levelService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤー専用です。");
            return true;
        }

        LevelTier current = LevelTier.fromValue(levelService.getLevel(player));
        long activeSeconds = levelService.getActiveSeconds(player);
        int achievements = levelService.countAchievements(player);
        long selfIncomeCents = levelService.getSelfIncomeCents(player);

        player.sendMessage(Component.text("=== レベル制度 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("現在レベル: Lv" + current.getValue(), NamedTextColor.AQUA)
                .append(Component.text(" (" + current.getUnlockDescription() + ")", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("アクティブプレイ時間: " + formatHours(activeSeconds), NamedTextColor.WHITE));
        player.sendMessage(Component.text("実績達成数: " + achievements + " 個", NamedTextColor.WHITE));
        player.sendMessage(Component.text("累計自力収入: " + formatMoney(selfIncomeCents), NamedTextColor.WHITE));

        LevelTier next = current.next();
        if (next == null) {
            player.sendMessage(Component.text("最大レベルに到達しています！", NamedTextColor.LIGHT_PURPLE));
        } else {
            player.sendMessage(Component.text("次の Lv" + next.getValue() + " まで:", NamedTextColor.YELLOW));
            player.sendMessage(progressLine("  プレイ時間", formatHours(activeSeconds),
                    formatHours(next.getRequiredPlaySeconds()),
                    activeSeconds >= next.getRequiredPlaySeconds()));
            player.sendMessage(progressLine("  実績", achievements + " 個",
                    next.getRequiredAchievements() + " 個",
                    achievements >= next.getRequiredAchievements()));
            if (next.getRequiredSelfIncomeCents() > 0) {
                player.sendMessage(progressLine("  累計自力収入", formatMoney(selfIncomeCents),
                        formatMoney(next.getRequiredSelfIncomeCents()),
                        selfIncomeCents >= next.getRequiredSelfIncomeCents()));
            }
        }
        return true;
    }

    private Component progressLine(String label, String current, String required, boolean done) {
        return Component.text(label + ": " + current + " / " + required,
                done ? NamedTextColor.GREEN : NamedTextColor.RED)
                .append(Component.text(done ? "  ✔" : "", NamedTextColor.GREEN));
    }

    private String formatHours(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + " 時間 " + minutes + " 分";
    }

    private String formatMoney(long cents) {
        if (cents % 100 == 0) {
            return String.format(Locale.ROOT, "%,d S", cents / 100);
        }
        return String.format(Locale.ROOT, "%,.2f S", cents / 100.0);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
