# YAES チュートリアル

[yaes](https://github.com/rcardin/yaes)（Yet Another Effect System）を学ぶためのハンズオンチュートリアルです。

## エフェクトシステムとは何か

### 「エフェクト」とは

純粋関数は入力から出力を計算するだけですが、実際のプログラムは**副作用**（side effect）を伴います。

- コンソールへの出力
- ファイルの読み書き
- 例外の送出
- 状態の変更
- 乱数の生成
- 並行処理

これらの副作用を**型で追跡し、制御する**仕組みがエフェクトシステムです。

### 従来のアプローチとその課題

Scala のエフェクト管理には、大きく2つの流れがあります。

**1. モナドベース（Cats Effect / ZIO）**

```scala
// ZIO の例
def program: ZIO[Console & Random, IOException, Unit] =
  for
    n    <- Random.nextInt
    _    <- Console.printLine(s"乱数: $n")
  yield ()
```

モナドスタイルでは `for`/`yield`（flatMap チェーン）で処理を組み立てます。型安全でエフェクトの追跡も万全ですが、以下の課題があります。

- **`for`/`yield` の強制** — 通常の `if`/`while`/`try` が使えず、専用のコンビネータが必要
- **学習コスト** — モナド変換子、ファイバー、スコープなど独自の概念が多い
- **エコシステムのロックイン** — ライブラリ全体が特定のエフェクト型に依存する

**2. ダイレクトスタイル（yaes / Kyo など）**

```scala
// yaes の例
def program(using Output, Random): Unit =
  val n = Random.nextInt
  Output.printLn(s"乱数: $n")
```

ダイレクトスタイルでは**普通の Scala コード**としてエフェクトを書きます。`for`/`yield` は不要で、`if`/`while`/`try` がそのまま使えます。

### なぜ yaes なのか

| 特徴 | yaes | Cats Effect | ZIO |
|------|------|-------------|-----|
| スタイル | ダイレクト | モナディック | モナディック |
| エフェクトの宣言 | `using` パラメータ | 型パラメータ `F[_]` | `ZIO[R, E, A]` |
| 制御構文 | `if`/`while`/`try` | 専用コンビネータ | 専用コンビネータ |
| 学習コスト | 低い | 高い | 高い |
| Scala 3 活用度 | コンテキスト関数を全面活用 | Scala 2 由来 | Scala 2 由来 |
| 依存関係 | なし（標準ライブラリのみ） | cats-core | zio |

yaes の最大の特徴は **Scala 3 のコンテキストパラメータ（`using`）をエフェクトの仕組みそのものとして使う**ことです。特別なラッパー型やモナドは不要で、普通の関数がそのままエフェクトフルな関数になります。

### 例外 vs Either vs Raise — 本当の違い

エフェクトシステムに初めて触れる人がよく感じる疑問があります。

> 「例外でいいのでは？」「Either と何が違うの？」

この疑問はもっともです。3つのアプローチを同じ例で比較してみましょう。

**例外 — 書きやすいが、何が起きるか型から分からない**

```scala
def divide(a: Int, b: Int): Int =
  if b == 0 then throw new ArithmeticException("ゼロ除算")
  a / b

def calculate(x: Int): Int =
  divide(divide(x, 2), 3)  // 簡潔だが、エラーの可能性が型に現れない
```

例外はコードが簡潔で、エラーが自動的に呼び出し元に伝播します。しかし `calculate` の型シグネチャ `Int => Int` からは、この関数が失敗する可能性があることが分かりません。コンパイラはエラー処理を強制しません。

**Either — 型安全だが、コードが煩雑になる**

```scala
def divide(a: Int, b: Int): Either[String, Int] =
  if b == 0 then Left("ゼロ除算") else Right(a / b)

def calculate(x: Int): Either[String, Int] =
  divide(x, 2).flatMap(r => divide(r, 3))  // flatMap の連鎖が必要
```

`Either` はエラーの可能性を型で表現します。コンパイラがエラー処理を強制してくれます。しかし代償として、全ての計算を `flatMap` で繋ぐ必要があります。処理が増えるほどネストが深くなり、普通のコードとは見た目がかけ離れていきます。

**Raise — 例外の書きやすさ + Either の型安全性**

```scala
def divide(a: Int, b: Int): Int raises String =
  if b == 0 then Raise.raise("ゼロ除算")
  a / b

def calculate(x: Int): Int raises String =
  divide(divide(x, 2), 3)  // 簡潔、かつエラーの可能性が型に現れる
```

`Raise` は**両方の良いところを兼ね備えています**。

- 例外のように普通の関数呼び出しで書ける — `flatMap` は不要
- エラーは自動的に呼び出し元に伝播する — 明示的な受け渡し不要
- `raises String` が型シグネチャに現れる — コンパイラがエラー処理を強制
- 内部的には `boundary`/`break` を使用 — 例外より高速（スタックトレース生成なし）

つまり、**Raise は「例外の人間工学」と「Either の型安全性」を同時に実現する仕組み**です。「例外でいい」でも「Either でいい」でもなく、両方の利点を取れるところが本質的な違いです。

| | 書きやすさ | エラーの伝播 | 型安全性 | パフォーマンス |
|---|---|---|---|---|
| **例外** | 簡潔 | 自動 | なし | スタックトレースのコスト |
| **Either** | flatMap が必要 | 手動（flatMap） | あり | 高速 |
| **Raise** | 簡潔 | 自動 | あり | 高速（boundary/break） |

## yaes の仕組み

### コアの構造

yaes の核心はシンプルです。

```scala
// エフェクトの「能力」を表すラッパー
class Yaes[+F](val unsafe: F)

// 各エフェクトは型エイリアスとして定義される
type Raise[E] = Yaes[Raise.Unsafe[E]]
type State[S] = Yaes[State.Unsafe[S]]
type Output   = Yaes[Output.Unsafe]
type Random   = Yaes[Random.Unsafe]
// ...
```

`Yaes[F]` は「`F` という能力が使える」というマーカーです。各エフェクトは内部に `Unsafe` トレイトを持ち、実際の処理を定義します。

### エフェクトの宣言と使用

関数が必要とするエフェクトは `using` パラメータで宣言します。

```scala
// この関数は「失敗する可能性」と「乱数」を必要とする
def rollOrFail()(using Random, Raise[String]): Int =
  val n = Random.nextInt
  if n % 2 == 0 then Raise.raise("偶数が出た！")
  n
```

`using` は Scala 3 のコンテキストパラメータです。呼び出し側がエフェクトを提供しない限りコンパイルエラーになるため、**エフェクトの追跡がコンパイル時に保証されます**。

### ハンドラによる実行

エフェクトフルな関数は、ハンドラ（`run`）で囲むことで実行されます。

```scala
// ハンドラをネストして全エフェクトを提供
val result: Either[String, Int] = Random.run {
  Raise.either {
    rollOrFail()
  }
}
```

ハンドラの役割は：
1. **エフェクトの実装を提供する** — `Random.run` は実際の乱数生成器を注入する
2. **エフェクトの結果を変換する** — `Raise.either` はエラーを `Either` に変換する
3. **スコープを区切る** — ハンドラのブロックを抜けるとエフェクトは使えなくなる

### `Raise` の内部動作

`Raise` は yaes の中核エフェクトです。boundary/break メカニズムを使って、例外のオーバーヘッドなしに短絡処理を実現しています。

```
Raise.either { ... Raise.raise("エラー") ... }

1. either が boundary を設定
2. raise が break でそこまでジャンプ
3. either がエラーを Left に包んで返す
```

従来の `throw`/`catch` と違い：
- **型安全** — どんなエラーが起きるか型で分かる
- **スタックトレースなし** — boundary/break はスタックトレースを生成しないため高速
- **合成可能** — 複数のエラー型を自由に組み合わせられる

### エフェクトの合成

複数のエフェクトは `using` パラメータの列挙で自然に合成されます。

```scala
// 3つのエフェクトを必要とする関数
def game()(using Random, State[Int], Output): Unit =
  val coin = Random.nextBoolean
  if coin then State.update[Int](_ + 1)
  Output.printLn(s"結果: ${if coin then "表" else "裏"}")

// ハンドラのネストで全エフェクトを提供
Output.run {
  Random.run {
    State.run(0) {
      game()
    }
  }
}
```

モナドスタイルと異なり、エフェクトの数が増えてもコードの構造は変わりません。`using` が増えるだけです。

## エフェクト一覧

yaes が提供する主なエフェクトです。

### Raise — 型付きエラー

計算を型付きエラーで短絡させます。`Either` のダイレクトスタイル版です。

```scala
def divide(a: Int, b: Int): Int raises String =
  if b == 0 then Raise.raise("ゼロ除算")
  a / b

Raise.either { divide(10, 0) } // Left("ゼロ除算")
```

### State — 状態管理

ミュータブルな状態を制御された形で扱います。

```scala
val (finalState, result) = State.run(0) {
  State.update[Int](_ + 1)
  State.update[Int](_ * 10)
  State.get[Int]  // 10
}
// finalState = 10, result = 10
```

### Output / Input — コンソール I/O

標準入出力をエフェクトとして扱います。テスト時にモック実装に差し替えられます。

```scala
Output.run {
  Output.printLn("こんにちは")
}
```

### Random / Clock — 乱数と時刻

乱数生成と時刻取得をエフェクトとして扱います。

```scala
Random.run {
  val n = Random.nextInt  // 乱数
}
Clock.run {
  val now = Clock.now     // 現在時刻 (Instant)
}
```

### Resource — リソース管理

リソースの取得と解放を保証します。エラーが起きても解放処理が確実に実行されます。

```scala
Resource.run {
  val conn = Resource.acquire(new MyConnection())
  conn.query("SELECT 1")
} // ブロックを抜けると自動的にクローズ
```

### Async — 構造化並行処理（Java 24+）

Java 24 の `StructuredTaskScope` を活用した構造化並行処理です。

```scala
Async.run {
  val (a, b) = Async.par(
    { 重い計算1() },
    { 重い計算2() }
  )
  // 両方が完了するまで待つ。片方が失敗したらもう片方もキャンセル。
}
```

## テスタビリティ — エフェクトシステム最大の実用的メリット

エフェクトシステムを使う最も実用的な理由は**テスト容易性**です。

エフェクトは `using` パラメータで注入されるため、テスト時に本物の実装をモック（偽物）に差し替えられます。これは DI（依存性注入）コンテナなしで実現される、言語レベルの依存性注入です。

### モックの作り方

各エフェクトの `Unsafe` トレイトを実装して `Yaes` で包むだけです。

```scala
// 常に同じ値を返す Random モック
def fixedRandom(value: Int): Random =
  new Yaes(new Random.Unsafe {
    def nextInt(): Int = value
    def nextBoolean(): Boolean = value % 2 == 0
    def nextDouble(): Double = value.toDouble
    def nextLong(): Long = value.toLong
  })

// 出力をバッファに記録する Output モック
def bufferedOutput(): (Output, () => List[String]) =
  val buffer = scala.collection.mutable.ListBuffer.empty[String]
  val output: Output = new Yaes(new Output.Unsafe {
    def print(text: String): Unit = buffer += text
    def printLn(text: String): Unit = buffer += text
    def printErr(text: String): Unit = ()
    def printErrLn(text: String): Unit = ()
  })
  (output, () => buffer.toList)
```

### テストの書き方

```scala
class MyTests extends munit.FunSuite:

  // Raise: either でエラーパスを検証
  test("空のユーザー名を拒否する") {
    val result = Raise.either { validateUsername("") }
    assertEquals(result, Left("ユーザー名は必須です"))
  }

  // Random: モックを注入して決定的にテスト
  test("出目 6 で大当たり") {
    given Random = fixedRandom(5) // (5 % 6).abs + 1 = 6
    val result = Raise.either { luckyMessage() }
    assertEquals(result, Right("大当たり！"))
  }

  // Output: バッファで出力を検証
  test("正しいメッセージを出力する") {
    val (testOutput, getLines) = bufferedOutput()
    given Output = testOutput
    greetUser("花子")
    assert(getLines().head.contains("花子"))
  }

  // State: 最終状態と結果の両方を検証
  test("正しい回数インクリメントする") {
    val (finalState, result) = State.run(0) { incrementN(5) }
    assertEquals(finalState, 5)
  }
```

フレームワーク固有のモック機構（Mockito 等）は不要です。エフェクトの仕組み自体がモックをネイティブにサポートしています。

## 前提条件

- [scala-cli](https://scala-cli.virtuslab.org/) がインストール済みであること
- Java 21 以上（レッスン 1〜7）
- Java 24 以上（レッスン 8：構造化並行処理）

## レッスン一覧

| レッスン | 内容 |
|----------|------|
| lesson1  | **Raise による型付きエラーハンドリング** — `raise`, `run`, `fold`, `either`, `raises` 型, `.value`, `ensure`, `catching` |
| lesson2  | **Raise 応用パターン** — `recover`, `withDefault`, `withError`, `accumulate`, `traced` |
| lesson3  | **State による状態管理** — `run`, `get`, `set`, `update`, `use` |
| lesson4  | **コンソール入出力** — `Output.printLn`, `Input.readLn`（対話型） |
| lesson5  | **乱数と時計** — `Random`, `Clock` |
| lesson6  | **エフェクトの合成** — 複数エフェクトの組み合わせ、コイントスゲーム |
| lesson7  | **リソース管理** — `Resource.install`, `acquire`, LIFO 解放順序 |
| lesson8  | **構造化並行処理** — `Async.fork`, `par`, `race`, `timeout`（要 Java 24+） |
| lesson9  | **テスタビリティ** — モック実装の作り方、エフェクトフルなコードのテスト手法 |

## 実行方法

```bash
# レッスン一覧を表示
scala-cli run . --main-class index

# 各レッスンを実行
scala-cli run . --main-class lesson1
scala-cli run . --main-class lesson2
# ...
scala-cli run . --main-class lesson9

# テストを実行
scala-cli test .
```

## プロジェクト構成

```
project.scala                  # ビルド設定（Scala 3.8.1 + yaes-core + munit）
main.scala                     # レッスン一覧
lesson1_raise.scala            # Raise 基礎
lesson2_raise_advanced.scala   # Raise 応用
lesson3_state.scala            # State
lesson4_io.scala               # Output / Input
lesson5_random_clock.scala     # Random / Clock
lesson6_composing.scala        # エフェクト合成
lesson7_resource.scala         # Resource
lesson8_async.scala            # Async（要 Java 24+）
lesson9_testing.scala          # テスタビリティ
tests/ExampleTests.test.scala  # munit テスト
```

## 参考リンク

- [yaes GitHub](https://github.com/rcardin/yaes) — ソースコードとドキュメント
- [Scala 3 コンテキスト関数](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html) — yaes の基盤となる言語機能
- [boundary/break (SIP-2)](https://docs.scala-lang.org/sips/unroll-default-arguments.html) — `Raise` が内部で使用するメカニズム
- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505) — `Async` が基盤とする Java 24 の機能
