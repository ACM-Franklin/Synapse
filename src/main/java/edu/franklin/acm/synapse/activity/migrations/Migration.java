package edu.franklin.acm.synapse.activity.migrations;

import java.time.LocalDateTime;

public record Migration(String name, boolean succeeded, LocalDateTime occurredAt) {}
