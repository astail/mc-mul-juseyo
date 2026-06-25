# CLAUDE.md

Claude がこのリポジトリで作業する際の開発メモ（Paper プラグイン）。

## プラグインの目的

MulJuseyo は、通知を **ON にした（オプトイン）オンラインプレイヤー**へ一定間隔（既定 60 分 = 1 時間に 1 度）で
**「水を飲んでください」と本人のチャットへ通知** する。現実の水分補給を促すためのプラグイン。
通知は既定 OFF で、各プレイヤーが `/muljuseyo on` で受信を開始する。サーバー側のみで動き、クライアント MOD は不要。

## ビルド要件

- Java 25 + Maven。生成物は `MulJuseyo-1.0.0.jar`。
- 唯一の依存は `io.papermc.paper:paper-api:26.1.2.build.69-stable`（provided）。
- ローカルビルドは `./deploy.sh`（Homebrew `openjdk@25` を想定）。

## アーキテクチャ構成

- **MulJuseyoPlugin**: 本体。設定読込、`runTaskTimer` による定期チェック、リマインドの組み立て・送信、
  プレイヤーごとの次回通知時刻（`nextAt`）とオプトイン集合（`notifyEnabled`）の管理、`players.yml` への
  永続化（`loadPlayers` / `savePlayers`）、`PlayerJoinEvent` / `PlayerQuitEvent` での状態セットアップ・破棄。
- **MulJuseyoCommand**: `/muljuseyo <status|drink|on|off|reload>` の実処理とタブ補完。

## 設計上の要点

- **通知タイミングは「プレイヤーごとの期限」方式**: グローバルな一斉通知ではなく、プレイヤーごとに
  「次に通知する時刻」（`nextAt`、epoch ミリ秒）を持つ。ログイン時に `now + 間隔` を仕込み、期限が来たら
  通知して `nextAt` を `now + 間隔` へ積み直す。各自のログインからの経過で 1 時間ごとに通知される。
- **チェックは粗いポーリング**: 内部タスクは `CHECK_INTERVAL_TICKS`（200 tick = 10 秒）ごとに全員の期限を見る。
  間隔（分単位）に対して十分細かく、負荷も小さい。リマインド間隔そのものは `interval-minutes` で設定する。
- **通知はオプトイン（既定 OFF）**: `notifyEnabled` 集合に入ったプレイヤーだけが通知対象。`/muljuseyo on` で
  追加、`/muljuseyo off` で除外。`setNotifyEnabled(true)` では在席者の `nextAt` を `now + 間隔` へ積み直す。
- **沈黙が貯まらない**: 通知 OFF・権限なし・無効中に過ぎた期限は通知を飛ばすだけで、次回は必ず
  `now + 間隔` に積み直す。これにより `/muljuseyo on` 直後に一斉通知が起きない。
- **`/muljuseyo drink`**: 「水を飲んだ」申告。`nextAt` を `now + 間隔` に巻き戻し、次の通知を先送りする。
- **メッセージはベル音併用可**: 通知は Adventure の `Component`（プレフィックス AQUA + 💧 + 本文 YELLOW）で送り、
  `notify-sound: true` なら `block.note_block.bell` を本人に鳴らす。本文は `message` で差し替え可能。
- **権限は 2 段階**: `on`/`off`・`drink`・`status` は `muljuseyo.use`（既定 true）。サーバー全体に影響する
  `reload` は `muljuseyo.manage`（既定 op）を別途要求（`MulJuseyoCommand#requireManage`）。
  通知の受信可否は `muljuseyo.notify`（既定 true）。サーバー全体の有効/無効は `config.yml` の `enabled` ＋ `/muljuseyo reload`。
- **オプトイン状態は永続**: `notifyEnabled` は `<dataFolder>/players.yml` の `notify-enabled`（UUID 群）へ保存し、
  `onEnable` で復元する（`loadPlayers` / `savePlayers`。`onDisable`・`setNotifyEnabled` の都度に保存）。
  `nextAt` は非永続（在席中のみ・再起動でリセット）。`/muljuseyo reload` は config と監視タスクを読み直し、
  在席者の `nextAt` を積み直す（オプトイン状態は維持）。

## 既知の制限 / 注意

- 通知粒度は内部チェック間隔（10 秒）ぶんだけ遅れうる。1 時間間隔の用途では実質問題にならない。
- リマインドは在席中かつ通知 ON のオンラインプレイヤーのみが対象。オフライン中は時間が進んでもログイン時に
  `now + 間隔` から再スタートする（ログイン直後にまとめて通知することはない）。
- オプトイン状態（`notifyEnabled`）は `players.yml` に永続化され再起動後も維持される。次回時刻（`nextAt`）は非永続。
- `interval-minutes` は 1〜1440 にクランプ。`remind-on-join: true` なら通知 ON のプレイヤーにログイン直後にも 1 回通知する。

## リリース手順

- セマンティックバージョニング。`v*` タグの push で GitHub Actions（`.github/workflows/build.yml`）がビルドし、
  `gh release create --generate-notes` で jar を添付する。
- サーバーへの配置（Releases から DL、または Docker `itzg/minecraft-server` の `PLUGINS` 環境変数で自動 DL）は
  README の「サーバーへの配置」を参照。
