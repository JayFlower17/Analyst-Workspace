package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatSession {
    private Long id;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}

