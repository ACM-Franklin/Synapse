# Current Task: OAuth PKCE And Session Hardening

## Goal

Apply the useful parts of the security review without turning Synapse into an
enterprise auth hairball. Keep the v1 single-instance model, but close protocol
gaps that are cheap and real.

## Plan

- [x] Add PKCE `code_verifier` / S256 `code_challenge` to the Discord OAuth flow.
- [x] Compare returned OAuth state and cookie state with timing-safe equality.
- [x] Bound the transient OAuth state store so login spam cannot grow memory forever.
- [x] Add production reverse-proxy and CORS hardening config for the same-host deployment model.
- [x] Update tests and docs, then run focused and full validation.

## Review

- Review item rejected for now: replacing the in-process session store with
	stateless encrypted cookies is not part of MVP hardening. It changes auth
	architecture and revocation semantics; the current TTL-bounded session map is
	deliberately simple for a single-instance bot.
- Review item deferred: a reactive, header-aware Discord 429 retry client is not
	necessary until real traffic proves the current local rate limiter is
	insufficient.
- Focused auth tests, full test suite, diagnostics, and `git diff --check` pass.
