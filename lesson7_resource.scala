// レッスン 7: リソース管理
//
// Resource エフェクトはエラー発生時でもクリーンアップ処理を確実に実行します。
// リソースは LIFO（後入れ先出し）順で解放されます。

import in.rcard.yaes.*
import java.io.Closeable

@main def lesson7(): Unit =
  println("=== レッスン 7: リソース管理 ===")
  println()

  // --- Resource.install: カスタムの取得/解放 ---
  // install(取得)(解放) — 取得を実行し、解放を後で実行するよう登録
  println("--- Resource.install ---")
  Resource.run {
    val handle = Resource.install {
      println("  リソース A を取得")
      "リソースA"
    } { name =>
      println(s"  $name を解放")
    }
    println(s"  $handle を使用中")
  }
  // 出力: 取得 → 使用 → 解放
  println()

  // --- LIFO 解放順序 ---
  // 後から取得したリソースが先に解放される
  println("--- LIFO 解放順序 ---")
  Resource.run {
    val a = Resource.install {
      println("  A を取得")
      "A"
    } { name => println(s"  $name を解放") }

    val b = Resource.install {
      println("  B を取得")
      "B"
    } { name => println(s"  $name を解放") }

    val c = Resource.install {
      println("  C を取得")
      "C"
    } { name => println(s"  $name を解放") }

    println(s"  $a, $b, $c を使用中")
  }
  // 解放順序: C → B → A（取得の逆順）
  println()

  // --- Resource.acquire: Closeable の自動クローズ ---
  println("--- Resource.acquire（Closeable） ---")

  // デモ用のシンプルな Closeable
  class MyConnection(val name: String) extends Closeable:
    println(s"  コネクション '$name' をオープン")
    def query(sql: String): String = s"$name から '$sql' の結果"
    def close(): Unit = println(s"  コネクション '$name' をクローズ")

  Resource.run {
    val conn = Resource.acquire(MyConnection("db1"))
    val result = conn.query("SELECT * FROM users")
    println(s"  $result")
  }
  // ブロック終了時にコネクションが自動的にクローズされる
  println()

  // --- Resource.ensuring: リソースなしのファイナライザ ---
  println("--- Resource.ensuring ---")
  Resource.run {
    Resource.ensuring {
      println("  ファイナライザが実行された！")
    }
    println("  何らかの処理を実行中...")
  }
  println()

  // --- Resource + Raise: エラー発生時でもクリーンアップが実行される ---
  println("--- Resource + Raise の連携 ---")
  val result: Either[String, String] = Raise.either {
    Resource.run {
      val r = Resource.install {
        println("  貴重なリソースを取得")
        "貴重なリソース"
      } { name => println(s"  $name を解放（エラー後でも！）") }

      println(s"  $r を使用中...")
      Raise.raise("何かがおかしくなった！")
      s"$r で成功" // ここには到達しない
    }
  }
  println(s"結果: $result")
  println()

  // --- 複数リソース + エラー ---
  println("--- 複数リソース + エラー ---")
  val result2: Either[String, String] = Raise.either {
    Resource.run {
      val db = Resource.install {
        println("  データベースをオープン")
        "db"
      } { name => println(s"  $name をクローズ") }

      val file = Resource.install {
        println("  ファイルをオープン")
        "file"
      } { name => println(s"  $name をクローズ") }

      val cache = Resource.install {
        println("  キャッシュを初期化")
        "cache"
      } { name => println(s"  $name をクリア") }

      println(s"  $db, $file, $cache で処理中...")
      Raise.raise("予期しないエラー！")
      "完了"
    }
  }
  println(s"結果: $result2")
  // 3つのリソースすべてがエラー発生後も逆順で解放される
  println()

  println("=== レッスン 7 終了 ===")
