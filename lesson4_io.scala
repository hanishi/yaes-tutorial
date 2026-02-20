// Lesson 4: Console I/O with Output and Input
//
// Output and Input are effects for console interaction.
// They make I/O explicit in your function signatures and testable.

import in.rcard.yaes.*
import java.io.IOException

@main def lesson4(): Unit =
  println("=== Lesson 4: Console I/O with Output and Input ===")
  println()

  // --- Output.printLn: printing to stdout ---
  println("--- Output.printLn ---")
  Output.run {
    Output.printLn("Hello from the Output effect!")
    Output.printLn("Each line is printed via the effect, not raw println.")
  }
  println()

  // --- Output.print: printing without newline ---
  println("--- Output.print ---")
  Output.run {
    Output.print("Hello ")
    Output.print("world")
    Output.printLn("!") // now with newline
  }
  println()

  // --- Output.printErrLn: printing to stderr ---
  println("--- Output.printErrLn ---")
  Output.run {
    Output.printLn("This goes to stdout")
    Output.printErrLn("This goes to stderr")
  }
  println()

  // --- Composing Output in functions ---
  println("--- Functions requiring Output ---")
  def greet(name: String)(using Output): Unit =
    Output.printLn(s"Hello, $name!")
    Output.printLn("Welcome to the YAES tutorial.")

  Output.run {
    greet("Alice")
  }
  println()

  // --- Input.readLn: reading from stdin ---
  // Input.readLn requires both Input and Raise[IOException]
  println("--- Input.readLn (interactive) ---")
  println("(Type your name and press Enter)")

  val result: Either[IOException, String] = Output.run {
    Input.run {
      Raise.either[IOException, String] {
        Output.print("What is your name? ")
        val name = Input.readLn()
        Output.printLn(s"Nice to meet you, $name!")
        name
      }
    }
  }
  result match
    case Right(name) => println(s"(Captured name: $name)")
    case Left(err)   => println(s"(I/O error: ${err.getMessage})")
  println()

  // --- Composing Output + Input in a function ---
  println("--- Echo loop (type 'quit' to exit) ---")
  def echoLoop()(using Output, Input, Raise[IOException]): Unit =
    Output.print("> ")
    val line = Input.readLn()
    if line.trim.toLowerCase != "quit" then
      Output.printLn(s"You said: $line")
      echoLoop()
    else
      Output.printLn("Goodbye!")

  Output.run {
    Input.run {
      Raise.recover {
        echoLoop()
      }(err => println(s"I/O error: ${err.getMessage}"))
    }
  }
  println()

  println("=== End of Lesson 4 ===")
