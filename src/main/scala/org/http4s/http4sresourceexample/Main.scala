package org.http4s.http4sresourceexample

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

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    server[IO]
  }

  def server[F[_]: Effect]: Stream[F, ExitCode] = {
    for {

      scheduler <- Scheduler(5)
      client <- Stream.bracket(PooledHttp1Client().pure[F])(Stream.emit(_).covary[F], _.shutdown)

      timer <- Stream.eval(async.signalOf[F, FiniteDuration](0.seconds))
      counter <- Stream.eval(async.refOf[F, Int](0))

      exitCode <- BlazeBuilder[F]
        .bindHttp(8080, "0.0.0.0")
        .mountService(
          counterService[F](counter) <+>
            timerService[F](timer)
        )
        .serve
        .concurrently(scheduler.awakeEvery[F](1.milli).to(_.evalMap(timer.set)))

    } yield exitCode
  }

  def counterService[F[_]: Effect](counter: async.Ref[F, Int]): HttpService[F] = {
    object dsl extends Http4sDsl[F]
    import dsl._
    HttpService{
      case GET -> Root / "counter" =>
        counter.modify(_ + 1).flatMap(change => Ok(change.now.show))
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