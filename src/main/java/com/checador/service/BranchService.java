package com.checador.service;

import com.checador.entity.Branch;
import com.checador.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<Branch> getAll() {
        return branchRepository.findByActive(true);
    }

    public Branch findById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
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
        Branch branch = findById(id);
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
        Branch branch = findById(id);
        branch.setActive(false);
        branchRepository.save(branch);
    }
}
