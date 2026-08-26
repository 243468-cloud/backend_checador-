# ════════════════════════════════════════════════════════════════════════════
# Script de arranque del backend (PowerShell/Windows)
# Carga variables del .env antes de lanzar Spring Boot con Maven
# ════════════════════════════════════════════════════════════════════════════
# Uso: .\start-backend.ps1

$envFile = Join-Path $PSScriptRoot "..\.env"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^([^#\s][^=]*)=(.*)$') {
            $key   = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            Write-Host "  ✓ $key cargada" -ForegroundColor Green
        }
    }
    Write-Host ""
    Write-Host "▶ Variables de entorno cargadas desde .env" -ForegroundColor Cyan
} else {
    Write-Host "⚠ No se encontró .env — Copia .env.example como .env y complétalo" -ForegroundColor Yellow
    exit 1
}

Write-Host "▶ Iniciando Spring Boot..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
mvn spring-boot:run -q
