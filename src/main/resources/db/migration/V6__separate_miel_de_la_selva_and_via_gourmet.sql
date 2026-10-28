-- ============================================================================
-- V6__separate_miel_de_la_selva_and_via_gourmet.sql
-- Separa las empresas Via Gourmet (tenant_id = 1) y Miel de la Selva Maya (tenant_id = 2)
-- y asigna a Magdalena Sánchez (galeriemaya@hotmail.com) a Miel de la Selva Maya.
-- ============================================================================

DROP PROCEDURE IF EXISTS separate_miel_de_la_selva_and_via_gourmet;

DELIMITER $$
CREATE PROCEDURE separate_miel_de_la_selva_and_via_gourmet()
BEGIN
    DECLARE v_has_branches INT DEFAULT 0;
    DECLARE v_has_email INT DEFAULT 0;

    SELECT COUNT(*) INTO v_has_branches
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'branches';

    SELECT COUNT(*) INTO v_has_email
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email';

    -- 1. Asegurar que tenant_id = 1 tenga el nombre y slug de Via Gourmet si existe
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'tenant_settings') THEN
        UPDATE tenant_settings
        SET company_name = 'Via Gourmet',
            slug = 'via-gourmet'
        WHERE tenant_id = 1;
    END IF;

    -- 2. Separar Miel de la Selva Maya (tenant_id = 2) para Magdalena Sánchez si existe en users
    IF v_has_email > 0 AND EXISTS (SELECT 1 FROM users WHERE LOWER(email) = 'galeriemaya@hotmail.com' OR id = 20) THEN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'tenant_settings') THEN
            INSERT INTO tenant_settings (tenant_id, company_name, slug, primary_color, tolerance_minutes, subscription_status, created_at)
            SELECT 2, 'Miel de la Selva Maya', 'miel-de-la-selva-maya', '#d97706', 10, 'ACTIVE', NOW()
            FROM DUAL
            WHERE NOT EXISTS (SELECT 1 FROM tenant_settings WHERE tenant_id = 2);
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'tenant_modules') THEN
            INSERT INTO tenant_modules (tenant_id, module_name)
            SELECT 2, m.module_name
            FROM (
                SELECT 'schedules' AS module_name UNION ALL
                SELECT 'lateArrivals' UNION ALL
                SELECT 'reports' UNION ALL
                SELECT 'rankings'
            ) m
            WHERE NOT EXISTS (
                SELECT 1 FROM tenant_modules tm WHERE tm.tenant_id = 2 AND tm.module_name = m.module_name
            );
        END IF;

        IF v_has_branches > 0 THEN
            INSERT INTO branches (tenant_id, name, address, latitude, longitude, radius_meters, tolerance_minutes, active, created_at)
            SELECT 2, 'Miel de la Selva Maya', 'Sucursal Principal', 19.4326077, -99.1332080, 100, 10, 1, NOW()
            FROM DUAL
            WHERE NOT EXISTS (SELECT 1 FROM branches WHERE tenant_id = 2);
        END IF;

        UPDATE users
        SET role = 'ADMIN',
            tenant_id = 2,
            branch_id = (SELECT id FROM branches WHERE tenant_id = 2 ORDER BY id ASC LIMIT 1),
            active = 1
        WHERE LOWER(email) = 'galeriemaya@hotmail.com' OR id = 20;
    END IF;
END$$
DELIMITER ;

CALL separate_miel_de_la_selva_and_via_gourmet();
DROP PROCEDURE IF EXISTS separate_miel_de_la_selva_and_via_gourmet;
