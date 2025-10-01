package com.example.bank.dispatcher

import com.example.bank.domain.{TradeRequest, ProcessResult}
import akka.actor.typed.ActorRef

trait DispatcherCommand
final case class DispatchTrade(trade: TradeRequest, replyTo: ActorRef[ProcessResult]) extends DispatcherCommand
case object Shutdown extends DispatcherCommand