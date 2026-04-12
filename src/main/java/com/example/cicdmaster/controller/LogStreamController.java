package com.example.cicdmaster.controller;

import com.example.cicdmaster.service.LogStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/logs")
@Tag(name = "Logs", description = "Поток логов в реальном времени для UI")
public class LogStreamController {

    private final LogStreamingService logStreamingService;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Подписка на поток логов", description = "Если jobId не задан, отдаются события по всем job")
    public SseEmitter streamLogs(
            @Parameter(description = "Идентификатор job")
            @RequestParam(required = false) UUID jobId) {
        return logStreamingService.subscribe(jobId);
    }
}