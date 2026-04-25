package com.analysis.model.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}

