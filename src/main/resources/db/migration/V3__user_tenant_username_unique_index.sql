-- ============================================================================
-- V3__user_tenant_username_unique_index.sql
-- Elimina el índice único global sobre users.username y crea el índice
-- compuesto uk_users_tenant_username(tenant_id, username) para soportar
-- usuarios con el mismo username en distintas empresas (multi-tenancy).
-- ============================================================================

-- 1. Asegurar que la columna tenant_id exista en la tabla users
DROP PROCEDURE IF EXISTS ensure_tenant_id_in_users;

DELIMITER $$
CREATE PROCEDURE ensure_tenant_id_in_users()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE users ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
    END IF;
END$$
DELIMITER ;

CALL ensure_tenant_id_in_users();
DROP PROCEDURE IF EXISTS ensure_tenant_id_in_users;

-- 2. Eliminar cualquier índice único previo sobre users.username que no sea uk_users_tenant_username
DROP PROCEDURE IF EXISTS drop_old_username_unique_index;

DELIMITER $$
CREATE PROCEDURE drop_old_username_unique_index()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE idx_name VARCHAR(128);
    DECLARE cur CURSOR FOR
        SELECT DISTINCT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'username'
          AND non_unique = 0
          AND index_name != 'PRIMARY'
          AND index_name != 'uk_users_tenant_username';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO idx_name;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @drop_sql = CONCAT('ALTER TABLE users DROP INDEX `', idx_name, '`');
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL drop_old_username_unique_index();
DROP PROCEDURE IF EXISTS drop_old_username_unique_index;

-- 3. Crear el índice único compuesto por tenant_id y username
DROP PROCEDURE IF EXISTS create_uk_users_tenant_username;

DELIMITER $$
CREATE PROCEDURE create_uk_users_tenant_username()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND index_name = 'uk_users_tenant_username'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_tenant_username UNIQUE (tenant_id, username);
    END IF;
END$$
DELIMITER ;

CALL create_uk_users_tenant_username();
DROP PROCEDURE IF EXISTS create_uk_users_tenant_username;
