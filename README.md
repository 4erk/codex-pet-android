# Codex Pet for Android

Нативное Android-приложение, которое показывает выбранного ChatGPT/Codex pet как прозрачный `TYPE_APPLICATION_OVERLAY`, без системного белого bubble-круга, маски и badge ChatGPT.

Проект не модифицирует ChatGPT, не использует root, Accessibility, Shizuku, hooking, приватные файлы ChatGPT или закрытые OpenAI API. Состояние задач берётся только из публичного Android notification API; сеть используется отдельно только для stable-обновлений через публичные GitHub Releases.

> Текущий stable: `0.5.0`. Интеграция остаётся notification-driven и умеет самовосстанавливаться при зависании listener/OEM binding.

## Основное

- Android 11–16: `minSdk 30`, `targetSdk 36`.
- Kotlin/native Views, без тяжёлого UI-фреймворка.
- Прозрачный `TYPE_APPLICATION_OVERLAY` с `PixelFormat.TRANSLUCENT`.
- Встроенный Violet Vixen v2: 9 состояний + 16 направлений взгляда.
- Ручной PNG/WebP/spritesheet/ZIP import с ограничениями размера и защитой от ZIP traversal.
- Автопоиск pet в публичных notification icon sources с проверкой прозрачности и защитой от случайной замены полного pack одним кадром.

## Notifications и устойчивость

`NotificationListenerService` отслеживает настроенный source package (по умолчанию `com.openai.chatgpt`).

В `0.5.0` listener больше не полагается на один callback или один channel id:

- heartbeat через периодический `activeNotifications` snapshot;
- debounced reconcile после posted/removed/ranking событий;
- защита от stale параллельных snapshot;
- повторный `requestRebind` с backoff;
- forced unbind/rebind после серии ошибок activeNotifications;
- адаптивная классификация Codex по channel/shortcut/progress/ongoing/text signals вместо жёсткой зависимости от `codex_remote_session`.

## Реплики

- Comic-style speech bubbles, привязанные к pet.
- До 5 отдельных кликабельных баблов одновременно.
- Независимый от pet масштаб `0.75×–1.50×`.
- Safe-area placement: справа/слева, при нехватке места сверху/снизу.
- Drag и snap перемещают уже attached `WindowManager` views через `updateViewLayout` — без remove/add мерцания.
- Overflow ротируется страницами.
- Приоритет: requires-input → blocked/error → result → running.
- Attention-события остаются до изменения notification; completion/chat — transient.
- Встроенный интерактивный **стенд 1–5 баблов** позволяет проверять компоновку без реальных уведомлений.
- Тап по баблу: `contentIntent` → bubble intent → launcher ChatGPT.

## Анимации

Состояние выбирается общей тестируемой моделью, а не разрозненными условиями UI:

- `WAITING`: требуется ответ/подтверждение или идёт reconnect;
- `FAILED`: ошибка/блокировка/disconnected;
- `REVIEW`: тесты, lint, аудит, чтение логов, проверка;
- `RUNNING`: реальная активная работа;
- `IDLE`: ничего активного;
- `JUMPING`: one-shot на завершение;
- `WAVING`: приветствие, новое сообщение, восстановление связи;
- `RUNNING_LEFT/RIGHT`: drag и edge snap.

Текстовые эвристики RU/EN имеют веса. Структурированный status/progress сильнее слабого глагола; явная ошибка не исчезает из-за позднего `building`, а явный `retrying/resuming` может корректно восстановить состояние.

## Обновление приложения

Stable-build проверяет `https://api.github.com/repos/4erk/codex-pet-android/releases/latest`.

Перед установкой APK проверяются:

1. stable GitHub Release;
2. HTTPS asset URL и разумный размер;
3. GitHub `sha256` digest release asset;
4. Android package name;
5. точное совпадение signing certificate с установленным Codex Pet.

Затем APK передаётся системному `PackageInstaller`. Защита Android не обходится: пользователь подтверждает установку, а при первом обновлении Android может попросить разрешить Codex Pet устанавливать обновления из этого источника.

Debug-build намеренно не обновляется поверх stable: один раз нужно установить stable APK, подписанный постоянным release key. После этого все будущие stable-версии обновляются тем же сертификатом.

## Интерфейс

Навигация сгруппирована по контексту:

- **Питомец** → внешний вид, размер, pack → ссылки на реплики/анимации/стенд;
- **Реплики** → текст, lifetimes, масштаб, количество → стенд и listener;
- **Анимации** → сценарии/условия/ручной тест → реплики и listener;
- **Подключение** → heartbeat/rebind/system access/MagicOS;
- **Поведение** → gestures/autostart/background;
- **Обновления** → stable channel/download/install;
- **Помощь** → восстановление, visual lab и diagnostics.

UI использует крупную визуальную иерархию, сгруппированные settings-карточки и disclosure-navigation вместо длинного набора равнозначных кнопок.

## Приватность и permissions

Notification/task text обрабатывается локально и не отправляется в GitHub update requests. История переписки на диск не сохраняется.

Permissions:

- `SYSTEM_ALERT_WINDOW`;
- `BIND_NOTIFICATION_LISTENER_SERVICE` на listener service;
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`;
- `POST_NOTIFICATIONS`;
- `RECEIVE_BOOT_COMPLETED`;
- `INTERNET` + `ACCESS_NETWORK_STATE` — только stable update channel;
- `REQUEST_INSTALL_PACKAGES` — только передача проверенного APK Android PackageInstaller.

Нет analytics, telemetry, Firebase, ads, trackers, storage-wide access, contacts, SMS, location, camera, microphone или `QUERY_ALL_PACKAGES`. Подробнее: [docs/privacy.md](docs/privacy.md).

## Сборка и release gate

Требования: JDK 17, Android SDK 36, Build Tools 35+.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

PR обязан пройти unit tests, lint, debug APK и minified release candidate.

`main` дополнительно требует **все** production signing secrets:

- `CODEX_PET_KEYSTORE_BASE64`;
- `CODEX_PET_KEYSTORE_PASSWORD`;
- `CODEX_PET_KEY_ALIAS`;
- `CODEX_PET_KEY_PASSWORD`.

Без них stable release намеренно падает и не публикует unsigned APK. При успехе CI:

1. собирает release с R8/resource shrinking;
2. проверяет подпись через `apksigner`;
3. создаёт `codex-pet-<version>.apk` + `.sha256`;
4. публикует/обновляет GitHub Release `v<version>`.

## HONOR / MagicOS

См. [docs/magicos.md](docs/magicos.md). Для максимальной устойчивости разрешите Auto-launch, Secondary launch и Run in background и исключите Codex Pet из battery optimization. Даже при OEM kill listener/overlay теперь имеют self-heal при следующей доступной точке восстановления.

## Ограничения

- Android notification API может содержать меньше задач, чем внутренний activity tray ChatGPT; приложение не выдумывает отсутствующие задачи.
- Отключение системных ChatGPT bubbles может изменить наличие `BubbleMetadata`, но обычные notifications должны остаться включёнными.
- Background FGS/autostart остаются subject to Android/MagicOS restrictions.
- Автообновление работает только между APK с одинаковым release signing certificate.

## Release notes

- [0.5.0](docs/releases/0.5.0.md)
- [0.4.0](docs/releases/0.4.0.md)
- [0.3.0-beta1](docs/releases/0.3.0-beta1.md)

## License

Apache-2.0. См. [LICENSE](LICENSE).
