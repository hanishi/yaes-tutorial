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

## 実行方法

```bash
# レッスン一覧を表示
scala-cli run . --main-class index

# 各レッスンを実行
scala-cli run . --main-class lesson1
scala-cli run . --main-class lesson2
# ...
scala-cli run . --main-class lesson8
```

## プロジェクト構成

```
project.scala                # ビルド設定（Scala 3.8.1 + yaes-core）
main.scala                   # レッスン一覧
lesson1_raise.scala          # Raise 基礎
lesson2_raise_advanced.scala # Raise 応用
lesson3_state.scala          # State
lesson4_io.scala             # Output / Input
lesson5_random_clock.scala   # Random / Clock
lesson6_composing.scala      # エフェクト合成
lesson7_resource.scala       # Resource
lesson8_async.scala          # Async（要 Java 24+）
```

## 参考リンク

- [yaes GitHub](https://github.com/rcardin/yaes) — ソースコードとドキュメント
- [Scala 3 コンテキスト関数](https://docs.scala-lang.org/scala3/reference/contextual/context-functions.html) — yaes の基盤となる言語機能
- [boundary/break (SIP-2)](https://docs.scala-lang.org/sips/unroll-default-arguments.html) — `Raise` が内部で使用するメカニズム
- [JEP 505: Structured Concurrency](https://openjdk.org/jeps/505) — `Async` が基盤とする Java 24 の機能
