-- ============================================================================
-- V7__expand_logo_and_icon_columns_for_base64.sql
-- Amplía las columnas logo_url e icon_url de tenant_settings a LONGTEXT para
-- permitir guardar imágenes en Base64 (data:image/...;base64,...) subidas
-- directamente desde celular o laptop.
-- ============================================================================

DROP PROCEDURE IF EXISTS expand_logo_and_icon_columns_for_base64;

DELIMITER $$
CREATE PROCEDURE expand_logo_and_icon_columns_for_base64()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_settings' AND column_name = 'logo_url') THEN
        ALTER TABLE tenant_settings MODIFY COLUMN logo_url LONGTEXT NULL;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tenant_settings' AND column_name = 'icon_url') THEN
        ALTER TABLE tenant_settings MODIFY COLUMN icon_url LONGTEXT NULL;
    END IF;
END$$
DELIMITER ;

CALL expand_logo_and_icon_columns_for_base64();
DROP PROCEDURE IF EXISTS expand_logo_and_icon_columns_for_base64;

