<div align="center">

# hollow.vpn

**Android-клиент для VLESS, VMess, Trojan, Shadowsocks, SOCKS5 и AmneziaWG.**
Material You, подписки, пинг серверов и плитка в шторке.

![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

</div>

<p align="center">
  <img src="docs/home.png" width="23%" alt="Главный экран" />
  <img src="docs/stats.png" width="23%" alt="Статистика трафика" />
  <img src="docs/servers.png" width="23%" alt="Список серверов" />
  <img src="docs/settings.png" width="23%" alt="Настройки" />
</p>

## О проекте

hollow.vpn: приложение, в котором не нужно разбираться в конфигах. Вставляешь ссылку, подписку или файл, выбираешь сервер с лучшим пингом и нажимаешь одну кнопку.

Внутри работают проверенные ядра: официальный [Xray-core](https://github.com/XTLS/Xray-core), [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) и [amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android). Само приложение отвечает за импорт, интерфейс и управление туннелем.

## Возможности

**Протоколы**

| Протокол | Детали |
|---|---|
| VLESS | в том числе Reality; транспорты tcp, ws, grpc, h2, httpupgrade, xhttp; TLS |
| VMess | те же транспорты |
| Trojan | импорт по `trojan://` |
| Shadowsocks | импорт по `ss://` |
| SOCKS5 | напрямую через свой прокси |
| AmneziaWG | включая 2.0 (диапазоны H1–H4, S3/S4, I1–I5) |
| Xray JSON | свой конфиг целиком |

**Импорт**

- Ссылки `vless://`, `vmess://`, `trojan://`, `ss://`, `socks5://`
- Конфиги Xray (JSON) и AmneziaWG (`[Interface]` / `[Peer]`)
- Ключи и файлы AmneziaVPN (`vpn://`, `.vpn`) для контейнеров AmneziaWG/WireGuard и Xray
- Подписки по `https://`: base64, обычный список и JSON-массив конфигов
- Файлы `.conf`, `.json`, `.txt`
- Несколько профилей за один импорт
- Если в подписке есть неподдерживаемые протоколы (hysteria2, tuic, wireguard:// и другие), приложение скажет, сколько записей пропущено, а не потеряет их молча

**Подписки**

- Серверы сгруппированы по подпискам, группы сворачиваются
- Автообновление по интервалу, который задаёт панель (заголовок `profile-update-interval`)
- Если панель отклоняет приложение («app not supported»), можно выбрать, кем представляться: v2rayNG, Happ или v2RayTun. Клиент также сам перебирает варианты, пока ответ не распарсится

**Серверы и подключение**

- TCP-пинг до каждого сервера, сортировка по пингу, кнопка «Лучший сервер», автопроверка при открытии списка (для AmneziaWG по UDP пинг недоступен)
- Задержка через туннель: реальный запрос в интернет через выбранный сервер
- Таймер сессии, счётчики трафика и график скорости за минуту
- Плитка быстрых настроек: включить и выключить VPN из шторки
- Приложение само выбирает DNS, MTU, IPv6 и обход локальной сети

**Оформление**

- Material You: цвета из обоев (Android 12+) или готовые палитры
- Тема: авто, тёмная, светлая; режим AMOLED
- Свой оттенок, скругление углов, размер текста
- Фирменная тема Hollow (чёрный и малиновый)
- Анимации и фон можно отключить, вибрация настраивается
- Предиктивный жест «назад»

## Как это работает

```
VLESS / VMess / Trojan / SS / Xray:
  телефон → TUN → hev-socks5-tunnel → Xray (127.0.0.1:10808) → сервер

SOCKS5:
  телефон → TUN → hev-socks5-tunnel → твой SOCKS5-прокси

AmneziaWG:
  телефон → amneziawg-android (свой VpnService внутри библиотеки) → сервер
```

Xray запускается отдельным процессом из официального бинарника. Это исключает конфликт Go-рантаймов с AmneziaWG. Сам клиент исключён из туннеля, поэтому его собственные запросы (подписки, пинг) идут напрямую.

## Сборка

Нативные ядра в репозиторий не входят, их нужно получить скриптами.

**Требования:** Android Studio Ladybug или новее, JDK 17, Android SDK, `curl`, `unzip`. Для AmneziaWG дополнительно `git`, `make`, `flock` и интернет (скачивается Go-тулчейн).

```bash
git clone https://github.com/<your-username>/hollow.vpn.git
cd hollow.vpn

# 1. hev-socks5-tunnel (AAR) и Xray-core для arm64-v8a и x86_64
./scripts/fetch-natives.sh

# 2. AmneziaWG: собирается долго, нужен один раз
./scripts/build-awg.sh
```

После этого откройте проект в Android Studio, дождитесь Gradle Sync и запустите на устройстве или эмуляторе.

Что делает каждый скрипт:

| Скрипт | Результат |
|---|---|
| `scripts/fetch-natives.sh` | `app/libs/hev-socks5-tunnel.aar`, `app/src/main/jniLibs/<abi>/libxray.so`, `geoip.dat` и `geosite.dat` |
| `scripts/build-awg.sh` | `app/libs/amneziawg-tunnel.aar` (версию можно задать через `AWG_TAG`) |

Без `fetch-natives.sh` приложение соберётся, но подключиться не сможет: при нажатии на кнопку оно сообщит, что не найдены ядра. Без `build-awg.sh` не будет работать только AmneziaWG.

Поддерживаемые архитектуры: `arm64-v8a` и `x86_64`.

## Стек

- Kotlin 2.0, Jetpack Compose, Material 3
- Navigation Compose, ViewModel, Coroutines
- DataStore для профилей и настроек
- kotlinx.serialization
- minSdk 29, targetSdk 35

## Структура проекта

```
app/src/main/java/app/glassvpn/
├── model/    парсеры ссылок и подписок, импорт Amnezia, генерация конфига Xray, пинг, хранилище
├── ui/       экраны (Главная, Профили, Настройки), тема, компоненты
├── vpn/      VpnService, движки (Xray, AmneziaWG, SOCKS5), плитка быстрых настроек
└── MainActivity.kt
scripts/      сборка и загрузка нативных ядер
```

## Ограничения и планы

- Нет раздельного туннелирования по приложениям и правил маршрутизации
- Из ключей AmneziaVPN пока не импортируются контейнеры OpenVPN, Cloak и Shadowsocks
- Hysteria2, TUIC и «чистый» WireGuard не поддерживаются

## Конфиденциальность

Приложение запрашивает только разрешения, нужные VPN-клиенту: интернет, состояние сети, foreground-сервис и уведомления. Резервное копирование данных приложения отключено (`allowBackup=false`).

## Лицензия

Распространяется по лицензии [Apache 2.0](LICENSE).

## Благодарности

- [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [amnezia-vpn/amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android)
