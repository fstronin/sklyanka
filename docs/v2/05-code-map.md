# Склянка — карта кодов требований

Нормативный документ: где после переезда документации живёт каждый код требования, куда
переносятся приёмочные сценарии и что уходит в архив. Версия 1.0 · 8 октября 2026.
Порядок работ — `docs/v2/04-roadmap.md` §5; журнал решений — `docs/v2/02-decisions.md`.

---

## 1. Правило устойчивости кодов

Код требования — устойчивый идентификатор: он не перенумеровывается, не переиспользуется
и не заводится заново при переезде документации. По оценке `04-roadmap.md` §5 в комментариях
кода 4 057 вхождений кодов; пересчёт 07.10.2026 по `*.kt`, `*.kts`, `*.xml` даёт 4 368
(из них 3 953 в строках комментариев) и ещё 368 в `*.feature`. Перенумерация осиротила бы
все эти ссылки.

Отсюда пять правил:

1. Определение кода переезжает в `docs/v2/`, номер остаётся прежним.
2. Ссылка в комментарии кода правится **только там, где ведёт на удалённый файл**: путь
   заменяется на новый по таблицам разделов 2 и 3. Формулировка требования в комментарии
   не переписывается.
3. Снятый код не удаляется и не переиспользуется: семья помечается одной строкой в документе,
   где она определена (`10-behavior.md` §8), без переноса содержания.
4. Новое требование получает следующий свободный номер своей семьи.
5. Пометки состояния требования едины для всего слоя: `[не реализовано]`, `[расхождение]`,
   `[вне продукта]`, `[снято Р-N]`.

## 2. Семейства кодов

| Семья | Диапазон | Где определено в `docs/v2/` | Прежний документ | Упоминаний |
|---|---|---|---|---|
| ПЛ- | ПЛ-1…ПЛ-5 | `11-quality.md` §3.9 «Сборка и совместимость» | `docs/spec/01-platform.md` §1 | 109 |
| ПЛ- | ПЛ-6…ПЛ-12 | `10-behavior.md` §4.1 «Ограничения платформы» | `docs/spec/01-platform.md` §3 | — |
| ПЛ- | ПЛ-13…ПЛ-22 | `10-behavior.md` §4.2–4.3 «Разрешения», «Вендорные шаги» | `docs/spec/01-platform.md` §5–6 | — |
| ПР- | ПР-1…ПР-14 | `10-behavior.md` §1.6, §2 | `docs/spec/02-data-model.md` §7 | 359 |
| ИНВ- | ИНВ-1…ИНВ-22 | `10-behavior.md` §1.5 | `docs/spec/02-data-model.md` §9 | 144 |
| МГ- | МГ-1…МГ-6 | `10-behavior.md` §1.8, `13-adr/ADR-0012-data-safety.md` | `docs/spec/02-data-model.md` §10 | 63 |
| НАП- | НАП-1…НАП-44 | `10-behavior.md` §3, `13-adr/ADR-0003`, `ADR-0004` | `docs/spec/04-reminders.md` | 640 |
| ПРФ- | ПРФ-1…ПРФ-31 | `10-behavior.md` §6 | `docs/spec/06-profiles.md` | 336 |
| ПЕР- | ПЕР-1…ПЕР-37 | `10-behavior.md` §7 | `docs/spec/07-data-transfer.md` | 350 |
| ЭКР- | ЭКР-1…ЭКР-10 | `10-behavior.md` §5 | `docs/spec/03-screens.md` | 84 |
| НФТ- | НФТ-1…НФТ-52, НФТ-16а, НФТ-16б, НФТ-43а | `11-quality.md` §3 | `docs/spec/10-nfr.md` | 872 |
| AC- | AC-1…AC-45 | `10-behavior.md` §5.9 | `docs/spec/14-reminder-check-ux.md` §1 | 91 |
| ЧАС- | ЧАС-1…ЧАС-30 | снято решением Р-3; отметка — `10-behavior.md` §8 | `docs/spec/05-watch.md` | 76 |
| Ц- | Ц-1…Ц-7 | `00-product.md` §3 | `docs/spec/00-overview.md` §3 (Ц-1…Ц-6) | 0 |
| У- | У-1…У-9 | `00-product.md` §8 | `docs/spec/00-overview.md` §4 (У-1…У-5) | 0 |
| П- | П-1…П-7 | `00-product.md` §4 | `docs/spec/00-overview.md` §5 (П-1…П-5) | 5 |
| НЦ- | НЦ-1…НЦ-9 | `00-product.md` §5.3 — состав без кодов | `docs/spec/00-overview.md` §7 | 0 |
| ПУТЬ- | ПУТЬ-1…ПУТЬ-7 | не переносится: нарратив. Пути человека — `00-product.md` §2, экраны — `10-behavior.md` §5 | `docs/spec/user-flows.md` | 0 |
| ОВ- | ОВ-1…ОВ-152 | решённые — архив; открытые — `02-decisions.md` (Р-12…Р-15) | `docs/spec/12-open-questions.md` | 1000 |
| ПМ- | ПМ-1…ПМ-33 | архив; действующее правило — в нормативном документе `docs/v2/` | `docs/spec/product-decisions.md` | 241 |
| Р- | Р-1…Р-15 | `02-decisions.md` — единственный журнал решений | нового журнала нет: прежние ПМ- и ОВ- заморожены | 0 |
| ИИ- | ИИ-1…ИИ-26 | `14-ai-entry.md` | семья введена 08.10.2026, прежнего документа нет | 0 |

