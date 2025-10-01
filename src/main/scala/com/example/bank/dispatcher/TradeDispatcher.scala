// TradeDispatcher.scala
package com.example.bank.dispatcher

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.example.bank.domain.{ProcessResult, TradeRequest}
import scala.collection.mutable

object TradeDispatcher {
  def apply(maxConcurrency: Int): Behavior[DispatcherCommand] =
    Behaviors.setup { ctx =>
      val processors = mutable.Map.empty[String, ActorRef[(TradeRequest, ActorRef[ProcessResult])]]
      val active = mutable.Set.empty[String]
      val queue = mutable.Queue.empty[(TradeRequest, ActorRef[ProcessResult])]

      def tryDispatch(): Unit = {
        while (active.size < maxConcurrency && queue.nonEmpty) {
          val (t, replyTo) = queue.dequeue()
          active += t.id
          val child = processors.getOrElseUpdate(t.id, ctx.spawn(TradeProcessor(), s"proc-${t.id}"))
          child ! (t, replyTo) // pass replyTo directly so child can reply to ask
        }
      }

      Behaviors.receiveMessage {
        case DispatchTrade(trade, replyTo) =>
          queue.enqueue((trade, replyTo))
          tryDispatch()
          Behaviors.same

        case Shutdown => Behaviors.stopped
      }
    }
}
