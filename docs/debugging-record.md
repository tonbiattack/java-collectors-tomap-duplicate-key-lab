# E003: `Collectors.toMap`が重複SKUを拒否し、価格スナップショットを公開しない

## 目的

価格更新は入力順に処理し、同じSKUが複数回現れた場合は最後の更新を採用します。旧スナップショット`SKU-01=80`に対し、`SKU-01=100`、`SKU-02=200`、`SKU-01=120`をリフレッシュした場合、`SKU-01=120`と`SKU-02=200`の新しいスナップショットを公開し、公開バージョンを`2`にする必要があります。

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4だけを使います。フレームワーク、HTTP、ファイル、データベース、並列処理は使いません。公開境界は`PriceSnapshotService#refresh(List<PriceUpdate>)`であり、直接結果として`RefreshOutcome`を、最終状態として`currentPrices()`と`publishedVersion()`を別々に読みます。

テストでは、最初に重複なしの`SKU-01=80`を公開して旧スナップショットを作ります。その後に重複SKUを含む固定更新リストを与えます。このため、公開が失敗した場合に「新しいMapが不完全」なのか「旧Mapのまま」なのかを区別できます。

## 最初に観測した事実

バグ状態はコミット[`05c5fde`](../commit/05c5fde)です。次のコマンドで、意図したアサーション差分を確認しました。

```bash
git checkout 05c5fde
mvn --batch-mode test -Dtest=PriceSnapshotServiceTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 直接結果 | `PUBLISHED` | `REJECTED_DUPLICATE_SKU` | `PriceSnapshotServiceTest` |
| 最終スナップショット | `{SKU-01=120, SKU-02=200}` | `{SKU-01=80}` | `PriceSnapshotService#currentPrices()` |
| 公開バージョン | `2` | `1` | `PriceSnapshotService#publishedVersion()` |
| マージ関数なしの`toMap` | 重複キーを受理できない | `IllegalStateException`、メッセージに`Duplicate key SKU-01`を含む | `CollectorsToMapObservationTest` |
| 後着値マージ付きの`toMap` | 後着の`SKU-01=120`を採用 | `{SKU-01=120, SKU-02=200}` | `CollectorsToMapObservationTest` |

```text
同じSKUの後着更新があっても新しいスナップショットを公開する
==> expected: <PUBLISHED> but was: <REJECTED_DUPLICATE_SKU>

最終スナップショットには後着のSKU-01価格と新SKUを保存する
==> expected: <{SKU-01=120, SKU-02=200}> but was: <{SKU-01=80}>

初期公開と重複SKUを含む更新の二回を公開済みとして数える
==> expected: <2> but was: <1>
```

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。直接の結果だけではなく、Mapと公開バージョンを最終状態として分けて確認したため、単に結果コードだけを誤って返した可能性は除外できます。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| 重複SKUは業務ルール上、拒否すべきである | 重複なし更新の対照テストと、題材契約の後着値優先規則を比較する | 重複なし更新は公開される。今回の契約は後着更新を採用すると明示しているため棄却。 |
| 旧スナップショットの置換または公開順序が誤っている | 新SKUだけを持つ更新でMapとバージョンが変化する対照テストを実行する | `uniqueSku_updatesRemainPublished`が成功するため棄却。 |
| マージ関数なしの`Collectors.toMap`が重複キーを拒否する | 同じ更新リストを二引数版と三引数版の`toMap`へ直接渡す | 二引数版は`IllegalStateException`、後着値を返すマージ関数付きは期待Mapを返す。採用。 |

## 確定した原因

バグ状態のサービスは、次の二引数版で更新を集約していました。

```java
Map<String, Integer> nextPrices = updates.stream()
        .collect(Collectors.toMap(
                PriceUpdate::sku,
                PriceUpdate::priceYen));
```

二引数版の`Collectors.toMap`は、同じキーに対応する二つの値をどう解決するかを受け取りません。重複キーを含む入力では`IllegalStateException`を送出します。重複があり得る場合は、マージ関数を受け取るoverloadを選ぶ必要があります。[1]

サービスはこの例外を`REJECTED_DUPLICATE_SKU`へ変換するため、候補スナップショットの代入とバージョン更新まで到達せず、旧Mapと旧バージョンが残りました。例外変換は症状の表現であり、直接原因はCollectorに重複解決規則を渡していないことです。

## 最小修正

修正コミットは[`a9dd51a`](../commit/a9dd51a)です。変更はCollectorに一つのマージ関数を渡すことだけです。

```java
Map<String, Integer> nextPrices = updates.stream()
        .collect(Collectors.toMap(
                PriceUpdate::sku,
                PriceUpdate::priceYen,
                (earlier, later) -> later));
```

三引数版の`toMap`は、キーが重複したときの値選択をマージ関数に委譲します。[1] 本題の契約では入力順で後着更新を採用するため、`later`を返します。

例外を握りつぶして旧Mapのまま成功扱いにする、重複を事前に削除する、Mapの`put`ループへ置き換える修正は採用しませんでした。前二者は公開契約を守らず、後者はCollectorで表せる業務規則を別の実装へ不必要に移すためです。

## 回帰保証

### 再発防止テスト

最初に失敗した`duplicateSku_updatesUseTheLaterValueAndPublishTheNewSnapshot`はそのまま残しています。このテストは、`PUBLISHED`という直接結果、後着価格を含む最終Map、公開バージョン`2`を別々に検証します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `duplicateSku_updatesUseTheLaterValueAndPublishTheNewSnapshot` | 同じSKUの後着更新を採用し、新しいスナップショットとバージョンを公開する。 |
| `uniqueSku_updatesRemainPublished` | 重複のない更新を従来どおり公開する。 |
| `noMergeFunctionRejectsDuplicateKeyButLaterValueMergeCollectsTheSnapshot` | 二引数版の拒否と、後着値マージによる集約の差を直接示す。 |

修正後の`mvn --batch-mode clean test`では、3テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout 05c5fde
mvn --batch-mode test -Dtest=PriceSnapshotServiceTest
# expected: <PUBLISHED> but was: <REJECTED_DUPLICATE_SKU>

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、更新の出現順が決まっており、後着値を採用する業務規則にだけ有効です。入力順が信頼できない場合、イベント時刻・バージョン番号・ソースの優先度・競合の監査情報を使って、別のマージ規則を設計する必要があります。

また、このラボは逐次Streamと単一スレッドのインメモリMapを使います。並列Stream、同時リフレッシュ、永続化、複数ノードへの公開では、merge関数だけで原子性や順序保証は得られません。重複をエラーとして報告すべき業務もあるため、常に`later`を返すべきだとは一般化できません。

## References

[1] [Oracle: `Collectors` — `toMap` overloads](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html)
