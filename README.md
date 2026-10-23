# hollow.vpn

Android VPN-клиент: Kotlin + Jetpack Compose, Material 3 (с dynamic color) и стеклянный «liquid glass» UI.
Протоколы: **VLESS (в т.ч. Reality)**, **VMess**, **Xray (свой JSON)**, **AmneziaWG**, **SOCKS5**.

## Что уже готово

- UI: три экрана (Главная / Профили / Настройки), анимированный aurora-фон, liquid glass панели, светящаяся кнопка подключения, плавные переходы
- Импорт: вставка ссылки или конфига, **подписка по `https://`** (base64 и обычный список), **импорт из файла** (`.conf`, `.json`, `.txt`)
- Парсинг `vless://…`, `vmess://…`, `socks5://…`, JSON Xray и конфигов AmneziaWG (`[Interface]`/`[Peer]`), в том числе несколько профилей за раз
- Генерация конфига Xray для VLESS/VMess (tcp / ws / grpc / h2 / httpupgrade / xhttp, TLS и Reality)
- Настройки: тема (авто/тёмная/светлая), акцентный цвет / Material You, размытие и тонировка стекла, скорость анимаций и отключение живого фона
- `VpnService` (foreground), хранение профилей и настроек в DataStore, состояние подключения, таймер и счётчики трафика

## Что нового в 0.5.0

- Название hollow.vpn, иконка из вашей картинки, тема Hollow (чёрный и малиновый) по умолчанию.
- «Жидкое стекло» убрано: обычные карточки Material 3. Кастомизация: акцент, свой оттенок, AMOLED, скругление, фон.
- Блок «Соединение» убран: DNS, MTU, IPv6 и обход локальной сети приложение выбирает само.
- AmneziaWG 2.0 (диапазоны H1–H4, S3/S4, I1–I5): библиотека заменена на официальную, нужен `scripts/build-awg.sh`.
- Импорт: trojan://, ss://, JSON-подписки (массив конфигов), параметры VLESS `encryption` и XHTTP `mode/extra`.
  Неподдерживаемые протоколы (hysteria2, tuic, wireguard://) не теряются молча: приложение пишет, сколько пропущено.
- Серверы сгруппированы по подпискам (сворачиваются), у каждой подписки есть корзина.

## Что нового в 0.3.0

- **«app not supported» при импорте подписки**: панель отклоняла User-Agent `GlassVPN/0.2`. Теперь клиент представляется как v2rayNG/Happ/v2RayTun (выбор в Настройки → Подписки) и сам перебирает остальные, пока ответ не распарсится.
- Material You: свой оттенок (ползунок hue), чёрный AMOLED, скругление углов.
- Соединение: DNS, MTU, IPv6, обход локальной сети (без geoip.dat), policy-таймауты Xray.

## Нативные ядра: статус

| Протокол | Статус |
|---|---|
| SOCKS5 | работает через hev-socks5-tunnel (готовый AAR, `scripts/fetch-natives.sh`) |
| AmneziaWG (в т.ч. 2.0) | официальный `amnezia-vpn/amneziawg-android`, собирается `scripts/build-awg.sh` в `app/libs/amneziawg-tunnel.aar` (вызовы через рефлексию, см. `vpn/AwgBackend.kt`) |
| VLESS / VMess / Xray | официальный бинарник Xray-core запускается отдельным процессом (`XrayCore` в `vpn/Engines.kt`), трафик в него заворачивает hev-socks5-tunnel |

Перед локальной сборкой один раз выполни `./scripts/build-awg.sh` (AmneziaWG, долго: качается Go) и `./scripts/fetch-natives.sh` (в GitHub Actions это уже встроено): он скачивает hev-socks5-tunnel.aar и Xray-core для arm64-v8a и x86_64. Нужны `curl` и `unzip`.

## Что нужно подключить (нативные ядра) — устаревшее описание

Я не мог скачать и собрать нативные библиотеки в своей среде, поэтому в местах `TODO(native)` (файл `vpn/Engines.kt`) стоят заглушки.
Пока ядра не подключены, нажатие на кнопку покажет ошибку «Подключи …». Это ожидаемо.

| Протокол | Что подключить | Где править |
|---|---|---|
| VLESS / Xray | [XTLS/libXray](https://github.com/XTLS/libXray) → собрать `.aar` через gomobile, положить в `app/libs/` | `XrayEngine` |
| VLESS / Xray / SOCKS5 | [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (tun2socks, TUN fd → SOCKS5) | `TunBridge` |
| AmneziaWG | [amnezia-vpn/amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android) (модуль `tunnel`) | `AwgEngine` |

Затем раскомментируй зависимости в конце `app/build.gradle.kts` и замени `TODO(native)` реальными вызовами.
Сигнатуры API у этих библиотек меняются между релизами, поэтому в коде оставлены наброски, а не жёсткие вызовы: сверяйся с README выбранной версии.

Схема работы:

```
VLESS/Xray:  телефон → TUN → hev-socks5-tunnel → Xray (127.0.0.1:10808) → сервер
SOCKS5:      телефон → TUN → hev-socks5-tunnel → твой SOCKS5-прокси
AmneziaWG:   телефон → amneziawg-android (свой VpnService внутри библиотеки) → сервер
```

## Сборка

1. Открой папку `glassvpn` в Android Studio (Ladybug или новее), дождись Gradle Sync (wrapper создастся сам).
2. Проверь версию `dev.chrisbanes.haze` в `app/build.gradle.kts`: в Glass.kt используются `hazeSource` / `hazeEffect` (Haze 1.2+). При другой версии имена API могут отличаться.
3. Run на устройстве или эмуляторе (minSdk 26).

## Про «liquid glass»

Реализован на Haze: размытие фона под элементом + светлая подложка + градиентная кромка-блик.
Это приближение, а не настоящая рефракция, как в iOS 26. Настоящее преломление можно добавить через AGSL `RuntimeShader` (Android 13+), как следующий шаг.

## Ограничения

- Подписка загружается один раз при импорте; автообновления и хранения subscription URL пока нет
- Нет per-app split tunneling и правил маршрутизации — логичные следующие шаги
