package edu.franklin.acm.synapse.api.dto;

/**
 * Body for POST /api/scans/historical. {@code confirm} must literally be the
 * string {@code "I_UNDERSTAND_THIS_SCANS_DISCORD"} — historical scans hit the
 * Discord API hard and should not start on a casual mis-click.
 */
public record HistoricalScanRequest(String confirm) {
}
