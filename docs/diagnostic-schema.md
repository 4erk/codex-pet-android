# Sanitized diagnostics schema v2

Корневые поля:

| Поле | Содержание |
|---|---|
| `schemaVersion` | Версия схемы, сейчас `2` |
| `exportedAt` | Unix time в milliseconds |
| `sanitized` | Всегда `true` для встроенного export |
| `app` | application id/version/build type |
| `device` | manufacturer/brand/model/Android version |
| `listener` | connection state, source package, counts, timestamps |
| `settings` | Только non-conversation settings и pet metadata |
| `cachedPet` | hash/source/timestamp/alpha, animation rows, look directions и frame size |
| `notifications` | Массив snapshots, максимум 100 in-memory |
| `privacy` | Явные проверки отсутствия text и INTERNET permission |

Notification identity fields `key`, `tag`, `groupKey`, `shortcutId` экспортируются только как SHA-256 hashes. `packageName`, numeric notification id, flags, category, channel и timestamps остаются открытыми для воспроизводимости.

`notificationRole` показывает решение classifier: `CODEX_TASK`, `CODEX_AVATAR` или `CHAT_MESSAGE`. Settings v2 также фиксируют видимость pet, pin и четыре переключателя auto/reaction bubbles.

Из известных text extras экспортируются:

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