«Упоминаний» — число вхождений кода семьи в `*.kt`, `*.kts`, `*.xml`, `*.feature`
на 07.10.2026; для разделённой семьи число стоит в первой строке. Ноль означает, что семья
живёт только в документации: её перенумерация кода не ломает, но и она не перенумеровывается.

Отдельные снятые и отложенные коды внутри действующих семей (НАП-22, НАП-23, НАП-27, НАП-28,
НАП-39, ПЛ-19, AC-32, AC-33, ПЕР-37) перечислены одной таблицей в `10-behavior.md` §8.

## 3. Старый путь → новый путь

Файл уходит в архив после того, как его действующее содержание перенесено. Архивный путь —
прежний путь с префиксом `docs/archive/` (раздел 5).

| Старый путь | Действующее содержание переезжает в | Файл |
|---|---|---|
| `docs/spec/00-overview.md` | `00-product.md` §2–§5, §8 (Ц-, П-, У-, НЦ-) | архив |
| `docs/spec/01-platform.md` | `11-quality.md` §3.9 (ПЛ-1…ПЛ-5), §7 (матрица устройств); `10-behavior.md` §4 (ПЛ-6…ПЛ-22) | архив |
| `docs/spec/02-data-model.md` | `10-behavior.md` §1 (ИНВ-, ПР-, МГ-); раскладка времени — `13-adr/ADR-0013-time-storage.md` | архив |
| `docs/spec/03-screens.md` | `10-behavior.md` §5 (ЭКР-); визуальные правила — `12-design.md` | архив |
| `docs/spec/04-reminders.md` | `10-behavior.md` §3 (НАП-); механизм — `13-adr/ADR-0003`, `ADR-0004` | архив |
| `docs/spec/05-watch.md` | не переносится: снято Р-3, отметка в `10-behavior.md` §8 | архив |
| `docs/spec/06-profiles.md` | `10-behavior.md` §6 (ПРФ-) | архив |
| `docs/spec/07-data-transfer.md` | `10-behavior.md` §7 (ПЕР-) | архив |
| `docs/spec/08-design-system.md` | `12-design.md` (токены, типографика, геометрия, компоненты) | архив |
| `docs/spec/09-scope.md` | `00-product.md` §5 (состав), `04-roadmap.md` §3 (исключено) | архив |
| `docs/spec/10-nfr.md` | `11-quality.md` §3 (НФТ-) | архив |
| `docs/spec/11-glossary.md` | термины модели — `10-behavior.md` §1.1–1.2; полный словарь переносится целиком | архив |
| `docs/spec/12-open-questions.md` | открытые вопросы — `02-decisions.md` (Р-12…Р-15); решённые (ОВ-) — только архив | архив |
| `docs/spec/13-monetization.md` | `03-monetization.md` | архив |
| `docs/spec/14-reminder-check-ux.md` | `10-behavior.md` §5.9 (AC-) | архив |
| `docs/spec/customer-questions.md` | не переносится: история согласований | архив |
| `docs/spec/decisions-request.md` | не переносится: история согласований | архив |
| `docs/spec/design-decisions.md` | действующие правила дизайна — `12-design.md`; журнал — только архив | архив |
| `docs/spec/designer-questions.md` | не переносится: история согласований | архив |
| `docs/spec/product-decisions.md` | действующие правила — нормативные документы `docs/v2/`; журнал (ПМ-) — только архив | архив |
| `docs/spec/user-flows.md` | пути человека — `00-product.md` §2; коды ПУТЬ- не переносятся | архив |
| `docs/spec/adr/ADR-0001…ADR-0013` (12 файлов) | `docs/v2/13-adr/` — 13 файлов в едином формате; `ADR-0010` написан впервые | прежние редакции — архив |
| `PLAN.md` | `04-roadmap.md` (фазы и гейты), §6 (реестр задач) | архив |
| `docs/tasks/README.md` | реестр и статусы — `04-roadmap.md` §6; цепочка артефактов задачи — `.claude/skills/skl-task*` | архив |
| `docs/tasks/SKL-NN/acceptance.feature` (93 файла) | `acceptance/src/main/resources/features/SKL-NN/acceptance.feature` (раздел 4) | **не архив**: это тесты |
| `docs/tasks/SKL-NN/task.md`, `qna.md`, `tech-options.md`, `TDD.md`, `TODO.md` | действующее поведение — `docs/v2/`; остальное — история задачи | архив |
| `docs/tasks/SKL-NN/` прочие артефакты (`pilot-*.md`, `runbook.md`, `self-check*.md`, `store-listing.md`, снимки) | не переносятся | архив |
| `docs/tasks/SKL-03/run/sklyanka-seed-stand.json` | `tools/spike/sklyanka-seed-stand.json` — вход инструмента, а не документ; путь в `tools/spike/config.sh:14` | перенос в `tools/` |
| `design/**` | неприкосновенный исходник заказчика | не трогается |

