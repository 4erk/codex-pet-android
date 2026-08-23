# Privacy

Codex Pet обрабатывает состояние пета и ChatGPT notifications локально. Сеть используется отдельно и только для механизма обновления самого приложения через публичные GitHub Releases.

## Что обрабатывается локально

Только notifications от настроенного source package. Для текущего списка задач title/summary и PendingIntent находятся в оперативной памяти процесса.

На диск сохраняются:

- настройки overlay, реплик, анимаций и обновлений;
- размер и позиции;
- cached pet bitmap / импортированный pet pack;
- pet hash, timestamp, asset source и source package;
- временный APK обновления во внутреннем cache приложения до установки/очистки cache.

История conversation/task text на диск не записывается.

## Diagnostics

Release build не сохраняет полный notification text в diagnostic snapshots: только наличие и длину известных полей.

Debug build может показывать полный текст на отдельном Diagnostics screen для разработки. Эти значения:

- не пишутся в Android log;
- не записываются в DataStore;
- исчезают при очистке snapshots/завершении процесса;
- никогда не включаются во встроенный JSON export.

В export notification keys/tags/group keys/shortcut IDs хешируются SHA-256. Bitmap hash относится только к пикселям и размеру pet image.

## Сеть и обновления

Manifest содержит `INTERNET` и `ACCESS_NETWORK_STATE` исключительно для stable update channel.

Codex Pet обращается к публичному GitHub Releases API репозитория `4erk/codex-pet-android` и, если пользователь разрешил, скачивает APK-asset выбранного stable release. Notification/task text, диагностические snapshots, pet pack и пользовательские настройки в эти запросы не добавляются.

Перед установкой скачанного APK приложение проверяет:

- HTTPS URL;
- размер файла;
- SHA-256 digest, опубликованный GitHub для release asset;
- Android package name;
- совпадение сертификата подписи APK с уже установленным Codex Pet.

Установка выполняется через системный Android `PackageInstaller` и требует предусмотренного Android подтверждения пользователя. `REQUEST_INSTALL_PACKAGES` нужен только для этого сценария обновления.

В приложении отсутствуют analytics, Crashlytics, telemetry, ads и trackers. Cleartext traffic запрещён через manifest/network security config.

## Неиспользуемые возможности

Приложение не запрашивает storage-wide access, contacts, SMS, location, camera, microphone или `QUERY_ALL_PACKAGES`; не использует Accessibility; не читает `/data/data/com.openai.chatgpt`; не перехватывает трафик или токены ChatGPT/OpenAI.
