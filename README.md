# test-solr-plugin-by-ChatGPT

ChatGPT が意外と Lucene / Solr に詳しいので、Solr プラグインを書かせてみたものです。


## 非機能要件
- Gradle
- Solr 9.4.0

## 機能要件

### `ConditionExpressionQParserPlugin`
- DocValues に条件式をつける
    - 条件の ID を葉
    - `{AND, OR, NOT}` を内部節点
- クエリで
    - 真の条件の ID のリストを渡すと、
    - マッチする条件式のついたドキュメントのみを返す

### `GreedyTermQParserPlugin`
複数のタームの中から、指定の document frequency 以上の最初のものを選び、`TermQuery` とする
- 条件を満たすものが存在しなければ、たんに最後のものを選ぶ
- ここでいう document frequency は Lucene / Solr の定義による
    - 例えば削除ずみドキュメントを含む
    - 複数シャード環境では、それぞれ異なるタームを選ぶことがある
