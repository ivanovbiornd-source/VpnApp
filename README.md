# VPN Shield — Android VPN Client

> **Demo-проект:** создан в рамках демонстрации возможностей нейросетей (Claude Sonnet 4.6).

---

## 📱 Возможности

| Функция | Статус |
|---|---|
| Большая кнопка включения VPN | ✅ |
| Минималистичный интерфейс | ✅ |
| Подписка серверов по JSON URL | ✅ |
| Автообновление подписки (WorkManager) | ✅ |
| Таймер ВКЛ/ВЫКЛ по расписанию | ✅ |
| GPS-автоподключение (зона «дом») | ✅ |
| Постоянное foreground-уведомление | ✅ |
| Запуск после перезагрузки | ✅ |

---

## 🏗 Архитектура

```
com.vpnapp/
├── model/
│   ├── VpnServer.kt          — датакласс сервера + SubscriptionConfig
│   ├── VpnState.kt           — enum состояний VPN
│   └── AppSettings.kt        — все настройки приложения
├── repository/
│   └── VpnRepository.kt      — загрузка серверов по URL + кэш
├── service/
│   ├── VpnService.kt         — AndroidVpnService (TUN-интерфейс)
│   ├── LocationService.kt    — ForegroundService с FusedLocationClient
│   ├── SubscriptionWorker.kt — PeriodicWorkRequest для автообновления
│   ├── AlarmReceiver.kt      — AlarmManager для таймера
│   └── BootReceiver.kt       — восстановление после перезагрузки
├── ui/
│   ├── SplashActivity.kt     — инициализация + SplashScreen API
│   ├── MainActivity.kt       — главный экран с кнопкой
│   ├── ServersActivity.kt    — список серверов + RecyclerView
│   ├── SettingsActivity.kt   — все настройки
│   └── MainViewModel.kt      — LiveData + управление состоянием
└── utils/
    ├── PreferencesManager.kt — SharedPreferences + Gson
    └── NotificationHelper.kt — каналы + foreground-уведомления
```

---

## 🔌 Интеграция реального VPN-SDK

`VpnService.kt` содержит полный каркас, но туннель — демо-заглушка.
Замените блок `// DEMO` на вызов реальной библиотеки:

### WireGuard
```kotlin
// build.gradle
implementation 'com.wireguard.android:tunnel:1.0.20230706'

// В startVpn():
val config = Config.parse(BufferedReader(StringReader(decodedConfig)))
val backend = GoBackend(this)
backend.setState(tunnel, State.UP, config)
```

### OpenVPN for Android
```kotlin
// build.gradle
implementation 'de.blinkt.openvpn:openvpn:0.7.39'

// В startVpn():
val vp = VpnProfile()
vp.readFromFile(configFile)
OpenVPNService.startVPN(this, vp)
```

### Xray / sing-box (proxy-режим)
```kotlin
// Запустить sing-box binary через ProcessBuilder
// Передать конфиг JSON
// Перенаправить трафик через TUN fd
```

---

## 📋 Формат JSON-подписки

Разместите файл на своём сервере (см. `servers_example.json`):

```json
{
  "version": 1,
  "updated_at": "2026-09-03T12:00:00Z",
  "servers": [
    {
      "id": "nl-01",
      "name": "Amsterdam #1",
      "country": "Нидерланды",
      "flag": "🇳🇱",
      "host": "nl1.example.com",
      "port": 51820,
      "protocol": "wireguard",
      "config": "<base64-encoded config>",
      "load": 23
    }
  ]
}
```

Поддерживаемые значения `protocol`: `wireguard`, `openvpn`, `proxy`

---

## ⚙️ Сборка

### Требования
- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 34
- minSdk: 26 (Android 8.0)

### Шаги
```bash
git clone <repo>
cd VpnApp
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🛡 Разрешения

| Разрешение | Зачем |
|---|---|
| `BIND_VPN_SERVICE` | Создание VPN-туннеля |
| `ACCESS_FINE_LOCATION` | GPS-автоподключение |
| `ACCESS_BACKGROUND_LOCATION` | GPS в фоне |
| `FOREGROUND_SERVICE` | Постоянные сервисы |
| `SCHEDULE_EXACT_ALARM` | Точный таймер |
| `RECEIVE_BOOT_COMPLETED` | Автозапуск |

---

## 📡 GPS-автоподключение

1. Включите переключатель «Авто по GPS» в настройках.
2. Находясь дома, нажмите «📌 Запомнить текущее место как дом».
3. Радиус зоны: **100 метров** (константа `homeRadius` в `AppSettings`).
4. **Дома** → VPN **отключается**.
5. **За пределами зоны** → VPN **включается** автоматически.

---

*Сгенерировано нейросетью Claude Sonnet 4.6 (Anthropic) · Сентябрь 2026*
