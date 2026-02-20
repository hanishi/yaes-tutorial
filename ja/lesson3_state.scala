// レッスン 3: State による状態管理
//
// State エフェクトはミュータブルな状態を制御された形で扱います。
// State.run(初期値) { ... } は (最終状態, 結果) のタプルを返します。

import in.rcard.yaes.*

@main def lesson3(): Unit =
  println("=== レッスン 3: State による状態管理 ===")
  println()

  // --- 基本: State.get と State.set ---
  println("--- State.get / State.set ---")
  val (s1, r1) = State.run(0) {
    val before = State.get[Int]      // 現在の状態を読む
    State.set(10)                    // 状態を上書き
    val after = State.get[Int]       // 更新後の状態を読む
    s"変更前 $before → 変更後 $after"
  }
  println(s"最終状態: $s1, 結果: $r1") // 最終状態: 10, 結果: 変更前 0 → 変更後 10
  println()

  // --- State.update: 関数で状態を変換 ---
  println("--- State.update ---")
  val (s2, r2) = State.run(1) {
    State.update[Int](_ + 1)   // 状態 = 2
    State.update[Int](_ * 10)  // 状態 = 20
    State.update[Int](_ + 3)   // 状態 = 23
    State.get[Int]
  }
  println(s"更新後 — 状態: $s2, 結果: $r2") // 23, 23
  println()

  // --- State.use: 状態を変更せずに読み取る ---
  // State.use(f) は現在の状態に f を適用するが、状態自体は変更しない
  println("--- State.use ---")
  val (s3, r3) = State.run(List("こんにちは", "世界", "yaes")) {
    val count = State.use[List[String], Int](_.length)
    val upper = State.use[List[String], List[String]](_.map(_.toUpperCase))
    val csv   = State.use[List[String], String](_.mkString(", "))
    (count, upper, csv)
  }
  println(s"状態は不変: $s3")
  println(s"派生値:     $r3")
  println()

  // --- カウンタの例: 再帰的な状態付き計算 ---
  println("--- 再帰カウンタ ---")
  def countUp()(using State[Int]): String =
    val current = State.get[Int]
    if current >= 5 then s"$current に到達！"
    else
      State.update[Int](_ + 1)
      countUp()

  val (counterState, counterResult) = State.run(0) { countUp() }
  println(s"$counterResult（最終状態: $counterState）") // 5 に到達！（最終状態: 5）
  println()

  // --- キーバリューストアの例 ---
  println("--- キーバリューストア ---")
  type Store = Map[String, Int]

  val (store, total) = State.run(Map.empty[String, Int]) {
    State.update[Store](_ + ("りんご" -> 3))
    State.update[Store](_ + ("バナナ" -> 5))
    State.update[Store](_ + ("みかん" -> 2))

    val sum = State.use[Store, Int](_.values.sum)

    // 商品を削除
    State.update[Store](_ - "バナナ")

    sum
  }
  println(s"削除前の合計: $total")   // 10
  println(s"最終ストア: $store")     // Map(りんご -> 3, みかん -> 2)
  println()

  println("=== レッスン 3 終了 ===")
