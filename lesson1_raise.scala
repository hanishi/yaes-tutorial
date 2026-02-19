// レッスン 1: Raise による型付きエラーハンドリング
//
// Raise エフェクトは型付きエラーで計算を短絡（ショートサーキット）させます。
// Either に似ていますが、flatMap チェーン不要のダイレクトスタイルで書けます。

import in.rcard.yaes.*
import in.rcard.yaes.Raise.*
import scala.util.Try

@main def lesson1(): Unit =
  println("=== レッスン 1: Raise による型付きエラーハンドリング ===")
  println()

  // --- Raise.run: A | E（ユニオン型）を返す ---
  println("--- Raise.run ---")
  val success: String | Int = Raise.run[String, Int] {
    42
  }
  println(s"成功: $success") // 42

  val failure: String | Int = Raise.run[String, Int] {
    Raise.raise("何かがおかしい")
    42 // ここには到達しない
  }
  println(s"失敗: $failure") // 何かがおかしい
  println()

  // --- Raise.either: Either[E, A] を返す ---
  println("--- Raise.either ---")
  val right: Either[String, Int] = Raise.either {
    42
  }
  println(s"Right: $right") // Right(42)

  val left: Either[String, Int] = Raise.either {
    Raise.raise("エラー発生")
    42
  }
  println(s"Left: $left") // Left(エラー発生)
  println()

  // --- Raise.fold: 最も汎用的なハンドラ ---
  // fold(block)(onError)(onSuccess) — 両方のパスを処理する
  println("--- Raise.fold ---")
  val folded: String = Raise.fold {
    val x = 10
    val y = 0
    if y == 0 then Raise.raise("ゼロ除算エラー")
    x / y
  }(error => s"エラー捕捉: $error")(result => s"計算結果: $result")
  println(s"fold 結果: $folded") // エラー捕捉: ゼロ除算エラー
  println()

  // --- raises 中置型 ---
  // Raise[E] ?=> A の代わりに A raises E と書ける
  println("--- 中置型 `raises` ---")
  def divide(a: Int, b: Int): Int raises String =
    if b == 0 then Raise.raise("ゼロで割ることはできません")
    else a / b

  println(s"10 / 2 = ${Raise.either { divide(10, 2) }}") // Right(5)
  println(s"10 / 0 = ${Raise.either { divide(10, 0) }}") // Left(ゼロで割ることはできません)
  println()

  // --- Option / Either / Try を .value でリフト ---
  // .value は成功値をアンラップし、失敗時はエラーを raise する
  println("--- .value によるリフト ---")

  // Option: None のとき None.type を raise する
  val fromSome: Either[None.type, Int] = Raise.either {
    val opt: Option[Int] = Some(42)
    opt.value
  }
  println(s"Some(42).value: $fromSome") // Right(42)

  val fromNone: Either[None.type, Int] = Raise.either {
    val opt: Option[Int] = None
    opt.value
  }
  println(s"None.value:     $fromNone") // Left(None)

  // Either: Left の値を raise する
  val fromRight: Either[String, Int] = Raise.either {
    val e: Either[String, Int] = Right(99)
    e.value
  }
  println(s"Right(99).value: $fromRight") // Right(99)

  val fromLeft: Either[String, Int] = Raise.either {
    val e: Either[String, Int] = Left("不正な値")
    e.value
  }
  println(s"Left(不正な値).value: $fromLeft") // Left(不正な値)

  // Try: Failure のとき Throwable を raise する
  val fromTryOk: Either[Throwable, Int] = Raise.either {
    val t: Try[Int] = Try(42)
    t.value
  }
  println(s"Try(42).value:        $fromTryOk") // Right(42)

  val fromTryFail: Either[Throwable, Int] = Raise.either {
    val t: Try[Int] = Try("数字じゃない".toInt)
    t.value
  }
  println(s"Try(パース失敗).value: $fromTryFail") // Left(NumberFormatException)
  println()

  // --- Raise.ensure: バリデーション ---
  println("--- Raise.ensure ---")
  def validateAge(age: Int): Int raises String =
    Raise.ensure(age >= 0)("年齢は負の値にできません")
    Raise.ensure(age <= 150)("年齢が現実的ではありません")
    age

  println(s"年齢 25:  ${Raise.either { validateAge(25) }}")  // Right(25)
  println(s"年齢 -1:  ${Raise.either { validateAge(-1) }}")  // Left(年齢は負の値にできません)
  println(s"年齢 200: ${Raise.either { validateAge(200) }}") // Left(年齢が現実的ではありません)
  println()

  // --- Raise.catching: 例外を型付きエラーに変換 ---
  println("--- Raise.catching ---")
  val caught: Either[String, Int] = Raise.either {
    Raise.catching {
      "数字じゃない".toInt
    }(ex => s"パース失敗: ${ex.getMessage}")
  }
  println(s"catching 結果: $caught") // Left(パース失敗: ...)
  println()

  println("=== レッスン 1 終了 ===")
