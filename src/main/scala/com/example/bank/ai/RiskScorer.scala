package com.example.bank.ai

import cats.effect.IO
import com.example.bank.domain.TradeRequest
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser._
import sttp.client3._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.circe._

final case class ChatMessage(role: String, content: String)
final case class ChatRequest(model: String,
                             messages: List[ChatMessage],
                             max_tokens: Int,
                             temperature: Double)

object RiskScorer {
  private val apiKey = sys.env.getOrElse("OPENAI_API_KEY", "")
  private val openAiUrl = uri"https://api.openai.com/v1/chat/completions"
  private val model = "gpt-4o-mini" // replace with any available model

  private def fallbackScore(trade: TradeRequest): ExplainableScore = {
    val notional = trade.quantity * trade.price
    val base = math.log1p(notional) / 10.0
    val factor = if (trade.symbol.endsWith("JPY")) 1.2 else if (trade.symbol.endsWith("USD")) 1.0 else 1.1
    val s = (base * factor) % 10.0
    ExplainableScore(s, s"fallback deterministic score; notional=$notional")
  }

  // sanitize assistant text: remove ``` fences, optional language tag, and trim
  private def sanitizeAssistantText(text: String): String = {
    val t = Option(text).getOrElse("").trim
    // remove leading/trailing triple backticks block and optional language tag (e.g., ```json)
    val tripleFenceRegex = """(?s)^\s*```[a-zA-Z0-9_-]*\s*(.*)\s*```$""".r
    val noFence = t match {
      case tripleFenceRegex(inner) => inner.trim
      case _ => t.stripPrefix("```").stripSuffix("```").trim
    }
    // also remove surrounding single backticks if present
    noFence.stripPrefix("`").stripSuffix("`").trim
  }

  def score(trade: TradeRequest): IO[ExplainableScore] = {
    val prompt =
      s"""You are an explainable risk scorer. Given the trade fields produce:
         |- a numeric score between 0 and 10 (higher = riskier)
         |- a short explanation listing the main contributing features and numbers
         |
         |Trade:
         |- id: ${trade.id}
         |- symbol: ${trade.symbol}
         |- quantity: ${trade.quantity}
         |- price: ${trade.price}
         |
         |Also compute notional = quantity * price and include it in explanation.
         |Return JSON with keys: score, explanation.
         |Be concise and deterministic.
         |""".stripMargin

    val requestBody = ChatRequest(
      model = model,
      messages = List(
        ChatMessage("system", "You are a deterministic, concise risk-scoring assistant."),
        ChatMessage("user", prompt)
      ),
      max_tokens = 200,
      temperature = 0.0
    )

    AsyncHttpClientCatsBackend.resource[IO]().use { backend =>
      val req = basicRequest
        .post(openAiUrl)
        .auth.bearer(apiKey)
        .contentType("application/json")
        .body(requestBody)
        .response(asJson[Json])

      for {
        response  <- req.send(backend)
        responseEither = response.body
        result <- responseEither match {
          case Left(err) =>
            IO.delay(println(s"[RiskScorer] LLM call failed: $err")) *> IO.pure(fallbackScore(trade))

          case Right(json) =>
            val loggingIO = IO.delay {
              val keyPresent = apiKey.nonEmpty
              println(s"[RiskScorer] OPENAI_API_KEY present: $keyPresent")
              println("[RiskScorer] OPENAI JSON RESPONSE: " + json.noSpaces)

              // try to print assistant raw text if available (masked)
              val assistantTextOpt: Option[String] = for {
                ch <- json.hcursor.downField("choices").focus
                arr <- ch.asArray
                first <- arr.headOption
                text <- first.hcursor.downField("message").downField("content").as[String].toOption
              } yield text
              assistantTextOpt.foreach(t => println("[RiskScorer] Assistant text (raw, trimmed): " + t.trim))
            }

            loggingIO *> {
              val maybeRawText: Option[String] = for {
                choicesJson <- json.hcursor.downField("choices").focus
                choicesArr  <- choicesJson.asArray
                first       <- choicesArr.headOption
                messageJson <- first.hcursor.downField("message").focus
                content     <- messageJson.hcursor.downField("content").as[String].toOption
              } yield content

              maybeRawText match {
                case Some(raw) =>
                  val text = sanitizeAssistantText(raw)
                  parseScoreAndExplanation(text, trade)
                case None =>
                  // also try legacy choices[0].text
                  val legacyTextOpt: Option[String] = for {
                    ch <- json.hcursor.downField("choices").focus
                    arr <- ch.asArray
                    first <- arr.headOption
                    txt <- first.hcursor.downField("text").as[String].toOption
                  } yield txt

                  legacyTextOpt match {
                    case Some(raw2) =>
                      val text = sanitizeAssistantText(raw2)
                      parseScoreAndExplanation(text, trade)
                    case None =>
                      IO.delay(println(s"[RiskScorer] No assistant content found; returning fallback. JSON: ${json.noSpaces}")) *>
                        IO.pure(fallbackScore(trade))
                  }
              }
            }
        }
      } yield result
    }
  }

  private def parseScoreAndExplanation(text: String, trade: TradeRequest): IO[ExplainableScore] = IO {
    // Try parse JSON first (text should already be sanitized)
    val tryJson = parse(text).flatMap(_.as[ExplainableScore])
    tryJson match {
      case Right(es) => es
      case Left(_) =>
        // fallback: extract first numeric score in text and use full text as explanation
        val cleaned = text.trim
        val scoreOpt = """([0-9]+(\.[0-9]+)?)""".r.findFirstIn(cleaned).map(_.toDouble)
        val notional = trade.quantity * trade.price
        val score = scoreOpt.getOrElse {
          val base = math.log1p(notional) / 10.0
          val symbolFactor = if (trade.symbol.endsWith("JPY")) 1.2 else if (trade.symbol.endsWith("USD")) 1.0 else 1.1
          (base * symbolFactor) % 10.0
        }
        ExplainableScore(score, s"LLM raw output: $cleaned; computed_notional=$notional")
    }
  }
}
