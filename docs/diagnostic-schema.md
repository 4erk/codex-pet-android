# Sanitized diagnostics schema v4

Корневые поля:

| Поле | Содержание |
|---|---|
| `schemaVersion` | Версия схемы, сейчас `4` |
| `exportedAt` | Unix time в milliseconds |
| `sanitized` | Всегда `true` для встроенного export |
| `app` | application id/version/build type |
| `device` | manufacturer/brand/model/Android version |
| `listener` | connection state, active count, heartbeat, scan failures, rebind attempts, timestamps |
| `settings` | Non-conversation settings, speech/update preferences и pet metadata |
| `cachedPet` | hash/source/timestamp/alpha, animation rows, look directions и frame size |
| `notifications` | Массив snapshots, максимум 100 in-memory |
| `privacy` | Проверка отсутствия notification text + факт INTERNET permission и его update-only purpose |

Notification identity fields `key`, `tag`, `groupKey`, `shortcutId` экспортируются только как SHA-256 hashes. `packageName`, numeric notification id, flags, category, channel и timestamps остаются открытыми для воспроизводимости.

`notificationRole` показывает решение classifier: `CODEX_TASK`, `CODEX_AVATAR` или `CHAT_MESSAGE`. `parserNotes` дополнительно содержит confidence и sanitized reasons adaptive classifier без добавления полного notification text в export.

Listener v4 фиксирует `lastHeartbeatAt`, `consecutiveScanFailures` и `rebindAttempts`, чтобы отличать реально работающий listener от OEM-состояния «binding числится подключённым, но activeNotifications больше не читаются».

Settings v4 добавляют bubble scale/count и параметры GitHub stable updater (`autoUpdateEnabled`, `autoDownloadUpdates`, `updateWifiOnly`, `lastUpdateCheckAt`).

Из известных text extras экспортируются только наличие и длина:

```json
{
  "android.title": {
    "present": true,
    "length": 24
  }
}
```

Само значение отсутствует даже при export из debug build.

Для каждого pet candidate экспортируются source, Android `Icon.type`, Drawable class, intrinsic/rendered dimensions, animation/adaptive flags, alpha statistics, SHA-256 и решение transparency gate.
