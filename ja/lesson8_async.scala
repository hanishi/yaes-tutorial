// レッスン 8: Async による構造化並行処理
//
// 注意: このレッスンは Java 24 以上が必要です（仮想スレッドと StructuredTaskScope）。
// Java 24 未満の場合はレッスン 1〜7 をお楽しみください。
//
// Async エフェクトは構造化並行処理を提供します。フォークされた全ファイバーは
// 親スコープが終了する前に完了（またはキャンセル）されることが保証されます。

import in.rcard.yaes.*
import scala.concurrent.duration.*

@main def lesson8(): Unit =
  println("=== レッスン 8: 構造化並行処理 ===")
  println()

  // --- Async.run + Async.fork: ファイバーの基本 ---
  println("--- Async.fork ---")
  Async.run {
    val fiber1 = Async.fork("worker-1") {
      println(s"  [${Thread.currentThread().getName}] タスク1を実行中...")
      Async.delay(100.millis)
      "結果1"
    }

    val fiber2 = Async.fork("worker-2") {
      println(s"  [${Thread.currentThread().getName}] タスク2を実行中...")
      Async.delay(150.millis)
      "結果2"
    }

    // .value はファイバーの結果をアンラップ（キャンセル時は Cancelled を raise）
    val r1: String = Raise.recover(fiber1.value)(_ => "キャンセル済み")
    val r2: String = Raise.recover(fiber2.value)(_ => "キャンセル済み")
    println(s"  fiber1: $r1, fiber2: $r2")
  }
  println()

  // --- Async.par: 2つのタスクを並列実行し、両方の完了を待つ ---
  println("--- Async.par ---")
  Async.run {
    val (a, b) = Async.par(
      {
        println(s"  [${Thread.currentThread().getName}] A を計算中...")
        Async.delay(100.millis)
        42
      },
      {
        println(s"  [${Thread.currentThread().getName}] B を計算中...")
        Async.delay(200.millis)
        "こんにちは"
      }
    )
    println(s"  par の結果: ($a, $b)") // (42, こんにちは)
  }
  println()

  // --- Async.race: 2つのタスクを実行し、先に終わった方の結果を採用 ---
  println("--- Async.race ---")
  Async.run {
    val winner = Async.race(
      {
        println(s"  [${Thread.currentThread().getName}] 速いタスク...")
        Async.delay(50.millis)
        "速い"
      },
      {
        println(s"  [${Thread.currentThread().getName}] 遅いタスク...")
        Async.delay(500.millis)
        "遅い"
      }
    )
    println(s"  勝者: $winner") // 速い（通常）
  }
  println()

  // --- Async.timeout: タスクが時間内に完了しなければ失敗 ---
  println("--- Async.timeout ---")
  Async.run {
    // 成功: タイムアウト内に完了
    val ok: Either[Async.TimedOut, String] = Raise.either {
      Async.timeout(1.second) {
        Async.delay(100.millis)
        "時間内に完了"
      }
    }
    println(s"  タイムアウト内: $ok") // Right(時間内に完了)

    // タイムアウト: 時間超過
    val timedOut: Either[Async.TimedOut, String] = Raise.either {
      Async.timeout(100.millis) {
        Async.delay(2.seconds)
        "これは完了しない"
      }
    }
    println(s"  タイムアウト超過: $timedOut") // Left(TimedOut)
  }
  println()

  // --- Async と他のエフェクトの組み合わせ ---
  println("--- Async + 他のエフェクト ---")
  Output.run {
    Async.run {
      Output.printLn("並行計算を開始...")

      val (sum, product) = Async.par(
        {
          val result = (1 to 100).sum
          result
        },
        {
          val result = (1 to 10).map(_.toLong).product
          result
        }
      )

      Output.printLn(s"1〜100 の合計 = $sum")
      Output.printLn(s"1〜10 の積 = $product")
    }
  }
  println()

  println("=== レッスン 8 終了 ===")
