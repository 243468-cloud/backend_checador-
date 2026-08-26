#!/bin/bash
# ════════════════════════════════════════════════════════════════════════════
# Script de arranque del backend — Carga variables del .env antes de Maven
# ════════════════════════════════════════════════════════════════════════════
# Uso: ./start-backend.sh

set -a
source "$(dirname "$0")/../.env"
set +a

cd "$(dirname "$0")"
mvn spring-boot:run -q
