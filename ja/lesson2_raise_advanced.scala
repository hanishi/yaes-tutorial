// レッスン 2: Raise 応用パターン
//
// レッスン 1 の基礎を踏まえ、リカバリ、エラー型変換、
// エラー蓄積、トレース機能を学びます。

import in.rcard.yaes.*

@main def lesson2(): Unit =
  println("=== レッスン 2: Raise 応用パターン ===")
  println()

  // --- Raise.recover: エラーを処理してフォールバック値を返す ---
  println("--- Raise.recover ---")
  val recovered: Int = Raise.recover {
    Raise.raise("何かが壊れた")
    42
  }(error => -1)
  println(s"リカバリ済み: $recovered") // -1

  val notRecovered: Int = Raise.recover {
    42 // エラーは発生しない
  }(error => -1)
  println(s"エラーなし:   $notRecovered") // 42
  println()

  // --- Raise.withDefault: デフォルト値によるシンプルなリカバリ ---
  println("--- Raise.withDefault ---")
  val defaulted: Int = Raise.withDefault(0) {
    Raise.raise("エラー")
    42
  }
  println(s"withDefault (エラー時): $defaulted") // 0

  val noDefault: Int = Raise.withDefault(0) {
    42
  }
  println(s"withDefault (正常時):   $noDefault") // 42
  println()

  // --- Raise.withError: エラー型の変換 ---
  // 異なるエラー型を持つ関数を合成するときに使う
  println("--- Raise.withError ---")
  sealed trait AppError
  case class ValidationError(msg: String) extends AppError
  case class ParseError(msg: String) extends AppError

  def parseInt(s: String): Int raises String =
    Raise.catching(s.toInt)(ex => s"数値ではありません: $s")

  // parseInt の String エラーを AppError に変換
  val transformed: Either[AppError, Int] = Raise.either {
    Raise.withError[AppError, String, Int](e => ParseError(e)) {
      parseInt("abc")
    }
  }
  println(s"withError: $transformed") // Left(ParseError(数値ではありません: abc))

  val transformedOk: Either[AppError, Int] = Raise.either {
    Raise.withError[AppError, String, Int](e => ParseError(e)) {
      parseInt("42")
    }
  }
  println(s"withError (正常): $transformedOk") // Right(42)
  println()

  // --- Raise.accumulate: 短絡せず全てのエラーを収集する ---
  // 通常 Raise は最初のエラーで停止するが、accumulate は全エラーを集める
  println("--- Raise.accumulate ---")

  case class FormData(name: String, age: Int, email: String)

  // エラー蓄積によるバリデーション — 3つのフィールドが独立に検証される
  val allErrors: Either[List[String], FormData] = Raise.either {
    Raise.accumulate[List, String, FormData] {
      import scala.language.implicitConversions
      val name: Raise.LazyValue[String] = Raise.accumulating {
        Raise.ensure("".nonEmpty)("名前は必須です")
        ""
      }
      val age: Raise.LazyValue[Int] = Raise.accumulating {
        Raise.ensure(-5 >= 0)("年齢は0以上でなければなりません")
        -5
      }
      val email: Raise.LazyValue[String] = Raise.accumulating {
        val e = "メールじゃない"
        Raise.ensure(e.contains("@"))("メールアドレスに @ が必要です")
        e
      }
      // LazyValue を使用する時点で、蓄積されたエラーが raise される
      FormData(name, age, email)
    }
  }
  println(s"全エラー: $allErrors")
  // Left(List(名前は必須です, 年齢は0以上でなければなりません, メールアドレスに @ が必要です))

  // エラーなしの蓄積 — 全フィールドが正常
  val allOk: Either[List[String], FormData] = Raise.either {
    Raise.accumulate[List, String, FormData] {
      import scala.language.implicitConversions
      val name: Raise.LazyValue[String] = Raise.accumulating {
        val n = "太郎"
        Raise.ensure(n.nonEmpty)("名前は必須です")
        n
      }
      val age: Raise.LazyValue[Int] = Raise.accumulating {
        val a = 30
        Raise.ensure(a >= 0)("年齢は0以上でなければなりません")
        a
      }
      val email: Raise.LazyValue[String] = Raise.accumulating {
        val e = "taro@example.com"
        Raise.ensure(e.contains("@"))("メールアドレスに @ が必要です")
        e
      }
      FormData(name, age, email)
    }
  }
  println(s"全て正常: $allOk")
  // Right(FormData(太郎,30,taro@example.com))
  println()

  // --- Raise.traced: エラーにスタックトレース情報を付加する ---
  // デフォルトでは Raise エラーはスタックトレースを持たない（boundary/break を使用）。
  // traced を使うとエラー発生時にスタックトレースが出力される。
  println("--- Raise.traced ---")
  val traced: Either[String, Int] = Raise.either {
    Raise.traced {
      Raise.raise("トレース付きエラー")
      42
    }
  }
  println(s"traced 結果: $traced") // Left(トレース付きエラー)
  // （上にデフォルトの TraceWith ハンドラによるスタックトレースが出力されている）
  println()

  println("=== レッスン 2 終了 ===")
