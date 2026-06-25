# MulJuseyo

**1 時間に 1 度「水を飲んでください」と本人のチャットへ通知する**プラグインです（Paper 用 / サーバー側のみ）。

ゲームに夢中になっていると、つい水分補給を忘れがち。MulJuseyo は通知を **ON にした各プレイヤー**へ一定間隔（**既定 60 分**）でリマインドを送り、現実の水分補給を促します。

> **通知は既定 OFF（オプトイン）です。** 各プレイヤーが `/muljuseyo on` を実行すると受信を開始します。一度 ON にすると再起動後も維持されます。
>
> **クライアント MOD は不要です。** サーバーにこのプラグインを入れるだけで、バニラのクライアントでもそのまま動作します。

## 解決する課題

「集中していて、長時間まったく水を飲んでいなかった」。
MulJuseyo はプレイヤーごとに時間を計り、**一定間隔（既定 1 時間）でチャットとベル音でやさしく知らせます**。

## 主な機能

- **オプトイン通知（既定 OFF）**: `/muljuseyo on` を実行した本人だけにリマインドが届きます。`/muljuseyo off` で停止します。
- **ON/OFF は永続化**: 通知の ON/OFF は `players.yml` に保存され、再ログイン・サーバー再起動後も維持されます。
- **定期リマインド**: プレイヤーごとに時間を計り、設定した間隔（既定 60 分）で「水を飲んでください」と本人のチャットへ通知。ベル音も鳴らせます。
- **プレイヤーごとに計測**: ログインからの経過で計るため、全員に一斉ではなく各自のタイミングで届きます。
- **水分補給の記録**: `/muljuseyo drink` で「飲んだ」と申告するとタイマーをリセットし、次の通知を先送りします。
- **メッセージ変更**: 通知文を `config.yml` で自由に差し替え可能。

## 動作要件

- サーバー: Paper 26.1.2（build 69 以上）
- Java: 25
- クライアント: バニラで可（MOD 不要・サーバー側のみ）

## 導入

1. `MulJuseyo-2.0.0.jar` を `plugins/` に置いてサーバーを再起動します。
2. 通知は既定 OFF です。受け取りたいプレイヤーは `/muljuseyo on` を実行します。以降、間隔（既定 60 分）ごとに通知が届きます。

例:

```text
[MulJuseyo] 💧 水を飲んでください！
```

## 使い方

通知は既定 OFF です。受け取りたいプレイヤーは自分で ON にします。挙動を変えたいときは `config.yml`（後述）か、ゲーム内コマンドで調整します。

- 通知を受け取りたい（自分だけ） → `/muljuseyo on` ／ 止める → `/muljuseyo off`（ON/OFF は再起動後も維持）
- 水を飲んだのでタイマーをリセットしたい → `/muljuseyo drink`
- 現在の設定・自分の通知 ON/OFF・次の通知までの時間を見たい → `/muljuseyo status`

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/muljuseyo on \| off` | 自分への通知を ON / OFF（既定 OFF・再起動後も維持） | `muljuseyo.use` |
| `/muljuseyo drink` | 水分補給を記録し、次回通知までのタイマーをリセット | `muljuseyo.use` |
| `/muljuseyo status` | 現在の設定（間隔など）と自分の通知 ON/OFF・次回通知までの時間を表示 | `muljuseyo.use` |
| `/muljuseyo reload` | 設定を再読み込み | `muljuseyo.manage` |

エイリアス: `/mj`, `/water`

## 権限

| 権限ノード | 説明 | 既定 |
|---|---|---|
| `muljuseyo.notify` | 水分補給リマインドを受け取れるか（受信可否。ON にしていてもこれが無いと届かない） | `true`（全員） |
| `muljuseyo.use` | `on` / `off` / `drink` / `status` の自分向け操作 | `true`（全員） |
| `muljuseyo.manage` | `reload` などサーバー全体に影響する操作 | `op` |

## 設定（`config.yml`）

```yaml
enabled: true              # サーバー全体でリマインドを有効にするか（編集後 /muljuseyo reload で反映）
interval-minutes: 60       # 何分ごとに通知するか（1〜1440・既定 60 = 1 時間）
remind-on-join: false      # ログイン直後にも一度通知するか（通知 ON のプレイヤーのみ）
notify-sound: true         # 通知時にベル音を鳴らすか
message: "水を飲んでください！"  # 通知に表示するメッセージ本文
```

> `enabled` はサーバー全体のスイッチ（管理者向け）です。各プレイヤーの通知 ON/OFF は `/muljuseyo on | off` で個別に切り替えます。

## 仕組み / 技術メモ

- プレイヤーごとに「次に通知する時刻」を保持し、内部タスク（`runTaskTimer`、10 秒間隔）で期限到来を確認します。ログイン時に `今 + 間隔` を仕込み、期限が来たら通知して次回を `今 + 間隔` へ積み直します。
- 一斉通知ではなく**各プレイヤーのログインからの経過で計測**するため、通知タイミングはプレイヤーごとに分散します。
- 通知 OFF・無効中に過ぎた期限は通知を飛ばすだけで次回を積み直すため、**`/muljuseyo on` 直後に一斉通知が起きません**（ON 化時は次回を `今 + 間隔` へ積み直します）。
- 通知は本人のチャットに届きます。`notify-sound` でベル音を併用できます。

### 制限

- 通知粒度は内部チェック間隔（10 秒）ぶんだけ遅れることがありますが、1 時間間隔の用途では実質問題になりません。
- 通知の ON/OFF（オプトイン状態）は `players.yml` に保存され、サーバー再起動後も維持されます。
- 次回通知時刻（`nextAt`）は永続化しません（在席中のみ保持・サーバー再起動でリセット）。

## ビルド

```bash
./deploy.sh        # Mac ネイティブ（JDK 25 + Maven）。生成物: target/MulJuseyo-2.0.0.jar
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

[ビルド](#ビルド) の手順で `target/MulJuseyo-2.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/MulJuseyo-2.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/MulJuseyo-2.0.0.jar <コンテナ名>:/data/plugins/
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
        https://github.com/astail/mc-mul-juseyo/releases/download/v2.0.0/MulJuseyo-2.0.0.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。バージョンを更新したら、URL の `v2.0.0` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v2.0.0/MulJuseyo-2.0.0.jar`）。

起動ログに以下が出れば成功です。

```text
[MulJuseyo] MulJuseyo を有効化しました（間隔: 60 分 / 状態: ON）。
```

## ライセンス

MIT License — [LICENSE](LICENSE) を参照。
