package com.example.bank.ai

import cats.effect.IO
import com.example.bank.domain.TradeRequest

object RiskScorer {
  // Pure, deterministic "model" implemented as Cats Effect IO so it can be tested/combined.
  def score(trade: TradeRequest): IO[ExplainableScore] = IO {
    // simple features
    val notional = trade.quantity * trade.price
    val base = math.log1p(notional) / 10.0
    val symbolFactor = trade.symbol match {
      case s if s.endsWith("JPY") => 1.2
      case s if s.endsWith("USD") => 1.0
      case _ => 1.1
    }
    val volatility = (trade.price % 1.0) * 0.5
    val score = (base * symbolFactor) + volatility
    val explanation =
      s"notional=$notional; base=${base.formatted("%.4f")}; symbolFactor=$symbolFactor; volatility=${volatility.formatted("%.4f")}"
    ExplainableScore(score, explanation)
  }
}
