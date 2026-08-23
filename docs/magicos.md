# HONOR X9c / MagicOS

MagicOS может агрессивно завершать foreground/background процессы. Названия пунктов немного отличаются между регионами и MagicOS 9/10.

## Обязательные разрешения

В Codex Pet последовательно откройте:

1. `Доступ к уведомлениям` и включите Codex Pet.
2. `Отображение поверх приложений` и разрешите Codex Pet.
3. На Android 13+ разрешите собственные notifications Codex Pet.

## App launch

Откройте `Настройки → Приложения → Запуск приложений` (иногда `Optimizer → App launch`), найдите Codex Pet:

- выключите `Управлять автоматически`;
- включите `Автозапуск / Auto-launch`;
- включите `Косвенный запуск / Secondary launch`;
- включите `Работа в фоне / Run in background`.

Это соответствует официальной рекомендации HONOR: [background app restart guidance](https://www.honor.com/global/support/content/en-us00406916/).

## Battery optimization

Откройте поиск в Settings, найдите `Оптимизация батареи`, выберите все приложения, затем Codex Pet и установите `Не разрешать` оптимизацию.

При необходимости закрепите Codex Pet в Recent apps свайпом вниз по карточке до появления значка замка.

## System bubble

Сначала завершите Phase 0 с включённым официальным ChatGPT bubble. Затем используйте кнопку в Codex Pet и отключите bubbles именно для ChatGPT, оставив обычные notifications включёнными.

После этого повторите диагностику: MagicOS/ChatGPT могут перестать публиковать `BubbleMetadata`, даже если notification listener продолжает получать обычные notifications. Это не предполагается заранее.

## После reboot

Опция автозапуска — best effort. Если MagicOS не разрешил foreground service start после boot:

1. откройте Codex Pet;
2. проверьте App launch и Battery optimization;
3. нажмите `Запустить Codex Pet`;
4. зафиксируйте ограничение в Phase 0 report.

Private HONOR intents/API намеренно не используются: приложение открывает только публичные Android settings screens и показывает ручной маршрут, когда прямого экрана нет.
