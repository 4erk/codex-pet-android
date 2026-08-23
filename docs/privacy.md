# Privacy

Codex Pet спроектирован как local-only приложение.

## Что обрабатывается

Только notifications от настроенного source package. Для текущего списка задач title/summary и PendingIntent находятся в оперативной памяти процесса.

На диск сохраняются только:

- настройки overlay;
- размер и позиции;
- cached pet bitmap;
- pet hash;
- timestamp;
- asset source и source package.

История conversation/task text на диск не записывается.

## Diagnostics

Release build не сохраняет полный notification text даже in-memory snapshots: только наличие и длину известных полей.

Debug build может показывать полный текст на отдельном Diagnostics screen для Phase 0. Эти значения:

- не пишутся в Android log;
- не записываются в DataStore;
- исчезают при очистке snapshots/завершении процесса;
- никогда не включаются во встроенный JSON export.

В export notification keys/tags/group keys/shortcut IDs хешируются SHA-256. Bitmap hash относится только к пикселям и размеру pet image.

## Сеть

Manifest не содержит `android.permission.INTERNET`. В приложении отсутствуют analytics, Crashlytics, telemetry, ads, trackers и update checker.

Файл `network_security_config.xml` дополнительно запрещает cleartext traffic, но основная гарантия — отсутствие network permission.

## Неиспользуемые возможности

Приложение не запрашивает storage-wide access, contacts, SMS, location, camera, microphone или `QUERY_ALL_PACKAGES`; не использует Accessibility; не читает `/data/data/com.openai.chatgpt`; не перехватывает трафик и токены.
