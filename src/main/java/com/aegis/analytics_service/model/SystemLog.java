package com.aegis.analytics_service.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "system_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @JsonProperty("serviceName")
    private String serviceName;

    @Column(nullable = false)
    @JsonProperty("logLevel")
    private String logLevel; // WARN, ERROR

    @Column(nullable = false, columnDefinition = "TEXT")
    @JsonProperty("message")
    private String message;

    @Column(columnDefinition = "TEXT")
    @JsonProperty("stackTrace")
    private String stackTrace;

    @Column(nullable = false)
    @JsonProperty("timestamp")
    private Instant timestamp;
}
