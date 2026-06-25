package io.github.astail.muljuseyo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** /muljuseyo コマンドの実処理とタブ補完。 */
public final class MulJuseyoCommand implements CommandExecutor, TabCompleter {

    private final MulJuseyoPlugin plugin;

    public MulJuseyoCommand(MulJuseyoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> requirePlayer(sender, player -> setNotify(player, true));
            case "off" -> requirePlayer(sender, player -> setNotify(player, false));
            case "drink" -> requirePlayer(sender, plugin::drinkNow);
            case "reload" -> { if (requireManage(sender)) doReload(sender); }
            case "status" -> sendStatus(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ───────────── サブコマンド ─────────────

    private void setNotify(Player player, boolean enabled) {
        plugin.setNotifyEnabled(player, enabled);
        player.sendMessage(enabled
                ? ok("通知をONにしました。次の通知は " + plugin.getIntervalMinutes() + " 分後です。")
                : ok("通知をOFFにしました。"));
    }

    private void doReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(ok("設定を再読み込みしました（間隔: " + plugin.getIntervalMinutes() + " 分）。"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(info("MulJuseyo: " + (plugin.isActive() ? "ON" : "OFF")
                + " / 間隔 " + plugin.getIntervalMinutes() + " 分"
                + " / ログイン時通知 " + (plugin.isRemindOnJoin() ? "あり" : "なし")
                + " / 通知音 " + (plugin.isNotifySound() ? "あり" : "なし")));
        if (sender instanceof Player player) {
            sender.sendMessage(info("あなたの通知: " + (plugin.isNotifyEnabled(player)
                    ? "ON（次まで約 " + plugin.minutesUntilNext(player) + " 分）"
                    : "OFF")));
        }
        sendUsage(sender);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(info("/muljuseyo on | off         - 自分への通知を ON / OFF（既定 OFF）"));
        sender.sendMessage(info("/muljuseyo drink            - 水分補給を記録してタイマーをリセット"));
        sender.sendMessage(info("/muljuseyo status           - 現在の設定を表示"));
        if (sender.hasPermission("muljuseyo.manage")) {
            sender.sendMessage(info("/muljuseyo reload           - 設定を再読み込み"));
        }
    }

    // ───────────── 補助 ─────────────

    private boolean requireManage(CommandSender sender) {
        if (sender.hasPermission("muljuseyo.manage")) {
            return true;
        }
        sender.sendMessage(error("この操作（reload）はサーバー管理者のみ実行できます。"));
        return false;
    }

    private void requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            sender.sendMessage(error("このサブコマンドはプレイヤーが実行してください。"));
        }
    }

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    // ───────────── タブ補完 ─────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("on", "off", "drink", "status"));
            if (sender.hasPermission("muljuseyo.manage")) {
                subs.add("reload");
            }
            return prefix(subs, args[0]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
