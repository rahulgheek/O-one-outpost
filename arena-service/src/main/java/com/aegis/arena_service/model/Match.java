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
@Document(collection = "matches")
public class Match {
    @Id
    private String matchId;

    private String playerOneId;
    private String playerTwoId;
    private String problemId;
    private String status;

    private String winnerId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer playerOneRating;
    private Integer playerTwoRating;
}
