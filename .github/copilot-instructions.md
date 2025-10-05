# Copilot instructions for bank-ai-dispatcher

This file gives focused, actionable information for AI coding agents to be productive in this repository.

## Quick summary (big picture)
- Scala microservice using Akka Typed actors + cats-effect for async work.
- Purpose: receive TradeRequest, compute an ExplainableScore (LLM when available), and return a ProcessResult.
- Main pieces: `ai` (RiskScorer + ExplainableScore), `dispatcher` (TradeDispatcher, TradeProcessor, DispatcherProtocol), `http` (HttpRoutes), `domain` (TradeRequest, ProcessResult).

## How to run locally (developer flow)
- Build & run with sbt. Main entry: `com.example.bank.Main` (starts HTTP server on localhost:8080).
- PowerShell example (set key + run):

  $env:OPENAI_API_KEY = "sk-..." ; sbt run

- If `OPENAI_API_KEY` is empty RiskScorer logs this and returns a deterministic fallback score.

## Key patterns & conventions (discoverable in code)
- HTTP API: `POST /api/trade` expects JSON matching `domain.TradeRequest` and returns `domain.ProcessResult`.
  See `src/main/scala/com/example/bank/http/HttpRoutes.scala`.
- Actor API: `DispatcherCommand` defines messages. Use `DispatchTrade(trade, replyTo)` to ask the dispatcher for a `ProcessResult`.
  See `src/main/scala/com/example/bank/dispatcher/DispatcherProtocol.scala`.
- Concurrency: `TradeDispatcher` creates a cats-effect `Semaphore` on actor setup to limit concurrent LLM calls (`maxConcurrency` parameter).
  Implementation uses `Semaphore[IO](maxConcurrency).unsafeRunSync()` and runs IO tasks with `.unsafeRunAndForget()` (see `TradeDispatcher.scala`).
- Synchronous usage: `TradeProcessor` calls `RiskScorer.score(...).unsafeRunSync()`—a pattern to watch for blocking in actor contexts.

## LLM integration specifics (RiskScorer)
- Location: `src/main/scala/com/example/bank/ai/RiskScorer.scala`.
- API: uses STTP + AsyncHttpClient Cats backend, sends chat-completions to `https://api.openai.com/v1/chat/completions`.
- How output is handled:
  - Looks for `choices[0].message.content` (or legacy `choices[0].text`).
  - Sanitizes assistant text (removes ``` fences and single backticks).
  - Attempts to parse sanitized text as JSON into `ExplainableScore(score, explanation)` (see `ai/ExplainableScore.scala`).
  - If parsing fails, extracts a numeric token from text or falls back to a deterministic score computed from notional (log1p) and symbol factors.

## Files you will likely edit
- Business logic / scoring: `ai/RiskScorer.scala`, `ai/ExplainableScore.scala`.
- Actor orchestration: `dispatcher/TradeDispatcher.scala`, `dispatcher/TradeProcessor.scala`.
- HTTP surface: `http/HttpRoutes.scala`, `Main.scala` for wiring.

## Common checks and pitfalls to validate when changing code
- Ensure `OPENAI_API_KEY` usage: RiskScorer currently reads `sys.env` at object init; changing this affects testability and wiring.
- Actor ask-timeout: `HttpRoutes` uses a 3s `Timeout` for dispatcher.ask — increase if LLM or network calls are expected to be slow.
- Concurrency safety: semaphore created with `unsafeRunSync()`—if you change initialization, preserve that a cats-effect `Semaphore` is used to bound concurrent LLM calls.
- JSON parsing: RiskScorer accepts both structured JSON and free text; tests should include both response shapes.

## Useful quick references
- TradeRequest type: `src/main/scala/com/example/bank/domain/TradeRequest.scala` (id, symbol, quantity, price)
- ProcessResult type: `src/main/scala/com/example/bank/domain/ProcessResult.scala` (id, symbol, notional, score, explanation)
- Entry point: `src/main/scala/com/example/bank/Main.scala` (creates dispatcher & http routes)

If any section is unclear or you want me to expand (examples, tests to add, or merge with an existing guidance file), tell me which part to iterate on.
