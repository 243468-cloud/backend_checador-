package com.checador.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(name = "latitude", nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double latitude;

    @Column(name = "longitude", nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double longitude;

    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters = 100;

    @Column(name = "tolerance_minutes", nullable = false)
    private Integer toleranceMinutes = 10;

    @Column(nullable = false)
    private Boolean active = true;
}
