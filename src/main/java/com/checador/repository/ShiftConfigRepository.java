package com.checador.repository;

import com.checador.entity.ShiftConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftConfigRepository extends JpaRepository<ShiftConfig, Long> {
    Optional<ShiftConfig> findByShiftName(String shiftName);
}
