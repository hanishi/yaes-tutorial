// レッスン 4: Output と Input によるコンソール入出力
//
// Output と Input はコンソール操作のためのエフェクトです。
// I/O を関数シグネチャで明示的にし、テスト可能にします。

import in.rcard.yaes.*
import java.io.IOException

@main def lesson4(): Unit =
  println("=== レッスン 4: コンソール入出力 ===")
  println()

  // --- Output.printLn: 標準出力への出力 ---
  println("--- Output.printLn ---")
  Output.run {
    Output.printLn("Output エフェクトからこんにちは！")
    Output.printLn("各行は生の println ではなくエフェクト経由で出力されます。")
  }
  println()

  // --- Output.print: 改行なしの出力 ---
  println("--- Output.print ---")
  Output.run {
    Output.print("こんにちは ")
    Output.print("世界")
    Output.printLn("！") // ここで改行
  }
  println()

  // --- Output.printErrLn: 標準エラー出力への出力 ---
  println("--- Output.printErrLn ---")
  Output.run {
    Output.printLn("これは標準出力に出ます")
    Output.printErrLn("これは標準エラー出力に出ます")
  }
  println()

  // --- 関数内での Output の利用 ---
  println("--- Output を使う関数 ---")
  def greet(name: String)(using Output): Unit =
    Output.printLn(s"こんにちは、${name}さん！")
    Output.printLn("YAES チュートリアルへようこそ。")

  Output.run {
    greet("太郎")
  }
  println()

  // --- Input.readLn: 標準入力からの読み取り ---
  // Input.readLn は Input と Raise[IOException] の両方が必要
  // 両方のエフェクトを合成して使う
  println("--- Input.readLn（対話型） ---")
  println("（名前を入力して Enter を押してください）")

  val result: Either[IOException, String] = Output.run {
    Input.run {
      Raise.either[IOException, String] {
        Output.print("お名前は？ ")
        val name = Input.readLn()
        Output.printLn(s"はじめまして、${name}さん！")
        name
      }
    }
  }
  result match
    case Right(name) => println(s"（入力された名前: $name）")
    case Left(err)   => println(s"（I/O エラー: ${err.getMessage}）")
  println()

  // --- Output + Input を使った関数の合成 ---
  println("--- エコーループ（'quit' で終了） ---")
  def echoLoop()(using Output, Input, Raise[IOException]): Unit =
    Output.print("> ")
    val line = Input.readLn()
    if line.trim.toLowerCase != "quit" then
      Output.printLn(s"入力内容: $line")
      echoLoop()
    else
      Output.printLn("さようなら！")

  Output.run {
    Input.run {
      Raise.recover {
        echoLoop()
      }(err => println(s"I/O エラー: ${err.getMessage}"))
    }
  }
  println()

  println("=== レッスン 4 終了 ===")
