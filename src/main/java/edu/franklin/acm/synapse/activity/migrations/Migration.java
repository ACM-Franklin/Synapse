package edu.franklin.acm.synapse.activity.migrations;

import java.time.LocalDateTime;

/**
 * This record represents an occurrence of a migration file execution.
 * @param name        The name of the migration file ran.
 * @param succeeded   Whether this execution succeeded.
 * @param occurredAt  When the execution occurred.
 */
public record Migration(String name, boolean succeeded, LocalDateTime occurredAt) {}
