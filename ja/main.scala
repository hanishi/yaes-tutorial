//> using scala 3.8.1
//> using dep in.rcard.yaes::yaes-core:0.14.0
//> using test.dep org.scalameta::munit::1.2.2

// YAES チュートリアル — レッスン一覧
//
// 各レッスンの実行方法:
//   scala-cli run . --main-class lesson1
//   scala-cli run . --main-class lesson2
//   ...
//
// レッスン:
//   lesson1 — Raise による型付きエラーハンドリング
//   lesson2 — Raise 応用パターン
//   lesson3 — State による状態管理
//   lesson4 — Output / Input によるコンソール入出力
//   lesson5 — Random と Clock エフェクト
//   lesson6 — 複数エフェクトの合成
//   lesson7 — リソース管理
//   lesson8 — 構造化並行処理（要 Java 24+）
//   lesson9 — テスタビリティ

@main def index(): Unit =
  println("YAES チュートリアル")
  println("==================")
  println()
  println("各レッスンの実行方法:")
  println("  scala-cli run . --main-class lesson1   — Raise による型付きエラーハンドリング")
  println("  scala-cli run . --main-class lesson2   — Raise 応用パターン")
  println("  scala-cli run . --main-class lesson3   — State による状態管理")
  println("  scala-cli run . --main-class lesson4   — コンソール入出力（対話型）")
  println("  scala-cli run . --main-class lesson5   — 乱数と時計")
  println("  scala-cli run . --main-class lesson6   — エフェクトの合成")
  println("  scala-cli run . --main-class lesson7   — リソース管理")
  println("  scala-cli run . --main-class lesson8   — 構造化並行処理（要 Java 24+）")
  println("  scala-cli run . --main-class lesson9   — テスタビリティ")
  println()
  println("テストの実行:")
  println("  scala-cli test .")
