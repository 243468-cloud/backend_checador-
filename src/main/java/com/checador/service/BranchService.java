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
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && tenantId == 0L) {
            return tenantSettingsService.findAllActiveBranchesForSuperuser();
        }
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
        if (tenantId != null && tenantId == 0L) {
            Long branchTenantId = tenantSettingsService.findBranchTenantIdForSuperuser(id);
            if (branchTenantId == null) {
                throw new ResourceNotFoundException("Sucursal no encontrada o acceso denegado");
            }
            try {
                TenantContext.setTenantId(branchTenantId);
                return branchRepository.findByIdAndTenantId(id, branchTenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada o acceso denegado"));
            } finally {
                TenantContext.setTenantId(0L);
            }
        }
        return branchRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada o acceso denegado"));
    }

    @Transactional
    public Branch create(String name, String address, Double lat, Double lng,
                         Integer radiusMeters, Integer toleranceMinutes) {
        Long tenantId = TenantContext.getTenantId();
        boolean isSuperuserContext = (tenantId != null && tenantId == 0L);
        if (isSuperuserContext) {
            TenantContext.setTenantId(1L);
        }
        try {
            Branch branch = Branch.builder()
                    .name(name)
                    .address(address)
                    .latitude(lat)
                    .longitude(lng)
                    .radiusMeters(radiusMeters != null ? radiusMeters : 100)
                    .toleranceMinutes(toleranceMinutes != null ? toleranceMinutes : 10)
                    .active(true)
                    .build();
            if (isSuperuserContext) {
                branch.setTenantId(1L);
            }
            return branchRepository.save(branch);
        } finally {
            if (isSuperuserContext) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    @Transactional
    public Branch update(Long id, String name, String address, Double lat, Double lng,
                         Integer radiusMeters, Integer toleranceMinutes) {
        Long tenantId = TenantContext.getTenantId();
        boolean isSuperuserContext = (tenantId != null && tenantId == 0L);
        if (isSuperuserContext) {
            Long branchTenantId = tenantSettingsService.findBranchTenantIdForSuperuser(id);
            if (branchTenantId == null) {
                throw new ResourceNotFoundException("Sucursal no encontrada o acceso denegado");
            }
            TenantContext.setTenantId(branchTenantId);
        }
        try {
            Long effectiveTenantId = TenantContext.getTenantId();
            Branch branch = branchRepository.findByIdAndTenantId(id, effectiveTenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada o acceso denegado"));
            if (name != null) branch.setName(name);
            if (address != null) branch.setAddress(address);
            if (lat != null) branch.setLatitude(lat);
            if (lng != null) branch.setLongitude(lng);
            if (radiusMeters != null) branch.setRadiusMeters(radiusMeters);
            if (toleranceMinutes != null) branch.setToleranceMinutes(toleranceMinutes);
            return branchRepository.save(branch);
        } finally {
            if (isSuperuserContext) {
                TenantContext.setTenantId(0L);
            }
        }
    }

    @Transactional
    public void deactivate(Long id) {
        Long tenantId = TenantContext.getTenantId();
        boolean isSuperuserContext = (tenantId != null && tenantId == 0L);
        if (isSuperuserContext) {
            Long branchTenantId = tenantSettingsService.findBranchTenantIdForSuperuser(id);
            if (branchTenantId == null) {
                throw new ResourceNotFoundException("Sucursal no encontrada o acceso denegado");
            }
            TenantContext.setTenantId(branchTenantId);
        }
        try {
            Long effectiveTenantId = TenantContext.getTenantId();
            Branch branch = branchRepository.findByIdAndTenantId(id, effectiveTenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada o acceso denegado"));
            branch.setActive(false);
            branchRepository.save(branch);
        } finally {
            if (isSuperuserContext) {
                TenantContext.setTenantId(0L);
            }
        }
    }
}
