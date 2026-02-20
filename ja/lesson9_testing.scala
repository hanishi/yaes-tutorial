// レッスン 9: エフェクトとテスタビリティ
//
// エフェクトシステムの最大の実用的メリットは「テスト容易性」です。
// エフェクトは using パラメータで注入されるため、テスト時に
// 本物の実装をモック（偽物）に差し替えることができます。
//
// このレッスンでは、エフェクトフルなコードをテスト可能にする方法を学びます。
// 実際の munit テストは tests/ ディレクトリにあります。
//   scala-cli test .

import in.rcard.yaes.*

@main def lesson9(): Unit =
  println("=== レッスン 9: エフェクトとテスタビリティ ===")
  println()

  // ============================================================
  // まず、テスト対象のビジネスロジックを定義する
  // ============================================================

  // --- 例1: バリデーション関数 ---
  // Raise だけを使う関数はテストが簡単
  def validateUsername(name: String)(using Raise[String]): String =
    Raise.ensure(name.nonEmpty)("ユーザー名は必須です")
    Raise.ensure(name.length >= 3)("ユーザー名は3文字以上必要です")
    Raise.ensure(name.length <= 20)("ユーザー名は20文字以内にしてください")
    Raise.ensure(name.forall(_.isLetterOrDigit))("ユーザー名は英数字のみ使用できます")
    name

  println("--- 例1: Raise のテスト ---")
  println("Raise.either でエラーパスと成功パスを検証できる:")

  val valid = Raise.either { validateUsername("taro123") }
  val empty = Raise.either { validateUsername("") }
  val short = Raise.either { validateUsername("ab") }
  val bad   = Raise.either { validateUsername("user@name") }

  println(s"  'taro123'   → $valid")
  println(s"  ''          → $empty")
  println(s"  'ab'        → $short")
  println(s"  'user@name' → $bad")
  // テストでは assert(valid == Right("taro123")) のように書く
  println()

  // --- 例2: Random のモック ---
  // Random を使う関数は、テスト時に固定値を返すモックを注入する
  println("--- 例2: Random のモック ---")

  def rollDice()(using Random): Int =
    (Random.nextInt % 6).abs + 1

  def gamble()(using Random, Raise[String]): String =
    val roll = rollDice()
    if roll >= 4 then s"勝ち！（出目: $roll）"
    else Raise.raise(s"負け...（出目: $roll）")

  // テスト用: 常に同じ値を返す Random を作る
  def fixedRandom(value: Int): Random =
    new Yaes(new Random.Unsafe {
      def nextInt(): Int = value
      def nextBoolean(): Boolean = value % 2 == 0
      def nextDouble(): Double = value.toDouble
      def nextLong(): Long = value.toLong
    })

  // 勝ちケースをテスト（出目が 4 になるよう nextInt = 3 を注入）
  val winResult = Raise.either {
    given Random = fixedRandom(3) // (3 % 6).abs + 1 = 4
    gamble()
  }
  println(s"  固定値 3 → $winResult") // Right(勝ち！（出目: 4）)

  // 負けケースをテスト（出目が 1 になるよう nextInt = 0 を注入）
  val loseResult = Raise.either {
    given Random = fixedRandom(0) // (0 % 6).abs + 1 = 1
    gamble()
  }
  println(s"  固定値 0 → $loseResult") // Left(負け...（出目: 1）)
  println()

  // --- 例3: Output のモック ---
  // Output を使う関数は、テスト時に出力をバッファに溜める
  println("--- 例3: Output のモック ---")

  def greetUser(name: String)(using Output): Unit =
    Output.printLn(s"こんにちは、${name}さん！")
    Output.printLn("今日も良い一日を。")

  // テスト用: 出力をバッファに記録する Output を作る
  def bufferedOutput(): (Output, () => List[String]) =
    val buffer = scala.collection.mutable.ListBuffer.empty[String]
    val output: Output = new Yaes(new Output.Unsafe {
      def print(text: String): Unit = buffer += text
      def printLn(text: String): Unit = buffer += text
      def printErr(text: String): Unit = ()
      def printErrLn(text: String): Unit = ()
    })
    (output, () => buffer.toList)

  val (testOutput, getLines) = bufferedOutput()
  given Output = testOutput
  greetUser("花子")

  val captured = getLines()
  println(s"  キャプチャした出力: $captured")
  // テストでは assert(captured.contains("こんにちは、花子さん！")) のように書く
  println()

  // --- 例4: State のテスト ---
  // State.run は (最終状態, 結果) を返すので、両方をアサートできる
  println("--- 例4: State のテスト ---")

  def addItems(items: List[String])(using State[List[String]]): Int =
    items.foreach { item =>
      State.update[List[String]](item :: _)
    }
    State.use[List[String], Int](_.length)

  val (finalState, count) = State.run(List.empty[String]) {
    addItems(List("りんご", "バナナ", "みかん"))
  }
  println(s"  最終状態: $finalState")
  println(s"  アイテム数: $count")
  // テストでは assert(count == 3) && assert(finalState.contains("りんご")) のように書く
  println()

  // --- 例5: 複数エフェクトの統合テスト ---
  println("--- 例5: 複数エフェクトの統合テスト ---")

  case class Player(name: String, score: Int)

  def playRound()(using Random, State[Player], Output, Raise[String]): Unit =
    val player = State.get[Player]
    val roll = rollDice()
    Output.printLn(s"${player.name} の出目: $roll")
    if roll >= 4 then
      State.update[Player](p => p.copy(score = p.score + roll))
    else if player.score + roll < 0 then
      Raise.raise("スコアが不足しています")

  // 全エフェクトをモック/制御してテスト
  val (testOut2, getLines2) = bufferedOutput()
  val testResult: Either[String, (Player, Unit)] = Raise.either {
    given Output = testOut2
    given Random = fixedRandom(5) // 出目 = (5 % 6).abs + 1 = 6
    State.run(Player("テスト太郎", 0)) {
      playRound()
    }
  }

  testResult match
    case Right((player, _)) =>
      println(s"  プレイヤー: $player")
      println(s"  出力: ${getLines2()}")
    case Left(err) =>
      println(s"  エラー: $err")
  // テストでは assert(player.score == 6) のように書く
  println()

  // ============================================================
  // ポイントまとめ
  // ============================================================
  println("--- まとめ ---")
  println("エフェクトシステムがテストを容易にする理由:")
  println()
  println("  1. エフェクトの差し替え")
  println("     using パラメータなので、テスト時にモック実装を注入できる")
  println("     例: Random → 固定値、Output → バッファ、Clock → 固定時刻")
  println()
  println("  2. 純粋なビジネスロジック")
  println("     副作用がエフェクトとして分離されるので、")
  println("     ロジック自体は入力→出力の純粋な関数としてテストできる")
  println()
  println("  3. エラーパスの検証")
  println("     Raise.either で簡単にエラーケースを検証できる")
  println("     例外の catch 不要、型安全にエラーを検査できる")
  println()
  println("  実際の munit テストは tests/ ディレクトリを参照:")
  println("    scala-cli test .")
  println()

  println("=== レッスン 9 終了 ===")
