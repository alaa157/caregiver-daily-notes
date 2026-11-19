# DEF-005 — Nondeterministic non-OpenAI request bodies (flaky byte contract)

- **Severity:** Minor (intermittent test failure; no production impact observed)
- **Component:** `backend/.../ai/ChatCompletionsLlmClient.java`
- **Environment:** JUnit 5, any JVM
- **Discovered:** 2026-11-19, `ChatCompletionsLlmClientTest.nonOpenAiRequestBodies_remainByteForByteUnchanged` failed in one full-suite run and passed in the next with no code change between them.

## Root cause

Message maps were built with `Map.of("role", …, "content", …)`. `Map.of` iteration order is salted per JVM, so Jackson serialized `content` before `role` in some runs. The non-OpenAI request body is a **byte-for-byte contract** (the test pins the exact JSON), making the failure intermittent and JVM-dependent.

## Fix

Commit `8b3df8fa`: `LinkedHashMap` with explicit `role` → `content` insertion order for both messages. Deterministic serialization on every JVM.

## Verification

The byte-contract test passes across repeated full-suite runs (runs 5–7).

**Status: fixed.**
