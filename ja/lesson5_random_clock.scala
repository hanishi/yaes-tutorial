// レッスン 5: Random と Clock エフェクト
//
// Random は制御された乱数生成、Clock は時刻アクセスを提供します。
// どちらもエフェクトなので、テスト時に実装を差し替えられます。

import in.rcard.yaes.*
import java.time.Instant

@main def lesson5(): Unit =
  println("=== レッスン 5: Random と Clock エフェクト ===")
  println()

  // --- Random: 乱数生成 ---
  println("--- Random ---")
  Random.run {
    val a = Random.nextInt
    val b = Random.nextInt
    println(s"ランダムな整数2つ: $a, $b")

    val bool = Random.nextBoolean
    println(s"ランダムな真偽値: $bool")

    val dbl = Random.nextDouble
    println(s"ランダムな小数: $dbl")

    val lng = Random.nextLong
    println(s"ランダムな長整数: $lng")
  }
  println()

  // --- 関数内での Random の利用 ---
  println("--- 関数内での Random ---")
  def rollDice()(using Random): Int =
    // nextInt は任意の整数を返すので、剰余演算で 1〜6 にする
    val raw = Random.nextInt
    (raw % 6).abs + 1

  Random.run {
    val rolls = (1 to 5).map(_ => rollDice())
    println(s"サイコロ5回: ${rolls.mkString(", ")}")
  }
  println()

  // --- ランダムなリストの生成 ---
  println("--- ランダムリスト ---")
  def randomList(size: Int)(using Random): List[Int] =
    (1 to size).map(_ => (Random.nextInt % 100).abs).toList

  Random.run {
    val lst = randomList(8)
    println(s"0〜99 の乱数8個: $lst")
  }
  println()

  // --- Clock.now: 現在時刻の取得 ---
  println("--- Clock ---")
  Clock.run {
    val now: Instant = Clock.now
    println(s"現在時刻: $now")

    val monotonic = Clock.nowMonotonic
    println(s"モノトニック時間: $monotonic")
  }
  println()

  // --- Clock で経過時間を測定 ---
  println("--- 経過時間の測定 ---")
  Clock.run {
    val start = Clock.now
    // 何らかの処理をシミュレート
    var sum = 0L
    for i <- 1 to 1_000_000 do sum += i
    val end = Clock.now

    val elapsed = java.time.Duration.between(start, end)
    println(s"1 から 1,000,000 の合計: $sum")
    println(s"経過時間: ${elapsed.toMillis}ミリ秒")
  }
  println()

  // --- Random + Clock の組み合わせ ---
  println("--- Random + Clock の組み合わせ ---")
  Random.run {
    Clock.run {
      val timestamp = Clock.now
      val value = Random.nextInt
      println(s"時刻 $timestamp のランダム値 = $value")
    }
  }
  println()

  println("=== レッスン 5 終了 ===")
