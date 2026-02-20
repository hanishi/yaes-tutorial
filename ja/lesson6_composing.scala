// レッスン 6: 複数エフェクトの合成
//
// 実際のプログラムでは複数のエフェクトを同時に使います。
// YAES では必要なエフェクトをコンテキストパラメータとして宣言し、
// ハンドラをネストして実行します。

import in.rcard.yaes.*

@main def lesson6(): Unit =
  println("=== レッスン 6: 複数エフェクトの合成 ===")
  println()

  // --- 複数エフェクトを持つ関数 ---
  println("--- マルチエフェクト関数 ---")

  // この関数は Random, Output, Raise を必要とする
  def randomGreeting(name: String)(using Random, Output, Raise[String]): String =
    if name.isEmpty then Raise.raise("名前が空です")
    val roll = (Random.nextInt % 3).abs
    val greeting = roll match
      case 0 => s"こんにちは、${name}さん！"
      case 1 => s"やあ、${name}さん！"
      case _ => s"ようこそ、${name}さん！"
    Output.printLn(greeting)
    greeting

  // ハンドラをネストして全エフェクトを提供
  val result: Either[String, String] = Output.run {
    Random.run {
      Raise.either {
        randomGreeting("花子")
      }
    }
  }
  println(s"結果: $result")
  println()

  // --- コイントス当てゲーム ---
  // Random（コイン投げ）、State（スコア管理）、
  // Output（メッセージ表示）、Raise（ゲームオーバー）を組み合わせる
  println("--- コイントス当てゲーム ---")

  case class Score(correct: Int, total: Int):
    def hit: Score = Score(correct + 1, total + 1)
    def miss: Score = Score(correct, total + 1)
    override def toString: String = s"$correct/$total"

  def flipCoin()(using Random): Boolean =
    Random.nextBoolean

  def playRound(guess: Boolean)(using Random, State[Score], Output): Unit =
    val coin = flipCoin()
    val coinStr = if coin then "表" else "裏"
    val guessStr = if guess then "表" else "裏"
    if coin == guess then
      Output.printLn(s"  コイン: $coinStr, 予想: $guessStr → 正解！")
      State.update[Score](_.hit)
    else
      Output.printLn(s"  コイン: $coinStr, 予想: $guessStr → 不正解")
      State.update[Score](_.miss)

  def playGame(rounds: Int)(using Random, State[Score], Output): Score =
    for i <- 1 to rounds do
      // デモのため表と裏を交互に予想
      val guess = i % 2 == 0
      playRound(guess)
    State.get[Score]

  // 全エフェクトを合成してゲームを実行
  val (finalScore, gameResult) = Output.run {
    Random.run {
      State.run(Score(0, 0)) {
        Output.printLn("6ラウンドのコイントスゲーム開始...")
        playGame(6)
      }
    }
  }
  println(s"最終スコア: $finalScore")
  println(s"ゲーム結果: $gameResult")
  println()

  // --- 複数エフェクトとエラーハンドリングの組み合わせ ---
  println("--- 多層エラーハンドリング ---")

  sealed trait GameError
  case object TooManyMisses extends GameError
  case class InvalidInput(msg: String) extends GameError

  def strictGame(rounds: Int)(using Random, State[Score], Output, Raise[GameError]): Score =
    for i <- 1 to rounds do
      val guess = flipCoin() // ランダムに予想
      playRound(guess)
      val score = State.get[Score]
      val misses = score.total - score.correct
      if misses >= 3 then
        Output.printLn(s"  ミスが${misses}回！ゲームオーバー。")
        Raise.raise(TooManyMisses)
    State.get[Score]

  val strictResult: Either[GameError, (Score, Score)] = Output.run {
    Random.run {
      Raise.either {
        State.run(Score(0, 0)) {
          Output.printLn("厳格モード（ミス3回で終了）...")
          strictGame(10)
        }
      }
    }
  }
  strictResult match
    case Right((finalState, score)) =>
      println(s"完走！ スコア: $score")
    case Left(TooManyMisses) =>
      println(s"ゲームオーバー: ミスが多すぎます！")
    case Left(InvalidInput(msg)) =>
      println(s"不正な入力: $msg")
  println()

  println("=== レッスン 6 終了 ===")
