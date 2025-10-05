package com.example.bank.http

import akka.actor.typed.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.example.bank.domain._
import com.example.bank.dispatcher._
import akka.actor.typed.ActorSystem
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.typed.scaladsl.AskPattern._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class HttpRoutes(dispatcher: ActorRef[DispatcherCommand])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = 3.seconds
  implicit val ec: ExecutionContext = system.executionContext

  val routes: Route =
    pathPrefix("api") {
      path("trade") {
        post {
          entity(as[TradeRequest]) { trade =>
            // ask pattern: create an actor to receive the ProcessResult
            val resF: Future[ProcessResult] =
              dispatcher.ask[ProcessResult](replyTo => DispatchTrade(trade, replyTo))
            onSuccess(resF) { result =>
              complete(result)
            }
          }
        }
      } ~
        path("health") {
          get {
            complete("OK")
          }
        }
    }
}
