package com.checador.controller;

import com.checador.entity.Branch;
import com.checador.service.BranchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping("/public")
    public ResponseEntity<List<Branch>> getPublicActiveBranches() {
        return ResponseEntity.ok(branchService.getAll());
    }

    @GetMapping
    public ResponseEntity<List<Branch>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Branch> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Branch> create(@Valid @RequestBody BranchRequest req) {
        Branch branch = branchService.create(req.name(), req.address(), req.latitude(),
                req.longitude(), req.radiusMeters(), req.toleranceMinutes());
        return ResponseEntity.ok(branch);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Branch> update(@PathVariable Long id,
                                          @RequestBody BranchRequest req) {
        Branch branch = branchService.update(id, req.name(), req.address(), req.latitude(),
                req.longitude(), req.radiusMeters(), req.toleranceMinutes());
        return ResponseEntity.ok(branch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        branchService.deactivate(id);
        return ResponseEntity.ok(Map.of("message", "Sucursal desactivada"));
    }

    public record BranchRequest(
            @NotBlank String name,
            String address,
            @NotNull Double latitude,
            @NotNull Double longitude,
            Integer radiusMeters,
            Integer toleranceMinutes
    ) {}
}
