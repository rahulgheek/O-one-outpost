package com.aegis.arena_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "daily_question")
public class DailyQuestion {

    @Id
    private String id;

    /** The ID of the Problem document this daily question refers to */
    private String problemId;

    /** The exact UTC instant when this problem becomes accessible to users */
    private Instant activatesAt;

    /** activatesAt + 24 hours — set automatically on creation */
    private Instant expiresAt;

    /** Email of the admin who scheduled this */
    private String scheduledBy;

    /** Optional display label, e.g. "Day 42 Challenge" */
    private String label;
}
