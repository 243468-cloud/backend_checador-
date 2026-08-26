package com.checador.service;

import org.springframework.stereotype.Service;

@Service
public class GeoService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * Calcula la distancia entre dos coordenadas usando la fórmula de Haversine.
     * @return Distancia en metros
     */
    public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Verifica si una coordenada está dentro del radio de la sucursal.
     */
    public boolean isWithinRadius(double branchLat, double branchLon,
                                   double userLat, double userLon, int radiusMeters) {
        return calculateDistance(branchLat, branchLon, userLat, userLon) <= radiusMeters;
    }
}
