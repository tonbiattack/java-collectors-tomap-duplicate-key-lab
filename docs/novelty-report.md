# 題材重複調査レポート: `Collectors.toMap`が重複SKUで失敗し、価格スナップショットを公開できない

## 調査対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 難易度プロファイル | 実践・上級 |
| 候補題材 | 同じSKUが複数回現れる更新リストを`Collectors.toMap`で集約し、マージ規則がないため価格スナップショットを公開できない問題 |
| 観測可能な契約 | 旧状態`SKU-01=80`へ、`SKU-01=100`、`SKU-02=200`、`SKU-01=120`を適用したとき、後着値を含むMapを公開すべきだが、バグ状態では`REJECTED_DUPLICATE_SKU`となり旧Map・旧バージョンが残る。 |
| 直接原因 | `Collectors.toMap(keyMapper, valueMapper)`に、重複SKUを後着値へ解決するマージ関数を渡していないこと。 |
| カタログ更新日時 | Repository Catalog（`/home/ubuntu/repository-catalog`）は存在しなかったため更新・検証は実施不能。代替として、ユーザー指定の`tonbiattack/qiita`を取得し、Java・`Collectors.toMap`・重複キー・Map変換・CSV・Streamを本文とパスから検索した。 |
| 検索語 | `Java`, `Collectors.toMap`, `toMap`, `重複キー`, `duplicate key`, `Map変換`, `Stream`, `CSV` |

Repository Catalogが利用できなかったため、このレポートの新規性判断は指定コンテンツリポジトリを対象とします。カタログに未登録のローカル専用教材までは保証できない限界を明示します。

## 近接候補の比較

| 既存コンテンツ | 既存の原因 | 既存の実境界・最終観測 | 今回の差分 | 判定 |
| --- | --- | --- | --- | --- |
| 「Defects4Jで実在したJavaバグをデバッグする：短いCSVレコードをヘッダMapへ変換すると配列範囲外になる」 | ヘッダの列番号で値配列を無条件に参照し、短いレコードで配列範囲外となる。 | Apache Commons CSVの`CSVRecord.toMap()`に短いレコードを渡し、`ArrayIndexOutOfBoundsException`を観測する。 | 既存は外部CSVライブラリのヘッダ数と配列長の境界である。今回はJava標準`Collectors.toMap`の重複キー契約、インメモリ更新リスト、公開結果・最終Map・バージョンを扱う。修正は範囲確認ではなくマージ関数の明示である。 | 重複なし |
| 「同じStreamを2回検索に使って失敗する：Java Streamの一度きり契約を実際にデバッグする」 | 終端操作後のStreamを再利用すること。 | 同じStreamを二度検索し、二度目の操作の失敗を観測する。 | 既存はStreamの消費ライフサイクル、今回はCollectorにおける重複キーの解決規則を扱う。入力、最終観測、最小修正が異なる。 | 重複なし |
| 「Spring Bootで価格検索が見つからない理由：BigDecimalのequalsとcompareToを最小再現から理解する」 | `BigDecimal`の数値的同値とscaleを含む`equals`の差。 | `HashMap<BigDecimal, String>`で同じ金額の検索が見つからない。 | 既存はキーオブジェクト同士の等価性と検索、今回は同じStringキーを持つ複数更新をCollectorでどの値へ集約するかを扱う。実境界、最終観測、修正が異なる。 | 重複なし |
| 先行する「Spring Webhookで`record`の`byte[]`により同一配信を重複処理する」教材 | `byte[]`を持つrecordの等価性が内容同一性を表さない。 | HTTP POSTを二度送り、二度目の応答と処理件数を観測する。 | 先行教材は配列・record・`HashSet`による同一性、今回はStringキー・Stream Collector・マージ規則による競合解決を扱う。 | 重複なし |
| 先行する「`String.split`が末尾の空列を捨て、CSVインポートが任意列を拒否する」教材 | 既定limitが末尾空トークンを破棄する。 | CSV風入力の受理結果、保存行、拒否件数を観測する。 | 先行教材はテキストのトークン化、今回は型付き更新リストのMap集約を扱う。`split`のlimitと`toMap`のmerge functionは異なる標準ライブラリ契約である。 | 重複なし |

## 結論

**作成する。**

Mapへ変換する近接題材は確認できましたが、`Collectors.toMap`の二引数版による**重複キーの拒否**を直接原因として、後着更新を採用する価格スナップショットの公開結果・最終Map・バージョンを扱う記事・教材は見つかりませんでした。

Apache Commons CSVの記事とは、原因が「短いレコードに対する配列境界」か「標準Collectorに渡す重複解決規則の欠落」かで異なります。実境界は外部ライブラリの`CSVRecord.toMap()`か純粋Javaの`PriceSnapshotService#refresh`か、観測契約は例外か公開結果・Map・バージョンか、最小修正は境界チェックかマージ関数かという四軸で区別できます。

## 作成前チェック

- [ ] Repository Catalogを手動更新して検証した。利用可能な`/home/ubuntu/repository-catalog`が存在しなかったため未実施。
- [x] 代替として`tonbiattack/qiita`を取得し、JavaのMap変換・重複キー・Streamに関する語彙的な近接候補を抽出した。
- [x] 高近接のApache Commons CSVのMap変換記事本文を確認し、四軸で比較した。
- [x] 先行するWebhookおよび`String.split`教材とも、直接原因・実境界・観測契約・最小修正を比較した。
- [x] 同じ失敗を名称だけ変えて再実装していない。
- [x] `language-agnostic-debugging-lab`の品質ゲートに沿い、失敗テスト、原因観測、最小修正、回帰テスト、バグ・修正の分離コミットを実装した。
