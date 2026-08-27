package com.checador.controller;

import com.checador.entity.User;
import com.checador.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class RealtimeEventController {

    private final RealtimeEventService realtimeEventService;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter subscribe(@AuthenticationPrincipal User user) {
        Long userId = user != null ? user.getId() : 0L;
        String role = user != null && user.getRole() != null ? user.getRole().name() : "GUEST";
        Long branchId = user != null && user.getBranch() != null ? user.getBranch().getId() : null;

        return realtimeEventService.subscribe(userId, role, branchId);
    }
}
