CREATE TABLE IF NOT EXISTS tenant_modules (
    tenant_id BIGINT NOT NULL,
    module_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (tenant_id, module_name)
);

INSERT INTO tenant_modules (tenant_id, module_name)
SELECT DISTINCT t.tenant_id, m.module_name
FROM users t
CROSS JOIN (
    SELECT 'schedules' AS module_name UNION ALL
    SELECT 'lateArrivals' UNION ALL
    SELECT 'reports' UNION ALL
    SELECT 'rankings'
) m
WHERE t.tenant_id > 0 AND NOT EXISTS (
    SELECT 1 FROM tenant_modules tm 
    WHERE tm.tenant_id = t.tenant_id AND tm.module_name = m.module_name
);
