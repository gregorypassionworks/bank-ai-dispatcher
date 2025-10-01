package com.example.bank

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.example.bank.dispatcher.TradeDispatcher
import com.example.bank.http.HttpRoutes

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "bank-system")
    implicit val ec = system.executionContext

    val dispatcher = system.systemActorOf(TradeDispatcher(maxConcurrency = 8), "dispatcher")
    val routes = new HttpRoutes(dispatcher)(system)

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes.routes)
    println("Server started at http://localhost:8080")
    scala.io.StdIn.readLine("press ENTER to terminate\n")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
