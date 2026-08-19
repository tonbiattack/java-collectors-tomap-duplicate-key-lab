# `Collectors.toMap`が重複SKUで失敗し、価格スナップショットを公開できない

Java標準ライブラリの`Collectors.toMap`を題材に、**同じSKUを含む更新リストを集約したとき、価格スナップショットが公開されない**問題を、失敗するテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は成功状態に保ち、意図的に失敗する状態はGit履歴に独立して残します。

## この題材で守る契約

> 既存の`SKU-01=80`に対して、`SKU-01=100`、`SKU-02=200`、`SKU-01=120`を順に取り込む場合、後着の`SKU-01=120`を採用し、`SKU-02=200`とともに新しいスナップショットを公開する。

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | 同じSKUを二回含む固定更新リストをリフレッシュする | `PUBLISHED`ではなく`REJECTED_DUPLICATE_SKU`になり、旧Mapと旧バージョンが残る |
| 観測 | 同じリストを二引数版と三引数版の`toMap`へ渡す | マージ関数なしでは`IllegalStateException`、後着値を返すマージ関数では新しいMapになる |
| 修正 | `(earlier, later) -> later`をCollectorへ渡す | 重複SKUに対する「後着値を採用する」規則が明示される |
| 回帰防止 | 同じサービステストを再実行する | 重複SKUの更新と重複なし更新が、ともに公開される |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| テストランナー | JUnit Jupiter 5.11.4 |
| アプリケーションフレームワーク | 不使用 |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、3テストがすべて成功します。

## バグを再現する

```bash
git checkout 05c5fde
mvn --batch-mode test -Dtest=PriceSnapshotServiceTest
# expected: <PUBLISHED> but was: <REJECTED_DUPLICATE_SKU>
# expected: <{SKU-01=120, SKU-02=200}> but was: <{SKU-01=80}>
# expected: <2> but was: <1>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

バグコミットでは設定やコンパイルではなく、重複SKUを含む更新の公開契約だけが失敗します。完全な出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

## 原因の要点

`Collectors.toMap(keyMapper, valueMapper)`には、重複キーが出現したときにどちらの値を採用するかという規則がありません。そのため、重複キーを含むStreamを集約すると`IllegalStateException`を送出します。[1]

本教材では更新リストが入力順で確定し、後から現れた値を採用するという契約を選びました。三引数版の`toMap`へ`(earlier, later) -> later`というマージ関数を渡すと、同じSKUで競合したときに後着値を選べます。[1]

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・回帰保証
│   ├── novelty-report.md        # 既存Java記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-service-test-output.txt
│   ├── 02-tomap-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../pricing/
│   ├── PriceSnapshotService.java
│   ├── PriceUpdate.java
│   └── RefreshOutcome.java
└── src/test/java/.../pricing/
    ├── CollectorsToMapObservationTest.java
    └── PriceSnapshotServiceTest.java
```

詳細な調査手順は[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## スコープ

この教材は、更新の出現順が確定しており、後着値を採用するという狭い業務規則を対象にします。並列Stream、時刻を比較した競合解決、金額精度、重複データの監査、永続化、外部フィードからの取り込みは対象外です。実運用では、どの更新を優先するかを仕様として決めたうえで、マージ関数をその規則へ合わせてください。

## References

[1] [Oracle: `Collectors` — `toMap` overloads](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
