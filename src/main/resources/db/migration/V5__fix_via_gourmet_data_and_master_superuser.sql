-- ============================================================================
-- V5__fix_via_gourmet_data_and_master_superuser.sql
-- 1. Asegura que todos los usuarios, sucursales, asistencias, horarios y permisos
--    de Via Gourmet estén asignados a tenant_id = 1 y su sucursal principal.
-- 2. Configura a Humberto (humberto@viagourmet.com / username 'humberto') como
--    ADMIN de Via Gourmet (tenant_id = 1) para que vea todos sus empleados,
--    horarios y reportes de asistencia.
-- 3. Configura exclusivamente a Marco Antonio Lequin (admin@master.com) como el
--    único SUPERUSER Maestro (tenant_id = 0).
-- 4. Asegura que Via Gourmet (tenant_id = 1) tenga su configuración en
--    tenant_settings y todos los módulos ('schedules', 'lateArrivals', 'reports', 'rankings')
--    activos en tenant_modules.
-- ============================================================================

DROP PROCEDURE IF EXISTS fix_via_gourmet_data_and_master_superuser;

DELIMITER $$
CREATE PROCEDURE fix_via_gourmet_data_and_master_superuser()
BEGIN
    DECLARE v_has_branches INT DEFAULT 0;
    DECLARE v_has_email INT DEFAULT 0;
    DECLARE v_via_branch_id BIGINT DEFAULT NULL;

    SELECT COUNT(*) INTO v_has_branches
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'branches';

    SELECT COUNT(*) INTO v_has_email
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email';

    -- 1. Asegurar que la sucursal principal (Via Gourmet) pertenezca a tenant_id = 1
    IF v_has_branches > 0 THEN
        UPDATE branches SET tenant_id = 1 WHERE id = 1 OR tenant_id = 0;
        SELECT id INTO v_via_branch_id FROM branches WHERE tenant_id = 1 ORDER BY id ASC LIMIT 1;
    END IF;

    -- 2. Configurar a Humberto como ADMIN de Via Gourmet (tenant_id = 1) y a Marco Antonio Lequin como SUPERUSER Maestro (tenant_id = 0)
    IF v_has_email > 0 THEN
        UPDATE users
        SET role = 'ADMIN',
            tenant_id = 1,
            branch_id = COALESCE(branch_id, v_via_branch_id)
        WHERE (LOWER(username) = 'humberto' OR LOWER(email) = 'humberto@viagourmet.com' OR (id = 1 AND v_via_branch_id IS NOT NULL))
          AND LOWER(COALESCE(email, '')) != 'admin@master.com';

        -- Asegurar que Marco Antonio Lequin (admin@master.com) sea SUPERUSER Maestro (tenant_id = 0)
        UPDATE users
        SET role = 'SUPERUSER',
            tenant_id = 0,
            branch_id = NULL
        WHERE LOWER(email) = 'admin@master.com' OR LOWER(full_name) LIKE '%marco antonio lequin%';
    END IF;

    -- 3. Asegurar que los empleados de Via Gourmet (tenant_id = 0 o 1) tengan tenant_id = 1 y branch_id asignado
    IF v_has_branches > 0 THEN
        UPDATE users
        SET tenant_id = 1,
            branch_id = COALESCE(branch_id, v_via_branch_id)
        WHERE role = 'EMPLOYEE' AND (tenant_id = 0 OR tenant_id = 1);
    END IF;

    -- 4. Asegurar que asistencias, horarios y permisos con tenant_id = 0 pasen a tenant_id = 1
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'attendances') THEN
        UPDATE attendances SET tenant_id = 1 WHERE tenant_id = 0;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'schedule_rosters') THEN
        UPDATE schedule_rosters SET tenant_id = 1 WHERE tenant_id = 0;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'leave_requests') THEN
        UPDATE leave_requests SET tenant_id = 1 WHERE tenant_id = 0;
    END IF;

    -- 5. Si existen datos de Via Gourmet (tenant_id = 1), asegurar su fila en tenant_settings y sus 4 módulos en tenant_modules
    IF EXISTS (SELECT 1 FROM users WHERE tenant_id = 1) THEN
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'tenant_settings') THEN
            UPDATE tenant_settings SET company_name = 'Via Gourmet', slug = 'via-gourmet' WHERE tenant_id = 1;

            INSERT INTO tenant_settings (tenant_id, company_name, slug, primary_color, tolerance_minutes, subscription_status, created_at)
            SELECT 1, 'Via Gourmet', 'via-gourmet', '#e11d48', 10, 'ACTIVE', NOW()
            FROM DUAL
            WHERE NOT EXISTS (SELECT 1 FROM tenant_settings WHERE tenant_id = 1);
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'tenant_modules') THEN
            INSERT INTO tenant_modules (tenant_id, module_name)
            SELECT 1, m.module_name
            FROM (
                SELECT 'schedules' AS module_name UNION ALL
                SELECT 'lateArrivals' UNION ALL
                SELECT 'reports' UNION ALL
                SELECT 'rankings'
            ) m
            WHERE NOT EXISTS (
                SELECT 1 FROM tenant_modules tm WHERE tm.tenant_id = 1 AND tm.module_name = m.module_name
            );
        END IF;
    END IF;

    -- 6. Separar Miel de la Selva Maya (tenant_id = 2) para Magdalena Sánchez si existe en users
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

CALL fix_via_gourmet_data_and_master_superuser();
DROP PROCEDURE IF EXISTS fix_via_gourmet_data_and_master_superuser;
