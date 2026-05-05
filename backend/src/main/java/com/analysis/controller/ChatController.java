package com.analysis.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.analysis.model.dto.ChatAnalyzeRequest;
import com.analysis.model.dto.CreateChatSessionRequest;
import com.analysis.model.dto.PromoteWorkspaceRequest;
import com.analysis.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody(required = false) CreateChatSessionRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String title = request != null ? request.getTitle() : null;
            var session = chatService.createSession(title);
            response.put("success", true);
            response.put("data", session);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create chat session: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.getAllSessions());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to list chat sessions: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable("sessionId") Long sessionId,
            @RequestBody CreateChatSessionRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.updateSessionTitle(sessionId, request != null ? request.getTitle() : null));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update chat session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable("sessionId") Long sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            chatService.deleteSession(sessionId);
            response.put("success", true);
            response.put("message", "会话已删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete chat session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(@PathVariable("sessionId") Long sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.getSessionMessages(sessionId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get messages for session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/sessions/{sessionId}/datasets")
    public ResponseEntity<Map<String, Object>> getSessionDatasets(@PathVariable("sessionId") Long sessionId) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.getSessionDatasets(sessionId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get datasets for session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/sessions/{sessionId}/upload")
    public ResponseEntity<Map<String, Object>> uploadToSession(
            @PathVariable("sessionId") Long sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.uploadDatasetToSession(sessionId, file, name));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload dataset to session {}: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/sessions/{sessionId}/analyze")
    public ResponseEntity<?> analyze(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody ChatAnalyzeRequest request) {
        return ResponseEntity.ok(chatService.analyze(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/promote-to-workspace")
    public ResponseEntity<Map<String, Object>> promoteToWorkspace(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody PromoteWorkspaceRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("success", true);
            response.put("data", chatService.promoteToWorkspace(
                    sessionId,
                    request.getWorkspaceName(),
                    request.getDescription()));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to promote chat session {} to workspace: {}", sessionId, e.getMessage(), e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