## 4. Приёмочные сценарии

`*.feature` — не документация, а тесты: модуль `acceptance` читает их прямо из `docs/tasks/`,
и на этом пути построены проверки сборки. Перенос делается до удаления `docs/tasks/`
и проверяется прогоном.

**Куда.** `acceptance/src/main/resources/features/SKL-NN/acceptance.feature` — по каталогу
на задачу, все 93 файла. Вложенность обязательна: `featureTargets()`
(`acceptance/build.gradle.kts:88-96`) берёт корневой каталог и запускает Cucumber отдельно
по каждому вложенному каталогу, в котором есть хотя бы один `.feature`, — иначе неразбираемый
файл одной задачи обрывает весь прогон. Сценарии закрытых задач остаются как регрессия.
Ресурсы попадают в артефакт модуля `acceptance`, который подключается только конфигурацией
`testDebugImplementation` и в APK не входит.

**Путь по умолчанию.** `featuresPath` — `acceptance/build.gradle.kts:41`: значение `"docs/tasks"`
заменяется на `"acceptance/src/main/resources/features"`. Переопределение свойством
`-Psklyanka.acceptance.features=` и карантин `-Psklyanka.acceptance.quarantine=` сохраняются.

**Шесть задач сборки читают этот путь** (все — `inputs.dir(rootProject.file(featuresPath))`):

| Задача | Что делает | Где вызывается |
|---|---|---|
| `acceptanceRun` | автоматический прогон сценариев, кроме `@manual`, `@slow`, `@postponed`, `@screen`, `@device` | `verifyAll` |
| `acceptanceChecklist` | печатает чек-лист сценариев `@manual` для устройства | `verifyAll` |
| `acceptanceScreenList` | печатает сценарии `@screen`: их исполняет экранный стенд на JVM | `verifyAll` |
| `acceptanceDeviceList` | печатает сценарии `@device`: проверяются только на устройстве | `verifyAll` |
| `verifyAcceptanceSteps` | валит сборку на дубле шага в словаре приёмки | `verifyAll` и `check` модуля (`acceptance/build.gradle.kts:483-485`) |
| `acceptanceSteps` | печатает реестр всех шагов | вручную |

`verifyAll` собирает первые пять (`build-logic/.../ProjectChecksConventionPlugin.kt:111-121`).

**Точки, где путь зашит в код.** Меняются вместе с `featuresPath`, иначе прогон падает
на отсутствующем файле:

