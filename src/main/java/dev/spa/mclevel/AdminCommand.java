package dev.spa.mclevel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /mclevel set <プレイヤー名> <0-3> — 管理者がプレイヤーのレベルを強制設定する。
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final String USAGE = "/mclevel set <プレイヤー名> <0-3>";
    private static final String INCOME_USAGE = "/mclevel income add <プレイヤー名> <金額> quest";
    private final LevelService levelService;

    public AdminCommand(LevelService levelService) {
        this.levelService = levelService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("income")) {
            return handleIncome(sender, args);
        }

        if (args.length != 3 || !args[0].equalsIgnoreCase("set")) {
            sendError(sender, "使用方法: " + USAGE);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sendError(sender, "プレイヤー「" + args[1] + "」はオンラインではありません。");
            return true;
        }

        Integer level = parseLevel(args[2]);
        if (level == null) {
            sendError(sender, "レベルは 0〜" + LevelTier.maxValue() + " の整数で指定してください。");
            return true;
        }

        levelService.setLevel(target, level);

        sender.sendMessage(Component.text(target.getName() + " のレベルを Lv" + level + " に設定しました。", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleIncome(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sendError(sender, "収入の記録はサーバー内部処理からのみ実行できます。");
            return true;
        }
        if (args.length != 5
                || !args[1].equalsIgnoreCase("add")
                || !args[4].equalsIgnoreCase("quest")) {
            sendError(sender, "使用方法: " + INCOME_USAGE);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sendError(sender, "プレイヤー「" + args[2] + "」はオンラインではありません。");
            return true;
        }

        Double amount = parseAmount(args[3]);
        if (amount == null) {
            sendError(sender, "収入額は0より大きい数値で指定してください。");
            return true;
        }

        levelService.addSelfIncome(target, amount);
        return true;
    }

    /** 0〜maxValue の範囲の整数としてパースする。範囲外・パース失敗時は null。 */
    private Integer parseLevel(String arg) {
        try {
            int level = Integer.parseInt(arg);
            return (level < 0 || level > LevelTier.maxValue()) ? null : level;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseAmount(String arg) {
        try {
            double amount = Double.parseDouble(arg);
            return !Double.isFinite(amount) || amount <= 0.0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "income");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return List.of("0", "1", "2", "3");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("income")) {
            return List.of("add");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("income")
                && args[1].equalsIgnoreCase("add")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
