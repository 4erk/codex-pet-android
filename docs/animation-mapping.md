# Анимации и приоритеты

`0.5.0` разделяет три уровня: достоверный task status, контекстный animation cue и итоговое steady/one-shot состояние пета.

## Ряды pet pack

| Ряд | Использование |
|---|---|
| idle | Нет активной работы/проверки/проблемы |
| running-right / running-left | Drag и edge snap вправо/влево |
| waving | Приветствие, обычное сообщение ChatGPT, восстановление связи |
| jumping | Однократное успешное завершение |
| failed | Ошибка, блокировка или disconnected |
| waiting | Нужен пользователь или идёт reconnect |
| running | Активная Codex-работа |
| review | Тесты, lint, аудит, чтение логов, проверка |
| 16 look directions (v2) | Короткий взгляд в сторону открытой реплики |

## Structured status

Task status сначала определяется без попытки угадать анимацию:

1. indeterminate / partial structured progress → `RUNNING`;
2. completed structured progress → `COMPLETED`;
3. `FLAG_ONGOING_EVENT` → `RUNNING`;
4. conservative status text fallback;
5. `UNKNOWN`.

## Weighted text cue

Для уточнения RUNNING/UNKNOWN используются RU/EN правила с весами:

1. requires input / approval / confirmation — самый сильный пользовательский сигнал;
2. reconnecting;
3. disconnected;
4. failed/blocked/permission denied/tests failed;
5. explicit retry/resume — активное восстановление;
6. completed/success;
7. review/test/lint/analyze;
8. generic active work.

К позиции совпадения добавляется небольшой recency bonus, но слабый generic verb не может перебить сильную ошибку только потому, что расположен позже.

Примеры:

- `Build failed while building the release` → `FAILED`, а не `ACTIVE`;
- `Build failed. Retrying and continuing` → `ACTIVE`;
- `Implemented the fix; now running tests` → `REVIEWING`;
- `Connection lost. Trying to reconnect` → `RECONNECTING`;
- `Нужно ваше подтверждение` → `WAITING_FOR_INPUT`.

## Steady-state priority

Итоговое постоянное состояние для совокупности Codex-задач:

1. `WAITING_FOR_INPUT` → `WAITING`;
2. `ERROR` / `FAILED` / `DISCONNECTED` → `FAILED`;
3. `RECONNECTING` → `WAITING`;
4. `REVIEWING` → `REVIEW`;
5. `RUNNING` / `ACTIVE` → `RUNNING`;
6. иначе `IDLE`.

`COMPLETED` намеренно отсутствует в steady priority: завершённое notification может ещё жить для transient speech, но не удерживает пета в `REVIEW`.

## One-shot reactions

- переход в `COMPLETED` → один `JUMPING`, затем пересчёт steady state;
- новое обычное сообщение ChatGPT → один `WAVING`, затем steady state;
- успешный выход из disconnected/reconnecting в active/review → один `WAVING`;
- ручное открытие реплик → `WAVING`, затем при наличии v2 pack короткий взгляд в сторону бабла.

Если одновременно есть attention/error state, one-shot не должен скрывать более важную проблему.

## Gesture states

Во время drag направление определяется последней фактической горизонтальной дельтой. После отпускания snap проигрывает `RUNNING_LEFT/RIGHT` до края. Новое касание отменяет текущий snap, затем UI возвращается к актуальному task state.

## Speech priority

Speech и animation используют совместимую, но не идентичную модель. Для реплик порядок: `needs input → blocked → ready → running`; показывается до 5 отдельных баблов, затем следующие элементы идут страницами. Attention-события не имеют искусственного deadline, completed/chat — transient.
