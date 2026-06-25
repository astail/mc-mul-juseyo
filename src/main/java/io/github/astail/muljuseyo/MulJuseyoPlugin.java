package io.github.astail.muljuseyo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    /** /muljuseyo on で通知を受け取ることにしたプレイヤー（オプトイン）。既定 OFF。players.yml に永続化する。 */
    private final Set<UUID> notifyEnabled = new HashSet<>();

    private boolean active;
    private int intervalMinutes;
    private boolean remindOnJoin;
    private boolean notifySound;
    private String message;

    private BukkitTask task;
    /** オプトイン状態の永続化先（<dataFolder>/players.yml）。loadPlayers() で初期化される。 */
    private File playersFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        if (!register()) {
            // コマンド登録に失敗した場合は disablePlugin 済み。task の起動も成功ログも出さずに離脱する。
            return;
        }
        loadPlayers();
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
        // クリアより先に保存する（空集合で上書きしないため）。
        savePlayers();
        nextAt.clear();
        notifyEnabled.clear();
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

    // ───────────────────────── オプトイン状態の永続化 ─────────────────────────

    /** players.yml から opt-in（notify-enabled）の UUID 群を読み込む。ファイルが無ければ空のまま。 */
    private void loadPlayers() {
        playersFile = new File(getDataFolder(), "players.yml");
        notifyEnabled.clear();
        if (!playersFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        for (String raw : yaml.getStringList("notify-enabled")) {
            try {
                notifyEnabled.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("players.yml の不正な UUID をスキップします: " + raw);
            }
        }
    }

    /** opt-in（notify-enabled）の UUID 群を players.yml へ保存する。初期化前（loadPlayers 未実行）は何もしない。 */
    private void savePlayers() {
        if (playersFile == null) {
            return;
        }
        List<String> ids = new ArrayList<>(notifyEnabled.size());
        for (UUID id : notifyEnabled) {
            ids.add(id.toString());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notify-enabled", ids);
        try {
            yaml.save(playersFile);
        } catch (IOException ex) {
            getLogger().warning("players.yml の保存に失敗しました: " + ex.getMessage());
        }
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
            // 期限到来。opt-in（on）かつ権限ありのときだけ通知。OFF / 権限なしは飛ばすだけで次回は積み直す（沈黙が貯まらない）。
            if (notifyEnabled.contains(id) && player.hasPermission("muljuseyo.notify")) {
                sendReminder(player);
            }
            nextAt.put(id, now + intervalMillis());
        }
    }

    // ───────────────────────── イベント ─────────────────────────

    /** ログイン時に初回の通知時刻を仕込む。opt-in 済みで remind-on-join なら即時に 1 回通知する。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (active && remindOnJoin && notifyEnabled.contains(id) && player.hasPermission("muljuseyo.notify")) {
            sendReminder(player);
        }
        nextAt.put(id, now + intervalMillis());
    }

    /** 退出時に次回通知時刻を破棄してメモリリークを防ぐ。opt-in 状態（notifyEnabled）は再ログインに備えて維持する。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        nextAt.remove(id);
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
     * 「水を飲んだ」申告を記録する（/muljuseyo drink 用）。
     * 次回の通知時刻を「今 + 間隔」へリセットしてタイマーを巻き戻し、本人へ確認メッセージを送る。
     * リマインド自体は送らない。
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

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public boolean isRemindOnJoin() {
        return remindOnJoin;
    }

    public boolean isNotifySound() {
        return notifySound;
    }

    public boolean isNotifyEnabled(Player player) {
        return notifyEnabled.contains(player.getUniqueId());
    }

    public void setNotifyEnabled(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (enabled) {
            notifyEnabled.add(id);
            // ON 化直後に、ためていた期限で即通知が飛ばないよう次回を「今 + 間隔」へ積み直す。
            nextAt.put(id, System.currentTimeMillis() + intervalMillis());
        } else {
            notifyEnabled.remove(id);
        }
        savePlayers();
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
