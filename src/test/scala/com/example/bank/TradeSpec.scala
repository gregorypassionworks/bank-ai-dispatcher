package com.example.bank

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal

class TradeSpec extends AsyncFunSuite {

  test("POST /api/trade should return a ProcessResult JSON") {
    val testKit = ActorTestKit()
    implicit val system: ActorSystem[Nothing] = testKit.system
    implicit val ec = system.executionContext

    val bindF: Future[akka.http.scaladsl.Http.ServerBinding] = Main.startServer("localhost", 0, maxConcurrency = 2)(system)

    bindF.flatMap { binding =>
      val address = binding.localAddress
      val url = s"http://${address.getHostString}:${address.getPort}/api/trade"

      val json =
        s"""{
           "id": "T-1",
           "symbol": "EURUSD",
           "quantity": 100000,
           "price": 1.12
        }"""

      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = url,
        entity = HttpEntity(ContentTypes.`application/json`, json)
      )

      val responseF = Http()(system.toClassic).singleRequest(request)

      responseF.flatMap { resp =>
        Unmarshal(resp.entity).to[String].map { body =>
          // cleanup
          binding.unbind()
          testKit.shutdownTestKit()
          assert(resp.status.isSuccess())
          assert(body.contains("\"id\":\"T-1\""))
          assert(body.contains("\"score\""))
        }
      }
    }
  }
}


