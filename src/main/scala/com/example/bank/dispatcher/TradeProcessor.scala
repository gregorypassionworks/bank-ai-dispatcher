// TradeProcessor.scala
package com.example.bank.dispatcher

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.example.bank.domain.{TradeRequest, ProcessResult}
import com.example.bank.ai.RiskScorer
import cats.effect.unsafe.IORuntime
import akka.actor.typed.ActorRef

object TradeProcessor {
  def apply(): Behavior[(TradeRequest, ActorRef[ProcessResult])] =
    Behaviors.setup { _ =>
      implicit val rt: IORuntime = IORuntime.global
      Behaviors.receiveMessage { case (trade, replyTo) =>
        val explain = RiskScorer.score(trade).unsafeRunSync()
        val result = ProcessResult(trade.id, trade.symbol, trade.quantity * trade.price, explain.score, explain.explanation)
        replyTo ! result
        Behaviors.same
      }
    }
}
