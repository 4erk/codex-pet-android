# Phase 0: проверка реальных ChatGPT notifications

Цель — экспериментально определить, что текущая версия ChatGPT публикует на HONOR X9c / MagicOS 9, до любых выводов о полноценной auto-sync.

Экспорт санитизирован: тексты title/summary/message не включаются. Для них сохраняются только факт наличия и длина. `key`, `tag`, `groupKey` и `shortcutId` хешируются.

## Подготовка

Запишите в заметки:

- модель: HONOR X9c;
- версия MagicOS и Android;
- версия ChatGPT;
- выбранный pet A;
- версия Codex Pet;
- включены ли системные bubbles ChatGPT.

Установите debug APK, выдайте Notification access и Overlay permission. Обычные ChatGPT notifications пока не отключайте.

## Эксперимент

### 1. Baseline

1. Закройте активные Codex-задачи, если это возможно.
2. В Diagnostics очистите snapshots.
3. Нажмите `Refresh active notifications`.
4. Экспортируйте `01-baseline.json`.

Ожидание: приложение не должно придумывать pet или задачи из `smallIcon`.

### 2. Одна выполняющаяся задача

1. Запустите одну Codex-задачу в ChatGPT.
2. Дождитесь появления официального bubble/status notification.
3. Обновите diagnostics и экспортируйте `02-running-one.json`.
4. Тапните по прозрачному pet и проверьте список задач.
5. Тапните по строке задачи и проверьте, открывается ли нужная сессия.

Запишите:

- `bubble.exists`, `iconExists`, `iconTypeName`;
- `drawableClass`, intrinsic size;
- bitmap width/height, `hasAlpha`, transparent percentage, partial-alpha percentage, transparent corners;
- hash и `acceptedForOverlay`;
- наличие content/bubble PendingIntent;
- structured progress, `FLAG_ONGOING_EVENT`, group/shortcut data;
- число задач в ChatGPT и число задач, доступных через notifications.

### 3. Переход running → completed

1. Дождитесь завершения той же задачи.
2. Экспортируйте `03-completed.json`.
3. Проверьте короткий bounce и возврат в idle.

Не помечайте результат SUCCESS/ERROR вручную: сравниваются только структурированные признаки и фактически опубликованные данные.

### 4. Несколько задач

1. Запустите 2–4 независимые задачи.
2. Экспортируйте `04-multiple.json`.
3. Сравните отдельные notifications, group summary, InboxStyle/MessagingStyle, `shortcutId`, `groupKey` и PendingIntent.

Если ChatGPT UI показывает больше задач, чем public notification API, это фиксируется как limitation; недостающие строки не синтезируются.

### 5. Смена pet

1. При неизменной задаче выберите pet B в ChatGPT.
2. Дождитесь notification update, нажмите Refresh.
3. Экспортируйте `05-pet-b.json`.
4. Сравните hash каждого `BUBBLE_ICON`, conversation icon и large icon с экспериментом 2.
5. Верните pet A и повторите как `06-pet-a-again.json`.

Auto-sync подтверждён, только если изменение выбранного pet воспроизводимо меняет подходящий public icon/hash и overlay автоматически принимает новый прозрачный asset.

### 6. Системные bubbles отключены

1. Через кнопку Codex Pet откройте `ChatGPT → Notifications → Bubbles`.
2. Отключите системные bubbles, но оставьте обычные ChatGPT notifications.
3. Запустите/обновите задачу.
4. Экспортируйте `07-bubbles-disabled.json`.

Критический вопрос: продолжает ли ChatGPT прикладывать `BubbleMetadata` и pet icon после отключения presentation в SystemUI.

### 7. Process death и reboot

1. Включите автозапуск и настройте MagicOS по `docs/magicos.md`.
2. Смахните Activity из recent apps: overlay должен продолжить работу.
3. Перезагрузите телефон.
4. Если MagicOS блокирует автоматический restart, откройте Codex Pet и зафиксируйте это как OEM limitation.
5. Проверьте восстановление размера, portrait/landscape position и cached pet.

## Матрица решения

| Результат | Действие |
|---|---|
| `BUBBLE_ICON` чистый и прозрачный | Использовать как основной auto-sync source |
| Bubble icon непригоден, Person icon чистый | Использовать conversation fallback и явно показать source |
| Только large icon чистый | Использовать large-icon fallback и явно показать source |
| Все public sources непрозрачны/не являются pet | Не заявлять auto-sync; оставить manual import |
| После отключения bubbles metadata остаётся | Рекомендуемый сценарий: system bubble off, Codex Pet on |
| После отключения bubbles metadata исчезает | Объяснить ограничение; проверить, сохраняется ли cached pet и task data в обычных notifications |
| Notification API даёт меньше задач | Показывать только доступные; оставить «Открыть все задачи в ChatGPT» |

## Быстрое сравнение JSON

```bash
jq '.notifications[] | {
  event,
  id,
  bubble,
  progress,
  progressMax,
  progressIndeterminate,
  selectedPetSource,
  petCandidates
}' 02-running-one.json
```

Нельзя присылать скриншоты или несанитизированные dumps с приватным текстом, если в них есть чувствительные данные. Предпочтителен встроенный sanitized export.
