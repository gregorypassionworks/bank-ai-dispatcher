package com.example.bank

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.example.bank.dispatcher.TradeDispatcher
import com.example.bank.http.HttpRoutes

import scala.concurrent.Future

object Main {

  /**
   * Start the HTTP server and return the binding Future.
   * Tests should create their own ActorSystem and call this method (use port 0 for ephemeral port).
   */
  def startServer(host: String = "localhost", port: Int = 8080, maxConcurrency: Int = 8)(implicit system: ActorSystem[_]): Future[akka.http.scaladsl.Http.ServerBinding] = {
    implicit val ec = system.executionContext
    val dispatcher = system.systemActorOf(TradeDispatcher(maxConcurrency), "dispatcher")
    val routes = new HttpRoutes(dispatcher)(system)
    Http().newServerAt(host, port).bind(routes.routes)
  }

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "bank-system")
    implicit val ec = system.executionContext

    val bindingFuture = startServer("localhost", 8080, 8)(system)
    println("Server started at http://localhost:8080")
    scala.io.StdIn.readLine("press ENTER to terminate\n")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
