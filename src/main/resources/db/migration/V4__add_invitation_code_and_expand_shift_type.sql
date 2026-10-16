-- ============================================================================
-- V4__add_invitation_code_and_expand_shift_type.sql
-- 1. Amplía la columna shift_type a VARCHAR(50) de manera idempotente
-- 2. Agrega las columnas de código de invitación a tenant_settings de manera idempotente
-- ============================================================================

-- 1. Ampliar shift_type solo en las tablas donde la columna exista
DROP PROCEDURE IF EXISTS expand_shift_type_if_exists;
DELIMITER $$
CREATE PROCEDURE expand_shift_type_if_exists()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'shift_type'
    ) THEN
        ALTER TABLE users MODIFY COLUMN shift_type VARCHAR(50) NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'attendances' AND column_name = 'shift_type'
    ) THEN
        ALTER TABLE attendances MODIFY COLUMN shift_type VARCHAR(50) NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'schedule_rosters' AND column_name = 'shift_type'
    ) THEN
        ALTER TABLE schedule_rosters MODIFY COLUMN shift_type VARCHAR(50) NULL;
    END IF;
END$$
DELIMITER ;

CALL expand_shift_type_if_exists();
DROP PROCEDURE IF EXISTS expand_shift_type_if_exists;

-- 2. Asegurar que las columnas invitation_code_hash e invitation_expires_at existan en tenant_settings
DROP PROCEDURE IF EXISTS add_invitation_columns_to_tenant_settings;

DELIMITER $$
CREATE PROCEDURE add_invitation_columns_to_tenant_settings()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'tenant_settings'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tenant_settings'
              AND column_name = 'invitation_code_hash'
        ) THEN
            ALTER TABLE tenant_settings ADD COLUMN invitation_code_hash VARCHAR(255) NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tenant_settings'
              AND column_name = 'invitation_expires_at'
        ) THEN
            ALTER TABLE tenant_settings ADD COLUMN invitation_expires_at DATETIME(6) NULL;
        END IF;
    END IF;
END$$
DELIMITER ;

CALL add_invitation_columns_to_tenant_settings();
DROP PROCEDURE IF EXISTS add_invitation_columns_to_tenant_settings;
