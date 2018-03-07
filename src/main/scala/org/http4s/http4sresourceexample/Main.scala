package org.http4s.http4sresourceexample

import cats.implicits._
import cats.effect._
import fs2._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.{AutoSlash, GZip, Logger}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends StreamApp[IO]{

  // Concrete Server in type IO[_] for StreamApp - Only Passes Required Values to the Kleisli
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] = {
    server[IO](requestShutdown)
  }

  // Create Server As a Kleisli in Stream[F, ?] consume the requestShutdown to terminate server
  // automatically after 30 seconds
  def server[F[_]](shutdown: F[Unit])(implicit F: Effect[F]): Stream[F, StreamApp.ExitCode] = {
    for {
      // Global Resources
      scheduler <- Scheduler(5)
      // Bracket Resources that need to be terminated on server shutdown to ensure they are gracefully handled.
      client <- Http1Client.stream[F]()

      // Service Resources - Needed in multiple locations.
      timer <- Stream.eval(async.signalOf[F, FiniteDuration](0.seconds))

      // Service Built With Non Leaking Resource F[HttpService[F]]
      // This can be done in a function so the main comprehension never sees it.
      counterServ <- Stream.eval(async.refOf[F, Int](0).map(counterService[F]))

      // Compose Services
      services = counterServ <+> timerService[F](timer)

      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .mountService(myMiddleWare[F](F)(services))
        .serve
        .concurrently(scheduler.awakeEvery[F](1.milli).to(_.evalMap(timer.set))) // Update Timer Every Millisecond
        .concurrently(scheduler.sleep(30.seconds).evalMap(_ => shutdown)) // Shutdown the Server After 30 Seconds

    } yield exitCode
  }

  // Construct Middleware via Composition
  def myMiddleWare[F[_]](implicit F: Effect[F]) =
  {httpService: HttpService[F] => GZip(httpService)(F)} compose
    {httpService : HttpService[F] => Logger(true, true)(httpService)(F)} compose
    {httpService : HttpService[F] => AutoSlash(httpService)(F)}



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
