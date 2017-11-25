package org.http4s.http4sresourceexample

import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import fs2._
import org.http4s._
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO]{

  // Concrete Server in type IO[_] for StreamApp - Only Passes Required Values to the Kleisli
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    server[IO].run(requestShutdown)
  }

  // Create Server As a Kleisli in Stream[F, ?] consume the requestShutdown to terminate server
  // automatically after 30 seconds
  def server[F[_]: Effect]: Kleisli[Stream[F, ?], F[Unit], ExitCode] = Kleisli{ shutdown =>
    for {

      // Global Resources
      scheduler <- Scheduler(5)
      // Bracket Resources the need to be terminated on server shutdown to ensure they are gracefully handled.
      client <- Stream.bracket(PooledHttp1Client().pure[F])(Stream.emit(_).covary[F], _.shutdown)

      // Service Resources
      timer <- Stream.eval(async.signalOf[F, FiniteDuration](0.seconds))
      counter <- Stream.eval(async.refOf[F, Int](0))

      services = counterService[F](counter) <+> timerService[F](timer) // Combine Services

      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .mountService(services)
        .serve
        .concurrently(scheduler.awakeEvery[F](1.milli).to(_.evalMap(timer.set))) // Update Timer Every Millisecond
        .concurrently(scheduler.sleep(30.seconds).evalMap(_ => shutdown)) // Shutdown the Server After 30 Seconds

    } yield exitCode
  }

  def counterService[F[_]: Effect](counter: async.Ref[F, Int]): HttpService[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    HttpService{
      case GET -> Root / "counter" =>
        counter.modify(_ + 1).flatMap(change => Ok(show"Counter - ${change.now}"))
    }
  }

  def timerService[F[_]: Effect](timer: async.immutable.Signal[F, FiniteDuration]): HttpService[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    HttpService{
      case GET -> Root / "timer" =>
        timer.get.flatMap(aliveTime => Ok(show"${aliveTime.toMillis} milliseconds"))
    }
  }

}