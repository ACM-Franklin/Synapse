package edu.franklin.acm.synapse.api.dto;

/**
 * Body for POST /api/admin/replay/messages. {@code confirm} must literally be
 * the string {@code "I_UNDERSTAND_THIS_REPLAYS_REWARDS"} — a deliberate
 * speed-bump so casual POSTs cannot wreck reward state.
 */
public record ReplayRequest(String confirm, Integer batchSize) {
}
