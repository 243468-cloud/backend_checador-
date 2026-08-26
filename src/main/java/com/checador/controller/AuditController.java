package com.checador.controller;

import com.checador.entity.User;
import com.checador.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<?> getGlobalLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var logs = auditService.getGlobalLogs(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<?> getLogsByBranch(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var logs = auditService.getLogsByBranch(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(logs);
    }
}
