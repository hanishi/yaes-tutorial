// Experiment: YAES + Pekko Typed Integration
//
// Demonstrates how to run a YAES effectful workflow inside a Pekko Typed actor.
//
// - HttpClient & Logger are custom YAES effects following the same
//   type-alias + Unsafe-trait pattern used by the built-in effects.
// - Dispatcher is a custom YAES effect that interprets (HttpClient & Logger)
//   programs into Futures. It bundles the effect implementations and an
//   ExecutionContext, so callers just say Dispatcher.dispatch { workflow }
//   and get a Future back.
// - The actor receives a Dispatcher at creation time and uses it to
//   interpret the YAES workflow, piping the resulting Future back to
//   itself with pipeToSelf.

package experiment

import in.rcard.yaes.Yaes
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// ── Custom YAES Effects ─────────────────────────────────────────

type HttpClient = Yaes[HttpClient.Unsafe]

object HttpClient:
  def fetch(url: String)(using hc: HttpClient): String =
    hc.unsafe.fetch(url)

  trait Unsafe:
    def fetch(url: String): String

type Logger = Yaes[Logger.Unsafe]

object Logger:
  def info(msg: String)(using l: Logger): Unit  = l.unsafe.info(msg)
  def error(msg: String)(using l: Logger): Unit = l.unsafe.error(msg)

  trait Unsafe:
    def info(msg: String): Unit
    def error(msg: String): Unit

// ── Dispatcher ──────────────────────────────────────────────────
// Interprets (HttpClient & Logger) programs into Futures.
// The actor receives an instance at creation time and calls
// dispatcher.dispatch { workflow } to get a Future back.

trait Dispatcher:
  def dispatch[A](program: (HttpClient, Logger) ?=> A): Future[A]

object Dispatcher:
  def create(
      httpClient: HttpClient,
      logger: Logger,
      ec: ExecutionContext
  ): Dispatcher =
    new Dispatcher:
      def dispatch[A](program: (HttpClient, Logger) ?=> A): Future[A] =
        Future {
          given HttpClient = httpClient
          given Logger     = logger
          program
        }(using ec)

// ── YAES Workflow ───────────────────────────────────────────────
// A pure effectful program that requires HttpClient & Logger.

def fetchData(url: String)(using HttpClient, Logger): String =
  Logger.info(s"Fetching $url")
  val response = HttpClient.fetch(url)
  Logger.info(s"Received: $response")
  response

// ── Pekko Typed Actor ───────────────────────────────────────────

object FetcherActor:

  // --- Protocol ---
  sealed trait Command
  final case class Fetch(url: String)                              extends Command
  private final case class FetchResult(url: String, value: String) extends Command
  private final case class FetchFailed(url: String, ex: Throwable) extends Command

  // --- State ---
  final case class State(fetchCount: Int, lastValue: Option[String])

  // --- Behavior ---
  def apply(dispatcher: Dispatcher): Behavior[Command] =
    running(dispatcher, State(0, None))

  private def running(dispatcher: Dispatcher, state: State): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Fetch(url) =>
          println(s"  [actor] Received Fetch($url)")
          ctx.pipeToSelf(dispatcher.dispatch { fetchData(url) }) {
            case Success(v) => FetchResult(url, v)
            case Failure(e) => FetchFailed(url, e)
          }
          Behaviors.same

        case FetchResult(url, value) =>
          val next = State(state.fetchCount + 1, Some(value))
          println(s"  [actor] Fetch #${next.fetchCount} complete: $url -> $value")
          running(dispatcher, next)

        case FetchFailed(url, ex) =>
          println(s"  [actor] Fetch failed for $url: ${ex.getMessage}")
          Behaviors.same
    }

// ── Main ────────────────────────────────────────────────────────

@main def pekkoExperiment(): Unit =
  println("=== YAES + Pekko Integration Experiment ===")
  println()

  // Real HTTP implementation using java.net.http
  val jdkClient = java.net.http.HttpClient.newHttpClient()

  val httpClient: HttpClient = new Yaes(new HttpClient.Unsafe:
    def fetch(url: String): String =
      val request = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(url))
        .GET()
        .build()
      jdkClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).body()
  )

  val logger: Logger = new Yaes(new Logger.Unsafe:
    def info(msg: String): Unit  = println(s"  [INFO]  $msg")
    def error(msg: String): Unit = println(s"  [ERROR] $msg")
  )

  // Wire the Dispatcher with stubs and a global EC for the demo
  val dispatcher = Dispatcher.create(
    httpClient,
    logger,
    ExecutionContext.global
  )

  // Spawn the actor system
  val system: ActorSystem[FetcherActor.Command] =
    ActorSystem(FetcherActor(dispatcher), "yaes-pekko")

  // Send a few Fetch messages against JSONPlaceholder
  system ! FetcherActor.Fetch("https://jsonplaceholder.typicode.com/todos/1")
  system ! FetcherActor.Fetch("https://jsonplaceholder.typicode.com/posts/1")
  system ! FetcherActor.Fetch("https://jsonplaceholder.typicode.com/users/1")

  // Give the actor time to process, then shut down
  Thread.sleep(2000)
  system.terminate()

  println()
  println("=== Done ===")
