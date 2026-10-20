package com.checador.service;

import com.checador.entity.Branch;
import com.checador.repository.BranchRepository;
import org.springframework.transaction.annotation.Transactional;
import com.checador.security.TenantContext;
import org.springframework.stereotype.Service;
import com.checador.exception.ResourceNotFoundException;

import java.util.List;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final TenantSettingsService tenantSettingsService;

    public BranchService(BranchRepository branchRepository, TenantSettingsService tenantSettingsService) {
        this.branchRepository = branchRepository;
        this.tenantSettingsService = tenantSettingsService;
    }

    public List<Branch> getAll() {
        return branchRepository.findByActive(true);
    }

    public List<Branch> getPublicBranchesBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return List.of();
        }
        Long tenantId = tenantSettingsService.getTenantIdBySlug(slug);
        if (tenantId == null) {
            return List.of();
        }
        try {
            TenantContext.setTenantId(tenantId);
            return branchRepository.findByActive(true);
        } finally {
            TenantContext.clear();
        }
    }

    public Branch findByIdWithTenant(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada o acceso denegado"));
    }

    @Transactional
    public Branch create(String name, String address, Double lat, Double lng,
                         Integer radiusMeters, Integer toleranceMinutes) {
        Branch branch = Branch.builder()
                .name(name)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .radiusMeters(radiusMeters != null ? radiusMeters : 100)
                .toleranceMinutes(toleranceMinutes != null ? toleranceMinutes : 10)
                .active(true)
                .build();
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch update(Long id, String name, String address, Double lat, Double lng,
                         Integer radiusMeters, Integer toleranceMinutes) {
        Branch branch = findByIdWithTenant(id);
        if (name != null) branch.setName(name);
        if (address != null) branch.setAddress(address);
        if (lat != null) branch.setLatitude(lat);
        if (lng != null) branch.setLongitude(lng);
        if (radiusMeters != null) branch.setRadiusMeters(radiusMeters);
        if (toleranceMinutes != null) branch.setToleranceMinutes(toleranceMinutes);
        return branchRepository.save(branch);
    }

    @Transactional
    public void deactivate(Long id) {
        Branch branch = findByIdWithTenant(id);
        branch.setActive(false);
        branchRepository.save(branch);
    }
}
