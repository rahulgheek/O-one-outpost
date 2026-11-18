package com.aegis.arena_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Document(collection = "match_records")
public class MatchRecord {

    @Id
    private String id;

    private String matchId;
    private String winnerId;
    private String winningStatus; // e.g., "SUCCESS"
    /** Judge language of the winning submission. */
    private String language;
    /** Source submitted by the winner, kept for audits and appeals. */
    private String submittedCode;
    private LocalDateTime timestamp;
}