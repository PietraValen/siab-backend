#!/bin/bash
# Prepara sessões do Claude Code na nuvem para rodar `mvn test` completo:
# JDK 25 (o pom exige Java 25) e as libs nativas de GTK/OpenGL que o
# OpenCV do javacv-platform carrega (sem elas, SiabBackendApplicationTests
# e SegmentationServiceTest falham com "libgtk-x11-2.0.so.0" ausente — ver
# CLAUDE.md). Só roda em sessões remotas; na máquina de cada um, nada muda.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PACOTES="openjdk-25-jdk-headless libgtk2.0-0t64 libcanberra-gtk-module libgl1 libglib2.0-0t64"

if ! dpkg -s $PACOTES >/dev/null 2>&1; then
  apt-get update -q >/dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q $PACOTES >/dev/null
fi

JDK25=/usr/lib/jvm/java-25-openjdk-amd64
if [ -n "${CLAUDE_ENV_FILE:-}" ] && [ -d "$JDK25" ]; then
  echo "export JAVA_HOME=$JDK25" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=$JDK25/bin:\$PATH" >> "$CLAUDE_ENV_FILE"
fi

# Baixa as dependências do Maven antecipadamente (idempotente).
JAVA_HOME=$JDK25 mvn -q -B dependency:go-offline -f "$CLAUDE_PROJECT_DIR/pom.xml" >/dev/null 2>&1 || true
