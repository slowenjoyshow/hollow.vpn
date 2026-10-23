#!/usr/bin/env bash
# Скачивает нативные ядра: hev-socks5-tunnel (AAR) и Xray-core (бинарник + geo-базы).
# Нужны curl и unzip. Запускать из корня проекта.
set -euo pipefail
HEV_TAG="${HEV_TAG:-}"   # пусто = последний релиз (AAR появился только в 2.18.0)

asset_url() {  # $1 = api url, $2 = regex имени файла
  curl -fsSL "$1" | grep -o "\"browser_download_url\": *\"[^\"]*$2\"" | head -1 | cut -d'"' -f4 || true
}

mkdir -p app/libs app/src/main/assets/xray
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

echo "== hev-socks5-tunnel ${HEV_TAG:-latest}"
if [ -n "$HEV_TAG" ]; then HEV_API="https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/tags/${HEV_TAG}"; else HEV_API="https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/latest"; fi
URL="$(asset_url "$HEV_API" '[^"/]*\.aar')"
[ -n "$URL" ] || { echo "В релизе hev-socks5-tunnel нет .aar (он есть с версии 2.18.0): положи hev-socks5-tunnel.aar в app/libs вручную." >&2; exit 1; }
curl -fL -o app/libs/hev-socks5-tunnel.aar "$URL"
# Если JNI-класс уже собран внутрь AAR, наш шим-дубликат не нужен (иначе «defined multiple times»).
if unzip -p app/libs/hev-socks5-tunnel.aar classes.jar > "$TMP/hev-classes.jar" 2>/dev/null \
   && unzip -l "$TMP/hev-classes.jar" | grep -q 'hev/htproxy/TProxyService.class'; then
  echo "  JNI-шим уже внутри AAR, удаляю app/src/main/java/hev/htproxy/TProxyService.kt"
  rm -f app/src/main/java/hev/htproxy/TProxyService.kt
fi

echo "== Xray-core (последний релиз)"
API="https://api.github.com/repos/XTLS/Xray-core/releases/latest"
fetch_xray() {  # $1 = имя ассета без .zip, $2 = abi-папка
  local url; url="$(asset_url "$API" "[^\"/]*$1\\.zip")"
  if [ -z "$url" ]; then echo "  нет ассета $1.zip, пропускаю $2" >&2; return 1; fi
  curl -fL -o "$TMP/$1.zip" "$url"
  mkdir -p "app/src/main/jniLibs/$2"
  unzip -o -j "$TMP/$1.zip" xray -d "$TMP/$2" >/dev/null
  cp "$TMP/$2/xray" "app/src/main/jniLibs/$2/libxray.so"
  unzip -o -j "$TMP/$1.zip" geoip.dat geosite.dat -d app/src/main/assets/xray >/dev/null || true
  echo "  $2 ok"
}
fetch_xray "Xray-android-arm64-v8a" "arm64-v8a" || { echo "Без arm64 Xray работать не будет" >&2; exit 1; }
fetch_xray "Xray-android-amd64" "x86_64" || true

ls -la app/libs app/src/main/jniLibs/* app/src/main/assets/xray
