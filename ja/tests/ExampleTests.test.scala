// YAES テスト例
//
// エフェクトをモック実装に差し替えて、ビジネスロジックをテストする方法を示します。
// 実行: scala-cli test .

import in.rcard.yaes.*

// ============================================================
// テスト対象のビジネスロジック
// ============================================================

/** ユーザー名バリデーション */
def validateUsername(name: String)(using Raise[String]): String =
  Raise.ensure(name.nonEmpty)("ユーザー名は必須です")
  Raise.ensure(name.length >= 3)("ユーザー名は3文字以上必要です")
  Raise.ensure(name.length <= 20)("ユーザー名は20文字以内にしてください")
  Raise.ensure(name.forall(_.isLetterOrDigit))("ユーザー名は英数字のみ使用できます")
  name

/** サイコロを振る */
def rollDice()(using Random): Int =
  (Random.nextInt % 6).abs + 1

/** 挨拶を出力する */
def greetUser(name: String)(using Output): Unit =
  Output.printLn(s"こんにちは、${name}さん！")
  Output.printLn("今日も良い一日を。")

/** カウンタを指定回数インクリメントする */
def incrementN(n: Int)(using State[Int]): Int =
  for _ <- 1 to n do State.update[Int](_ + 1)
  State.get[Int]

/** 乱数に基づいてメッセージを生成する */
def luckyMessage()(using Random, Raise[String]): String =
  val roll = rollDice()
  if roll == 6 then "大当たり！"
  else if roll >= 4 then s"当たり（出目: $roll）"
  else Raise.raise(s"はずれ（出目: $roll）")

// ============================================================
// テストヘルパー: モック実装
// ============================================================

/** 固定値を返す Random モック */
def fixedRandom(value: Int): Random =
  new Yaes(new Random.Unsafe {
    def nextInt(): Int = value
    def nextBoolean(): Boolean = value % 2 == 0
    def nextDouble(): Double = value.toDouble
    def nextLong(): Long = value.toLong
  })

/** 出力をバッファに記録する Output モック */
def bufferedOutput(): (Output, () => List[String]) =
  val buffer = scala.collection.mutable.ListBuffer.empty[String]
  val output: Output = new Yaes(new Output.Unsafe {
    def print(text: String): Unit = buffer += text
    def printLn(text: String): Unit = buffer += text
    def printErr(text: String): Unit = ()
    def printErrLn(text: String): Unit = ()
  })
  (output, () => buffer.toList)

// ============================================================
// テスト
// ============================================================

class RaiseTests extends munit.FunSuite:

  test("有効なユーザー名を受け入れる") {
    val result = Raise.either { validateUsername("taro123") }
    assertEquals(result, Right("taro123"))
  }

  test("空のユーザー名を拒否する") {
    val result = Raise.either { validateUsername("") }
    assertEquals(result, Left("ユーザー名は必須です"))
  }

  test("短すぎるユーザー名を拒否する") {
    val result = Raise.either { validateUsername("ab") }
    assertEquals(result, Left("ユーザー名は3文字以上必要です"))
  }

  test("不正な文字を含むユーザー名を拒否する") {
    val result = Raise.either { validateUsername("user@name") }
    assertEquals(result, Left("ユーザー名は英数字のみ使用できます"))
  }

class RandomTests extends munit.FunSuite:

  test("固定値の Random でサイコロの出目を検証する") {
    given Random = fixedRandom(3) // (3 % 6).abs + 1 = 4
    val result = rollDice()
    assertEquals(result, 4)
  }

  test("出目が常に 1〜6 の範囲にある") {
    // さまざまな固定値でテスト
    for value <- List(0, 1, 5, 11, -7, 100, -100) do
      given Random = fixedRandom(value)
      val result = rollDice()
      assert(result >= 1 && result <= 6, s"nextInt=$value → 出目=$result が範囲外")
  }

class OutputTests extends munit.FunSuite:

  test("greetUser が正しいメッセージを出力する") {
    val (testOutput, getLines) = bufferedOutput()
    given Output = testOutput
    greetUser("花子")

    val lines = getLines()
    assertEquals(lines.length, 2)
    assert(lines(0).contains("花子"))
    assertEquals(lines(1), "今日も良い一日を。")
  }

class StateTests extends munit.FunSuite:

  test("incrementN が正しい回数インクリメントする") {
    val (finalState, result) = State.run(0) { incrementN(5) }
    assertEquals(finalState, 5)
    assertEquals(result, 5)
  }

  test("初期値から正しくインクリメントする") {
    val (finalState, result) = State.run(10) { incrementN(3) }
    assertEquals(finalState, 13)
    assertEquals(result, 13)
  }

class IntegrationTests extends munit.FunSuite:

  test("出目 6 で大当たりメッセージを返す") {
    given Random = fixedRandom(5) // (5 % 6).abs + 1 = 6
    val result = Raise.either { luckyMessage() }
    assertEquals(result, Right("大当たり！"))
  }

  test("出目 4 で当たりメッセージを返す") {
    given Random = fixedRandom(3) // (3 % 6).abs + 1 = 4
    val result = Raise.either { luckyMessage() }
    assertEquals(result, Right("当たり（出目: 4）"))
  }

  test("出目 3 ではずれエラーを返す") {
    given Random = fixedRandom(2) // (2 % 6).abs + 1 = 3
    val result = Raise.either { luckyMessage() }
    assertEquals(result, Left("はずれ（出目: 3）"))
  }

  test("複数エフェクトを組み合わせたテスト") {
    val (testOutput, getLines) = bufferedOutput()

    def program()(using Random, Output, Raise[String]): String =
      val roll = rollDice()
      Output.printLn(s"出目: $roll")
      if roll >= 4 then "成功"
      else Raise.raise("失敗")

    given Random = fixedRandom(5) // 出目 = 6
    given Output = testOutput

    val result = Raise.either { program() }
    val lines = getLines()

    assertEquals(result, Right("成功"))
    assertEquals(lines, List("出目: 6"))
  }
