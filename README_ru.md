# ProxyBox

[![English](https://img.shields.io/badge/lang-English-blue)](README.md)

![API](https://img.shields.io/badge/API-24%2B-brightgreen)
![License](https://img.shields.io/badge/License-GPLv3-blue)
![GitHub release](https://img.shields.io/github/v/release/DaveBugg/ProxyBox)
![Downloads](https://img.shields.io/github/downloads/DaveBugg/ProxyBox/total)

Android VPN-клиент с открытым исходным кодом на базе [Xray-core](https://github.com/XTLS/Xray-core). Разработан для **смартфонов** и **Android TV / ТВ-приставок**.

> **Почему ProxyBox?** Полная поддержка Android TV с навигацией через пульт, передача конфигов с телефона на ТВ через QR-код и современный минималистичный интерфейс — то, чего нет в большинстве Xray-клиентов.

## Донат

Если проект оказался полезным, можете поддержать разработку:

**USDT (TRC20):** `TMWTigPZgTkekaRUjUrhJUNENFeUAE7T15`

<img src="docs/donate_usdt_trc20.png" alt="Donate USDT TRC20" width="200"/>

## Скриншоты

<p align="center">
  <img src="docs/Screenshot_20260409.jpg" alt="Мобильный интерфейс" width="250"/>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/Screenshot_20260322_171025.png" alt="Интерфейс Android TV" width="420"/>
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/Screenshot_20260322_171043.png" alt="Передача конфига с телефона на ТВ" width="420"/>
</p>

<p align="center">
  <em>Смартфон &nbsp;·&nbsp; Android TV &nbsp;·&nbsp; Передача конфига на ТВ</em>
</p>

## Возможности

- **Мульти-протокол**: VLESS, VMess, Shadowsocks, Trojan, Hysteria2
- **Транспорт**: TCP, WebSocket, gRPC, HTTP/2, QUIC, KCP, HTTPUpgrade, XHTTP (SplitHTTP)
- **Безопасность**: TLS, Reality, None
- **Оптимизация для Android TV**: отдельный ТВ-интерфейс с навигацией через D-pad и поддержкой Leanback лаунчера
- **Способы импорта конфигов**:
  - Вставить URL или JSON вручную
  - Сканировать QR-код камерой
  - Выбрать изображение QR-кода из галереи
  - Локальный HTTP-сервер с QR-кодом — сканируй с телефона, чтобы добавить конфиг на ТВ/приставку
  - Подписки (subscription URL) с автообновлением
- **Тестирование соединения**: TCP-пинг всех профилей + HTTP-тест (google.com/generate_204)
- **Автообновление гео-баз**: geoip.dat и geosite.dat из [v2fly](https://github.com/v2fly/geoip) через WorkManager (ежедневно, только Wi-Fi)
- **Автозапуск при загрузке**: автоматическое переподключение VPN после перезагрузки устройства
- **Локальное хранение**: все конфиги хранятся в Room-базе на устройстве
- **Импорт полного JSON**: принимает полные xray/v2ray конфиги с автоматическим сохранением зависимых outbound-ов (frag-proxy, WARP-цепочки через `dialerProxy` / `proxySettings.tag`)

## Безопасность и антидетект

ProxyBox усиливает локальный прокси-стек для минимизации поверхности атаки и снижения фингерпринтинга VPN. Проверено с помощью [RKN Hardering](https://github.com/xtclovver/RKNHardering):

- **Аутентификация локального SOCKS-прокси** — случайные логин/пароль генерируются при каждом подключении. Другие приложения не могут использовать или обнаружить прокси без знания пароля
- **Нет HTTP-прокси** — только SOCKS-inbound на localhost, без дополнительного HTTP-слушателя
- **Нет Xray gRPC/API** — stats и API inbound-ы не создаются
- **Полный туннель** — весь трафик идёт через VPN, предотвращая обход через прямое соединение
- **Нет в базах VPN-приложений** — имя пакета не определяется сервисами обнаружения VPN

> **Примечание:** Некоторые сигналы обнаружения, такие как `TRANSPORT_VPN`, интерфейс `tun0` и отсутствие `NET_CAPABILITY_NOT_VPN`, являются частью Android VPN API и не могут быть обойдены никаким userspace VPN-приложением.

## Архитектура

```
com.dave_cli.proxybox
├── core/
│   ├── CoreService.kt          # VPN-сервис (TUN-интерфейс + xray-движок)
│   ├── XrayManager.kt          # Жизненный цикл Xray (CoreController API)
│   ├── ConfigBuilder.kt        # Сборка JSON-конфига из профиля
│   ├── GeoFileManager.kt       # Загрузка geoip/geosite с ETag-кешированием
│   ├── GeoUpdateWorker.kt      # Периодическое обновление через WorkManager
│   ├── ProxyEngine.kt          # Интерфейс движка
│   └── BootReceiver.kt         # Автозапуск при загрузке
├── data/
│   ├── db/                     # Room-база (ProfileEntity, SubscriptionEntity)
│   └── repository/             # ProfileRepository (CRUD, пинг, подписки)
├── import_config/
│   ├── ConfigParser.kt         # Парсинг vless://, vmess://, ss://, trojan://, hy2://, JSON
│   ├── SubscriptionParser.kt   # Декодирование base64-подписок
│   └── QrDecoder.kt            # Декодирование QR-кодов из изображений
├── server/
│   ├── LocalConfigServer.kt    # NanoHTTPD-сервер для передачи конфигов на ТВ
│   └── QrGenerator.kt          # Генерация QR-кодов
├── ui/
│   ├── main/                   # Мобильный UI (MainActivity, ProfileAdapter)
│   ├── tv/                     # Android TV UI (TvMainActivity, TvProfileAdapter)
│   ├── add/                    # Экран добавления профиля
│   └── server/                 # Экран локального сервера с QR
└── ProxyBoxApp.kt              # Application-класс (планирует обновление гео)
```

## Требования

- Android 7.0+ (API 24)
- Xray-ядро через [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) (`libv2ray.aar`)

## Сборка

```bash
# Клонировать репозиторий
git clone https://github.com/DaveBugg/ProxyBox.git
cd ProxyBox

# Собрать debug APK
./gradlew assembleDebug

# APK будет здесь:
# app/build/outputs/apk/debug/app-debug.apk
```

> **Примечание**: Проект включает `libv2ray.aar` в `app/libs/`. Он содержит Xray-ядро, скомпилированное для armeabi-v7a, arm64-v8a, x86 и x86_64.

## Добавление конфигов

### На телефоне
1. Открой приложение → нажми **+ Add**
2. Вставь URL конфига (например `vless://...`) или полный JSON
3. Или отсканируй QR-код камерой / выбери изображение QR из галереи

### На Android TV / приставке
1. Открой приложение → выбери **Add Config via Phone**
2. Отсканируй QR-код на экране с телефона
3. В браузере откроется страница → вставь URL, JSON, загрузи QR-изображение или добавь подписку

### Подписки
Добавь URL подписки на экране добавления конфига или через веб-страницу локального сервера. Профили загружаются и сохраняются автоматически.

## Технологии

- **Язык**: Kotlin
- **VPN-ядро**: Xray (через libv2ray.aar, CoreController API)
- **База данных**: Room
- **Асинхронность**: Kotlin Coroutines + StateFlow
- **Сеть**: OkHttp, NanoHTTPD
- **QR**: ZXing
- **Фоновые задачи**: WorkManager
- **ТВ**: AndroidX Leanback

## Лицензия

Проект лицензирован под GNU General Public License v3.0. Подробности в файле [LICENSE](LICENSE).

## Благодарности

- [Xray-core](https://github.com/XTLS/Xray-core) — прокси-движок
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) — Android-обвязка
- [v2rayNG](https://github.com/2dust/v2rayNG) — референсная реализация
- [v2fly/geoip](https://github.com/v2fly/geoip) — гео-базы

## Отказ от ответственности

Данный проект создан исключительно в образовательных и исследовательских целях. Автор не несёт ответственности за использование данного программного обеспечения. Пользователи самостоятельно несут полную ответственность за соблюдение применимого законодательства в своей юрисдикции.
