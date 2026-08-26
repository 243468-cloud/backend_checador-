package com.checador.entity;

public enum AttendanceStatus {
    ON_TIME,    // Puntual
    LATE,       // Tardanza
    ABSENT,     // Falta
    IN_SHIFT,   // En turno (sin salida registrada)
    EXCUSED     // Justificado
}
