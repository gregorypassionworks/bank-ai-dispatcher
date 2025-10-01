package com.example.bank.domain

final case class TradeRequest(id: String, symbol: String, quantity: Long, price: Double)
