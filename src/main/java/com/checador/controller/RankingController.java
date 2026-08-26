package com.checador.controller;

import com.checador.service.RankingService;
import com.checador.service.RankingService.RankingResponseDTO;
import com.checador.service.RankingService.RewardsConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RankingResponseDTO> getRanking() {
        return ResponseEntity.ok(rankingService.calculateRanking());
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'ADMIN', 'EMPLOYEE')")
    public ResponseEntity<RewardsConfigDTO> getConfig() {
        return ResponseEntity.ok(rankingService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('SUPERUSER')")
    public ResponseEntity<RewardsConfigDTO> updateConfig(@RequestBody RewardsConfigDTO configDTO) {
        return ResponseEntity.ok(rankingService.updateConfig(configDTO));
    }
}