| Где | Что | Новый путь |
|---|---|---|
| `acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/run/ScreenAcceptanceRun.kt:43` | `docs/tasks/$task/acceptance.feature`; на `featureOf(task)` построены 85 экранных прогонов в `app` и `feature-*` | `acceptance/src/main/resources/features/$task/acceptance.feature` |
| `acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/tools/StepDictionary.kt:33` | `TASKS_DIR = "docs/tasks"` — обход сценариев словарём шагов | `acceptance/src/main/resources/features` |
| `acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/ScreenStandSteps.kt:585` | `TASKS_DIRECTORY = "docs/tasks"` | `acceptance/src/main/resources/features` |
| `acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/ScreenStandSteps.kt:604` | `TECH_DESIGN = "docs/tasks/SKL-24/TDD.md"` | `docs/archive/tasks/SKL-24/TDD.md` |
| `acceptance/src/main/kotlin/ru/stohastika/sklyanka/acceptance/steps/task/EmulatorHarnessSteps.kt:437` | `TECH_DESIGN = "docs/tasks/SKL-27/TDD.md"` | `docs/archive/tasks/SKL-27/TDD.md` |
| `acceptance/src/test/kotlin/ru/stohastika/sklyanka/acceptance/tools/StepDictionaryRulesTest.kt:108` | `File(RepositoryLayout.root, "docs/tasks")` в тесте правил словаря | `acceptance/src/main/resources/features` |
| `tools/spike/config.sh:14` | `SPIKE_SEED="docs/tasks/SKL-03/run/sklyanka-seed-stand.json"` | `tools/spike/sklyanka-seed-stand.json` |

Шаги, проверяющие состав аналитического контура (`AnalyticsContourSteps.kt`, в том числе
константа `PRODUCT_DECISIONS = "docs/spec/product-decisions.md"`), удаляются вместе с контуром
по Р-4 — пути в них не обновляются.

**Теги сохраняются:** `@manual`, `@slow`, `@screen`, `@device`, `@postponed`. Сценарии
часов (ЧАС-) и отложенные сценарии аналитики (`@postponed`) удаляются вместе со своими
контурами (Р-3, Р-4), а не переносятся.

**Порядок.** (1) Перенести 93 файла одним коммитом, сохранив вложенность `SKL-NN/`.
(2) Обновить `featuresPath` и семь точек таблицы выше. (3) Прогнать
`:acceptance:acceptanceRun`, `:acceptance:verifyAcceptanceSteps` и `verifyAll` — прогон
обязан увидеть те же сценарии, что и до переноса. (4) Только после зелёного прогона удалять
`docs/tasks/`.

## 5. Архив

Архив — не нормативный слой: в нём лежат документы, на которые ссылаются по старым номерам,
но новых записей там не появляется (`02-decisions.md`, вступление).

**Что уходит в `docs/archive/`:**

- `docs/archive/spec/` — все файлы `docs/spec/` (21) и каталог `adr/` с прежними редакциями
  решений: журналы ОВ-1…ОВ-152 и ПМ-1…ПМ-33, журналы дизайн-решений, вопросы заказчику
  и дизайнеру, словарь, нарративные пути человека;
- `docs/archive/tasks/SKL-NN/` — артефакты закрытых и снятых задач: `task.md`, `qna.md`,
  `tech-options.md`, `TDD.md`, `TODO.md`, отчёты пилота, чек-листы, снимки. Номера задач
  не переиспользуются, реестр — `04-roadmap.md` §6;
- `docs/archive/PLAN.md` — прежний план из 105 задач и семи датированных снимков приоритета.

**Как ссылаться.**

- Действующее правило цитируется по документу `docs/v2/` и коду требования, а не по архиву:
  «НАП-7 (`docs/v2/10-behavior.md`)», а не «`docs/spec/04-reminders.md`».
- История решения цитируется номером записи и архивным путём: «ОВ-76
  (`docs/archive/spec/12-open-questions.md`)», «ПМ-24 (`docs/archive/spec/product-decisions.md`)».
  Новые решения — только записью Р-N в `02-decisions.md`.
- Ссылка в комментарии кода, которая вела на удалённый файл, заменяется на архивный путь
  или на путь `docs/v2/` по таблицам разделов 2 и 3 — в зависимости от того, где теперь
  живёт правило, на которое ссылается комментарий.
- Архив не читается сборкой. Исключение — шаги приёмки, проверяющие устройство репозитория:
  их константы обновляются по таблице раздела 4.
