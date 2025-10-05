package com.example.bank

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import org.scalatest.funsuite.AsyncFunSuite
import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal

class MainSpec extends AsyncFunSuite {

  test("startServer should bind and respond to /api/health") {
    val testKit = ActorTestKit()
    implicit val system: ActorSystem[Nothing] = testKit.system
    implicit val ec = system.executionContext

    // start server on ephemeral port 0
    val bindF: Future[akka.http.scaladsl.Http.ServerBinding] = Main.startServer("localhost", 0, maxConcurrency = 2)(system)

    bindF.flatMap { binding =>
      val address = binding.localAddress
      val url = s"http://${address.getHostString}:${address.getPort}/api/health"

      // perform HTTP GET using the classic Http client via adapter
      val responseF = Http()(system.toClassic).singleRequest(HttpRequest(uri = url))

      responseF.flatMap { resp =>
        Unmarshal(resp.entity).to[String].flatMap { bodyRaw =>
          // response may be JSON-encoded string (e.g. "OK"); strip surrounding quotes
          val body = bodyRaw.stripPrefix("\"").stripSuffix("\"")
          // cleanup: unbind and shutdown testkit
          binding.unbind().map { _ =>
            testKit.shutdownTestKit()
            assert(resp.status.isSuccess())
            assert(body == "OK")
          }
        }
      }
    }
  }
}