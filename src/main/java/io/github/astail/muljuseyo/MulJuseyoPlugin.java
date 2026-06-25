package io.github.astail.muljuseyo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MulJuseyo 本体。
 * 一定間隔（既定 60 分）で各オンラインプレイヤーへ「水を飲んでください」と本人のチャットへ通知する。
 * サーバー側のみで動作（クライアント MOD 不要）。
 */
public final class MulJuseyoPlugin extends JavaPlugin implements Listener {

    /** リマインドの期限到来をチェックする内部間隔（tick）。20 tick = 1 秒なので 10 秒ごとに見る。 */
    private static final long CHECK_INTERVAL_TICKS = 200L;

    /** ベル音（通知時に鳴らす）。 */
    private static final Sound ALERT_SOUND =
            Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 0.8f, 1.0f);

    /** プレイヤーごとの「次に通知する時刻」（epoch ミリ秒）。在席中のみ保持し、退出で破棄する。 */
    private final Map<UUID, Long> nextAt = new HashMap<>();
    /** /muljuseyo mute で通知を一時停止しているプレイヤー（永続化しない＝再起動でリセット）。 */
    private final Set<UUID> muted = new HashSet<>();

    private boolean active;
    private int intervalMinutes;
    private boolean remindOnJoin;
    private boolean notifySound;
    private String message;

    private BukkitTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        if (!register()) {
            // コマンド登録に失敗した場合は disablePlugin 済み。task の起動も成功ログも出さずに離脱する。
            return;
        }
        // /reload 等で既にオンラインのプレイヤーがいる場合に備えてスケジュールしておく。
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            nextAt.put(player.getUniqueId(), now + intervalMillis());
        }
        startTask();
        getLogger().info("MulJuseyo を有効化しました（間隔: " + intervalMinutes
                + " 分 / 状態: " + (active ? "ON" : "OFF") + "）。");
    }

    @Override
    public void onDisable() {
        stopTask();
        nextAt.clear();
        muted.clear();
    }

    /**
     * plugin.yml のコマンドとリスナーを登録する。
     * 成功時は true、コマンド未定義でプラグインを無効化した場合は false を返す。
     */
    private boolean register() {
        PluginCommand command = getCommand("muljuseyo");
        if (command == null) {
            getLogger().severe("plugin.yml に muljuseyo コマンドが定義されていません。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
        MulJuseyoCommand handler = new MulJuseyoCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(this, this);
        return true;
    }

    /** config.yml を読み直して設定値を反映する。 */
    private void loadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        active = config.getBoolean("enabled", true);
        intervalMinutes = (int) clamp(config.getInt("interval-minutes", 60), 1, 1440);
        remindOnJoin = config.getBoolean("remind-on-join", false);
        notifySound = config.getBoolean("notify-sound", true);
        message = config.getString("message", "水を飲んでください！");
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private long intervalMillis() {
        return (long) intervalMinutes * 60_000L;
    }

    // ───────────────────────── スケジューラ ─────────────────────────

    private void startTask() {
        stopTask();
        task = getServer().getScheduler().runTaskTimer(this, this::tick, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 全オンラインプレイヤーの期限を確認し、到来していれば通知する（無効時は何もしない）。 */
    private void tick() {
        if (!active) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            // 在席だが未スケジュール（理論上は join で必ず入る）の場合は次回をセットして見送る。
            Long due = nextAt.get(id);
            if (due == null) {
                nextAt.put(id, now + intervalMillis());
                continue;
            }
            if (now < due) {
                continue;
            }
            // 期限到来。ミュート / 権限なしなら通知だけ飛ばし、次回はそのまま積み直す（沈黙が貯まらない）。
            if (!muted.contains(id) && player.hasPermission("muljuseyo.notify")) {
                sendReminder(player);
            }
            nextAt.put(id, now + intervalMillis());
        }
    }

    // ───────────────────────── イベント ─────────────────────────

    /** ログイン時に初回の通知時刻を仕込む。remind-on-join なら即時に 1 回通知する。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (active && remindOnJoin && player.hasPermission("muljuseyo.notify")) {
            sendReminder(player);
        }
        nextAt.put(player.getUniqueId(), now + intervalMillis());
    }

    /** 退出時に保持していた状態を破棄してメモリリークを防ぐ。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        nextAt.remove(id);
        muted.remove(id);
    }

    // ───────────────────────── 通知ロジック ─────────────────────────

    /** リマインド 1 件を本人へ送る（必要ならベル音も鳴らす）。 */
    private void sendReminder(Player player) {
        player.sendMessage(prefix()
                .append(Component.text("💧 ", NamedTextColor.AQUA))
                .append(Component.text(message, NamedTextColor.YELLOW)));
        if (notifySound) {
            player.playSound(ALERT_SOUND, Sound.Emitter.self());
        }
    }

    /**
     * 今すぐ本人へリマインドを送り、次回の通知時刻を「今 + 間隔」へリセットする（/muljuseyo drink 用）。
     * 「飲んだ」申告として使えるよう、タイマーを巻き戻す。
     */
    public void drinkNow(Player player) {
        nextAt.put(player.getUniqueId(), System.currentTimeMillis() + intervalMillis());
        player.sendMessage(prefix().append(Component.text(
                "水分補給を記録しました。次の通知は " + intervalMinutes + " 分後です。", NamedTextColor.GREEN)));
    }

    private Component prefix() {
        return Component.text("[MulJuseyo] ", NamedTextColor.AQUA);
    }

    // ───────────────────────── 公開ヘルパー（コマンドから利用） ─────────────────────────

    /** config と監視タスクを再読み込みし、全プレイヤーの次回通知時刻を「今 + 間隔」へ振り直す。 */
    public void reloadAll() {
        loadSettings();
        long now = System.currentTimeMillis();
        nextAt.clear();
        for (Player player : getServer().getOnlinePlayers()) {
            nextAt.put(player.getUniqueId(), now + intervalMillis());
        }
        startTask();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        this.active = value;
        getConfig().set("enabled", value);
        saveConfig();
        // ON に戻したとき、無効中に過ぎた期限で一斉通知しないよう次回を積み直す。
        if (value) {
            long now = System.currentTimeMillis();
            for (Player player : getServer().getOnlinePlayers()) {
                nextAt.put(player.getUniqueId(), now + intervalMillis());
            }
        }
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public boolean isRemindOnJoin() {
        return remindOnJoin;
    }

    public boolean isNotifySound() {
        return notifySound;
    }

    public boolean isMuted(Player player) {
        return muted.contains(player.getUniqueId());
    }

    public void setMuted(Player player, boolean mute) {
        if (mute) {
            muted.add(player.getUniqueId());
        } else {
            muted.remove(player.getUniqueId());
        }
    }

    /** 本人の次回通知までの残り分（切り上げ）。スケジュール前や期限超過時は 0。 */
    public long minutesUntilNext(Player player) {
        Long due = nextAt.get(player.getUniqueId());
        if (due == null) {
            return 0;
        }
        long remainMillis = due - System.currentTimeMillis();
        if (remainMillis <= 0) {
            return 0;
        }
        return (remainMillis + 59_999L) / 60_000L;
    }
}
