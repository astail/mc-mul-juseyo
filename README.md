# MulJuseyo

**1 時間に 1 度「水を飲んでください」と本人のチャットへ通知する**プラグインです（Paper 用 / サーバー側のみ）。

ゲームに夢中になっていると、つい水分補給を忘れがち。MulJuseyo は各プレイヤーへ一定間隔（**既定 60 分**）でリマインドを送り、現実の水分補給を促します。

> **クライアント MOD は不要です。** サーバーにこのプラグインを入れるだけで、バニラのクライアントでもそのまま通知が届きます。

## 解決する課題

「集中していて、長時間まったく水を飲んでいなかった」。
MulJuseyo はプレイヤーごとに時間を計り、**一定間隔（既定 1 時間）でチャットとベル音でやさしく知らせます**。

## 主な機能

- **定期リマインド**: プレイヤーごとに時間を計り、設定した間隔（既定 60 分）で「水を飲んでください」と本人のチャットへ通知。ベル音も鳴らせます。
- **プレイヤーごとに計測**: ログインからの経過で計るため、全員に一斉ではなく各自のタイミングで届きます。
- **水分補給の記録**: `/muljuseyo drink` で「飲んだ」と申告するとタイマーをリセットし、次の通知を先送りします。
- **ミュート / 全体 ON-OFF**: 自分だけ通知を止める `mute`、サーバー全体を止める `on|off`。
- **メッセージ変更**: 通知文を `config.yml` で自由に差し替え可能。

## 動作要件

- サーバー: Paper 26.1.2（build 69 以上）
- Java: 25
- クライアント: バニラで可（MOD 不要・サーバー側のみ）

## 導入

1. `MulJuseyo-1.0.0.jar` を `plugins/` に置いてサーバーを再起動します。
2. 以上でリマインドが始まります。各プレイヤーはログインから 60 分ごとに通知を受け取ります。

例:

```text
[MulJuseyo] 💧 水を飲んでください！
```

## 使い方

設定はデフォルトのままでも動作します。挙動を変えたいときは `config.yml`（後述）か、ゲーム内コマンドで調整します。

- 水を飲んだのでタイマーをリセットしたい → `/muljuseyo drink`
- しばらく通知を止めたい（自分だけ） → `/muljuseyo mute` ／ 戻す → `/muljuseyo unmute`
- 現在の設定・次の通知までの時間を見たい → `/muljuseyo status`

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/muljuseyo drink` | 水分補給を記録し、次回通知までのタイマーをリセット | `muljuseyo.use` |
| `/muljuseyo status` | 現在の設定（間隔など）と自分の次回通知までの時間を表示 | `muljuseyo.use` |
| `/muljuseyo mute \| unmute` | 自分への通知を停止 / 再開（再起動でリセット） | `muljuseyo.use` |
| `/muljuseyo on \| off` | リマインドの全体 有効 / 無効 | `muljuseyo.manage` |
| `/muljuseyo reload` | 設定を再読み込み | `muljuseyo.manage` |

エイリアス: `/mj`, `/water`

## 権限

| 権限ノード | 説明 | 既定 |
|---|---|---|
| `muljuseyo.notify` | 水分補給リマインドを受け取る | `true`（全員） |
| `muljuseyo.use` | `drink` / `status` / `mute` などの操作 | `true`（全員） |
| `muljuseyo.manage` | `on` / `off` / `reload` などサーバー全体に影響する操作 | `op` |

## 設定（`config.yml`）

```yaml
enabled: true              # リマインドを行うか（/muljuseyo on|off で切替）
interval-minutes: 60       # 何分ごとに通知するか（1〜1440・既定 60 = 1 時間）
remind-on-join: false      # ログイン直後にも一度通知するか
notify-sound: true         # 通知時にベル音を鳴らすか
message: "水を飲んでください！"  # 通知に表示するメッセージ本文
```

## 仕組み / 技術メモ

- プレイヤーごとに「次に通知する時刻」を保持し、内部タスク（`runTaskTimer`、10 秒間隔）で期限到来を確認します。ログイン時に `今 + 間隔` を仕込み、期限が来たら通知して次回を `今 + 間隔` へ積み直します。
- 一斉通知ではなく**各プレイヤーのログインからの経過で計測**するため、通知タイミングはプレイヤーごとに分散します。
- ミュート中・無効中に過ぎた期限は通知を飛ばすだけで次回を積み直すため、**ミュート解除直後や `/muljuseyo on` 直後に一斉通知が起きません**。
- 通知は本人のチャットに届きます。`notify-sound` でベル音を併用できます。

### 制限

- 通知粒度は内部チェック間隔（10 秒）ぶんだけ遅れることがありますが、1 時間間隔の用途では実質問題になりません。
- 次回通知時刻・ミュート状態は永続化しません（サーバー再起動でリセット）。

## ビルド

```bash
./deploy.sh        # Mac ネイティブ（JDK 25 + Maven）。生成物: target/MulJuseyo-1.0.0.jar
# または
mvn -B clean package
```

`v*` タグを push すると GitHub Actions（`.github/workflows/build.yml`）がビルドし、リリースに jar を添付します。

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手は次の 2 通り（A・B）です。Docker（itzg/minecraft-server）を使う場合は、後述の「Docker Compose で自動ダウンロード」も利用できます。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/mc-mul-juseyo/releases) から最新の `MulJuseyo-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/mc-mul-juseyo --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/MulJuseyo-1.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/MulJuseyo-1.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/MulJuseyo-1.0.0.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

### Docker Compose（itzg/minecraft-server）で自動ダウンロード

[`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) イメージを使う場合は、jar を手元に用意しなくても **`PLUGINS` 環境変数にリリースの URL を並べるだけ**で、起動時に自動ダウンロードして `plugins/` に配置できます。

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/mc-mul-juseyo/releases/download/v1.0.0/MulJuseyo-1.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。バージョンを更新したら、URL の `v1.0.0` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v1.0.0/MulJuseyo-1.0.0.jar`）。

起動ログに以下が出れば成功です。

```text
[MulJuseyo] MulJuseyo を有効化しました（間隔: 60 分 / 状態: ON）。
```

## ライセンス

MIT License — [LICENSE](LICENSE) を参照。
