package com.checador.service;

import com.checador.entity.ShiftConfig;
import com.checador.entity.ShiftType;
import com.checador.repository.ShiftConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftConfigService {

    private final ShiftConfigRepository shiftConfigRepository;

    @Transactional
    public List<ShiftConfig> getAllConfigs() {
        List<ShiftConfig> list = shiftConfigRepository.findAll();
        if (list.isEmpty()) {
            initDefaultConfigs();
            list = shiftConfigRepository.findAll();
        }
        return list;
    }

    @Transactional
    public void initDefaultConfigs() {
        if (shiftConfigRepository.count() == 0) {
            shiftConfigRepository.save(ShiftConfig.builder()
                    .shiftName("MORNING")
                    .label("Turno Matutino")
                    .startTime("07:00")
                    .endTime("15:00")
                    .daysDescription("Lunes a Sábado")
                    .build());

            shiftConfigRepository.save(ShiftConfig.builder()
                    .shiftName("EVENING")
                    .label("Turno Vespertino")
                    .startTime("14:00")
                    .endTime("22:00")
                    .daysDescription("Lunes a Sábado")
                    .build());

            shiftConfigRepository.save(ShiftConfig.builder()
                    .shiftName("SUNDAY")
                    .label("Turno Dominical")
                    .startTime("08:00")
                    .endTime("18:00")
                    .daysDescription("Solo Domingo (Entrada 8:00 AM para todos)")
                    .build());

            shiftConfigRepository.save(ShiftConfig.builder()
                    .shiftName("NOCTURNO")
                    .label("Turno Nocturno")
                    .startTime("22:00")
                    .endTime("06:00")
                    .daysDescription("Lunes a Sábado")
                    .build());

            shiftConfigRepository.save(ShiftConfig.builder()
                    .shiftName("MEDIO")
                    .label("Medio Turno")
                    .startTime("09:00")
                    .endTime("13:00")
                    .daysDescription("Horario Especial")
                    .build());
        }
    }

    @Transactional
    public List<ShiftConfig> updateConfigs(List<ShiftConfig> updatedList) {
        for (ShiftConfig item : updatedList) {
            if (item.getShiftName() != null) {
                ShiftConfig existing = shiftConfigRepository.findByShiftName(item.getShiftName())
                        .orElseGet(() -> ShiftConfig.builder().shiftName(item.getShiftName()).build());

                if (item.getLabel() != null) existing.setLabel(item.getLabel());
                if (item.getStartTime() != null) existing.setStartTime(item.getStartTime());
                if (item.getEndTime() != null) existing.setEndTime(item.getEndTime());
                if (item.getDaysDescription() != null) existing.setDaysDescription(item.getDaysDescription());

                shiftConfigRepository.save(existing);
            }
        }
        return getAllConfigs();
    }

    public LocalTime getShiftStartTime(ShiftType shiftType, boolean isSunday) {
        if (isSunday) {
            return getStartTimeByShiftName("SUNDAY", LocalTime.of(8, 0));
        }
        if (shiftType == null) {
            return getStartTimeByShiftName("MORNING", LocalTime.of(7, 0));
        }
        return switch (shiftType) {
            case MORNING -> getStartTimeByShiftName("MORNING", LocalTime.of(7, 0));
            case EVENING -> getStartTimeByShiftName("EVENING", LocalTime.of(14, 0));
            case SUNDAY  -> getStartTimeByShiftName("SUNDAY", LocalTime.of(8, 0));
            case MIXED   -> getStartTimeByShiftName("MEDIO", LocalTime.of(9, 0));
        };
    }

    public LocalTime getShiftEndTime(ShiftType shiftType, boolean isSunday) {
        if (isSunday) {
            return getEndTimeByShiftName("SUNDAY", LocalTime.of(18, 0));
        }
        if (shiftType == null) {
            return getEndTimeByShiftName("MORNING", LocalTime.of(15, 0));
        }
        return switch (shiftType) {
            case MORNING -> getEndTimeByShiftName("MORNING", LocalTime.of(15, 0));
            case EVENING -> getEndTimeByShiftName("EVENING", LocalTime.of(22, 0));
            case SUNDAY  -> getEndTimeByShiftName("SUNDAY", LocalTime.of(18, 0));
            case MIXED   -> getEndTimeByShiftName("MEDIO", LocalTime.of(13, 0));
        };
    }

    private LocalTime getStartTimeByShiftName(String key, LocalTime fallback) {
        try {
            return shiftConfigRepository.findByShiftName(key)
                    .map(c -> LocalTime.parse(c.getStartTime()))
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private LocalTime getEndTimeByShiftName(String key, LocalTime fallback) {
        try {
            return shiftConfigRepository.findByShiftName(key)
                    .map(c -> LocalTime.parse(c.getEndTime()))
                    .orElse(fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
