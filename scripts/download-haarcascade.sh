#!/usr/bin/env bash
# Baixa o haarcascade_frontalface_default.xml necessário para a Fase 3
# (Segmentação) e coloca no lugar certo dentro de resources.
set -euo pipefail

DEST_DIR="$(dirname "$0")/../src/main/resources/haarcascades"
DEST_FILE="$DEST_DIR/haarcascade_frontalface_default.xml"
URL="https://raw.githubusercontent.com/opencv/opencv/4.x/data/haarcascades/haarcascade_frontalface_default.xml"

mkdir -p "$DEST_DIR"

if [ -f "$DEST_FILE" ]; then
  echo "Já existe: $DEST_FILE (nada a fazer)"
  exit 0
fi

echo "Baixando haarcascade_frontalface_default.xml..."
curl -fsSL -o "$DEST_FILE" "$URL"
echo "OK: salvo em $DEST_FILE"
