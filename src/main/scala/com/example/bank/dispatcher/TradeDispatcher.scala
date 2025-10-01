package com.example.bank.dispatcher

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cats.effect.IO
import cats.effect.std.Semaphore
import cats.effect.unsafe.IORuntime
import com.example.bank.domain.{ProcessResult, TradeRequest}

import scala.concurrent.ExecutionContext

object TradeDispatcher {

  def apply(maxConcurrency: Int): Behavior[DispatcherCommand] =
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext
      implicit val rt: IORuntime = IORuntime.global

      // create a Semaphore in a simple, blocking way on actor startup
      // for a demo it's OK to create a semaphore synchronously; in production consider Resource wiring
      val semaphore = Semaphore[IO](maxConcurrency).unsafeRunSync()

      def runIO(io: IO[Unit]): Unit =
        io.unsafeRunAndForget()

      Behaviors.receiveMessage {
        case DispatchTrade(trade, replyTo) =>
          // create IO that acquires permit, runs scorer, replies to replyTo and releases permit automatically
          val task: IO[Unit] = semaphore.permit.use { _ =>
            for {
              explain <- com.example.bank.ai.RiskScorer.score(trade)
            } yield {
              val res = ProcessResult(trade.id, trade.symbol, trade.quantity * trade.price, explain.score, explain.explanation)
              replyTo ! res
            }
          }
          runIO(task) // start fiber; no blocking actor thread
          Behaviors.same

        case Shutdown =>
          Behaviors.stopped
      }
    }
}
