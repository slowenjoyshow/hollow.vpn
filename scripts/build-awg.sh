#!/usr/bin/env bash
# Собирает официальный amneziawg-android (поддерживает AmneziaWG 2.0: H1-H4 диапазонами, S3/S4, I1-I5)
# и кладёт AAR в app/libs/amneziawg-tunnel.aar. Запускать из корня проекта.
# Нужны: git, JDK 17+, Android SDK (NDK gradle докачает сам), make, flock, интернет (качается Go-тулчейн).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."   # корень проекта, даже если скрипт запущен из scripts/
TAG="${AWG_TAG:-v3.1.20260814}"
ROOT="$(pwd)"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT

git clone --depth 1 --branch "$TAG" --recurse-submodules --shallow-submodules \
  https://github.com/amnezia-vpn/amneziawg-android "$WORK/awg"
cd "$WORK/awg"
[ -f "$ROOT/local.properties" ] && cp "$ROOT/local.properties" . || true

./gradlew --no-daemon :tunnel:assembleRelease

AAR="$(find . -path '*build/outputs/aar/*' -name '*.aar' | grep -i release | head -1 || true)"
[ -n "$AAR" ] || AAR="$(find . -path '*build/outputs/aar/*' -name '*.aar' | head -1 || true)"
[ -n "$AAR" ] || { echo "AAR не найден: смотри вывод gradle выше" >&2; exit 1; }
mkdir -p "$ROOT/app/libs"
cp "$AAR" "$ROOT/app/libs/amneziawg-tunnel.aar"
ls -la "$ROOT/app/libs"
