# Overview

This project is a small Scala-based microservice that scores trades for risk using a simple deterministic fallback scorer plus optional calls to an LLM (OpenAI chat completions). The service accepts trade requests, calls RiskScorer to compute an ExplainableScore (numeric 0–10 plus short explanation), and returns or forwards a ProcessResult to the caller. The design favors safe defaults, deterministic fallback behavior, and clear instrumentation for debugging LLM integration.

---

## Architecture

- RiskScorer (core)
    - Sends chat-completion requests to the OpenAI API when an API key is available.
    - Sanitizes assistant text (removes ``` fences), parses JSON output into ExplainableScore, and falls back to deterministic scoring when parsing or the API call fails.
- Actor-based dispatcher (Akka)
    - Receives incoming TradeRequest messages, invokes RiskScorer.score(trade), and always replies to the original requester with ProcessResult (success or fallback).
- HTTP / CLI / Test harness (not mandatory)
    - One can expose the dispatcher over HTTP or use test generators to push trades into the actor pipeline.
- Configuration and Secrets
    - API key is injected using environment variable.

---

## Setup and configuration

- Requirements
    - JDK 11+ (or the version your project uses)
    - sbt (if developing with sbt)
    - Internet access for OpenAI API (if you will use LLM)
- Getting an API key
    - Create a key at your OpenAI account (Platform → API keys). Copy it once and keep it secret.
    - Immediately revoke any key you accidentally exposed.
- How to provide the key (pick one)
    - Preferred (local/dev): set an environment variable in the same process that starts the JVM:
      - macOS / Linux:
        - export OPENAI_API_KEY="sk-..."
        - sbt run
      - Windows PowerShell:
        - $env:OPENAI_API_KEY = "sk-..." ; sbt run
      - IntelliJ Run Configuration: Run → Edit Configurations → Environment variables → OPENAI_API_KEY=sk-...
    - Alternate: pass as JVM property and read sys.props:
      - java -Dopenai.api.key="sk-..." -jar app.jar (read sys.props("openai.api.key"))  
    - Production: use a secret manager (Vault, AWS Secrets Manager, Azure Key Vault) and inject at startup; pass into RiskScorer constructor.
- Recommended wiring
    - Do not read sys.env inside an object at classload time. Instead read configuration in Main and create RiskScorer(apiKey).

---

## How it works (end-to-end)

1. A TradeRequest arrives (example JSON: { "id":"T-1", "symbol":"EUR/USD", "quantity":100000, "price":1.12 }).
2. The dispatcher calls RiskScorer.score(trade) (returns cats-effect IO[ExplainableScore]).
3. RiskScorer:
    - Builds a deterministic prompt asking the model to return JSON {score, explanation}.
    - Sends a request to OpenAI with Authorization: Bearer <OPENAI_API_KEY>.
    - Receives JSON response and extracts assistant content from choices[0].message.content (or choices[0].text).
    - Sanitizes assistant text (strips ``` fences, language tags).
    - Attempts to parse sanitized text as JSON into ExplainableScore.
    - If parsing succeeds, clamps/validates numeric score and returns it.
    - If parsing fails or the HTTP call fails, computes a deterministic fallback ExplainableScore (log1p(notional)/10 * factor) and returns it.
4. The dispatcher maps the IO result back to the actor context, sending ProcessResult to the original requester in both success and error cases to avoid Ask timeouts and DeadLetters.