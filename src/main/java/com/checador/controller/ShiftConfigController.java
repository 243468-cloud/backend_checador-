package com.checador.controller;

import com.checador.entity.ShiftConfig;
import com.checador.service.ShiftConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings/shifts")
@RequiredArgsConstructor
public class ShiftConfigController {

    private final ShiftConfigService shiftConfigService;

    @GetMapping
    public ResponseEntity<List<ShiftConfig>> getShiftConfigs() {
        return ResponseEntity.ok(shiftConfigService.getAllConfigs());
    }

    @PutMapping
    public ResponseEntity<List<ShiftConfig>> updateShiftConfigs(@RequestBody List<ShiftConfig> configs) {
        return ResponseEntity.ok(shiftConfigService.updateConfigs(configs));
    }
}
