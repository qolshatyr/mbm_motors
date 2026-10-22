---
name: project-skills
description: Каталог и маршрутизатор всех навыков проекта MBM AutoMotors — Postman-навыков из postman/skills/ пользовательских навыков из ~/.agents/skills/ (отладка, проверка веб-приложения, дизайн, проверка перед завершением) и плагинов java-backend-skills, frontend-design, code-simplifier. Используй в начале любой нетривиальной задачи в этом репозитории — Java/Spring-код, React-фронтенд, CloudShop API, Postman-коллекции, ошибки и падающие тесты, UI и дизайн, завершение работы, коммит или PR — чтобы выбрать и прочитать нужные SKILL.md. Также используй, когда спрашивают «какие навыки есть», «какой скилл применить» или «список скиллов».
---

# Навыки проекта MBM AutoMotors

Claude Code сам не находит навыки из `postman/skills/` и `~/.agents/skills/`:
их нет в списке доступных навыков. Поэтому этот файл служит каталогом.
Выбери подходящие навыки по таблицам ниже и прочитай их `SKILL.md` по пути
через Read, прежде чем действовать.

Бери столько навыков, сколько действительно нужно задаче. Каждый
прочитанный навык занимает контекст и добавляет правила, поэтому одной
задаче обычно хватает одного-трёх. Кратко скажи пользователю, какие навыки
применяешь и зачем: этого требует `AGENTS.md`.

## Как выбирать

1. Определи, что за задача (таблица «Маршрутизация»).
2. Навыки плагинов (с префиксом `плагин:`) вызывай через Skill. Для
   остальных прочитай `SKILL.md` по пути. Справочники из `reference/` и
   `references/` читай, только когда навык на них отправляет.
3. Проверь, подходит ли навык к стеку: Java 21, Spring Boot 4.0.8,
   Thymeleaf, PostgreSQL, Flyway, React 19, Vite, Tailwind 4, Postman CLI.
   Если совет навыка противоречит `AGENTS.md` или коду проекта, следуй
   проекту.
4. Если навыка нет на диске (например, `~/.agents/skills/` не установлен на
   этой машине), продолжай без него. Сообщай об этом, только если это
   влияет на результат.
5. Перед словами «готово», коммитом или PR всегда примени
   `verification-before-completion`.

Навык — это способ работы, а не разрешение. Он не даёт права менять файлы
вне задачи, переключать режим «Обучение»/«Разработка» или выполнять
внешние действия без согласия.

## Маршрутизация

| Задача в этом проекте | Навыки (по порядку) |
| --- | --- |
| Java/Spring-код: модули `catalog`, `cart`, `order`, `delivery`, `admin`, `sync`, `cloudshop`, `config` | `java-backend-skills:java-implement` + навык области из строк ниже → `verification-before-completion`. Если плагина нет, `spring-boot-skill` |
| Бины, конфигурация, `@ConfigurationProperties`, запуск приложения | `java-backend-skills:spring-boot` |
| JPA, запросы, транзакции, блокировки (`DatabaseSyncLock`), миграции Flyway | `java-backend-skills:spring-data` |
| Spring Security, CSRF, вход сотрудника (`admin`), права на заявки | `java-backend-skills:spring-security` |
| Внешние вызовы CloudShop, повторы, outbox и `delivery`, расписание `sync` | `java-backend-skills:spring-integrations` (повтор неясной отправки запрещён правилами проекта) |
| Логи, метрики, health, корректное завершение | `java-backend-skills:spring-observability` |
| Тесты Spring MVC, Testcontainers, PostgreSQL-интеграция | `java-backend-skills:spring-testing` |
| Обычные unit-тесты JUnit/Mockito | `java-backend-skills:java-unit-testing` |
| Сбой Maven, Spotless, зависимости, профиль `release-site` | `java-backend-skills:java-build` |
| Границы модулей, ответственность классов, абстракции | `java-backend-skills:java-design` |
| Потоки, executors, конкурентный доступ | `java-backend-skills:java-concurrency` |
| Скорость, память, время запуска (с замерами) | `java-backend-skills:java-performance` |
| Выбор представления данных: record, Optional, коллекции, stream | `java-backend-skills:java-idiomatic` |
| Новый или изменённый HTTP-эндпоинт приложения | `api-builder` → `java-backend-skills:spring-web` (+ `java-backend-skills:spring-security`, если меняется доступ) → `api-testing` → `verification-before-completion` |
| Ошибка, падающий тест, неожиданное поведение, «не работает» | `systematic-debugging` (сначала причина, потом исправление) → навык области ошибки (`java-backend-skills:java-debugging` для Java/Spring, `java-backend-skills:java-build` для сборки, `webapp-testing` для воспроизведения в браузере) → `verification-before-completion` |
| Не визуальная работа во `frontend/`: логика, состояние, React Query, клиент API, сборка Vite | Специального навыка нет: следуй `docs/Frontend_Architecture.md` → `webapp-testing` → `verification-before-completion` |
| Проверить, как работает сайт в браузере: каталог, корзина, checkout, React-витрина, прототип | `webapp-testing` (готовые сценарии есть в `tests/browser/`) |
| Проверить удобство и доступность готового интерфейса | `web-design-guidelines` |
| Создать или заметно изменить визуальный дизайн (страницы, витрина, `docs/Storefront_Design.md`) | `frontend-design:frontend-design` (если плагина нет — `~/.agents/skills/frontend-design`) → `web-design-guidelines` → `webapp-testing` |
| Проверка CloudShop API через Postman, запуск коллекции, ad-hoc запрос | `api-testing` (+ `bootstrap`, если CLI или workspace не настроены). CloudShop — это v2.1 JSON `postman/assets/CloudShop_ReadOnly.postman_collection.json`, порядок шагов задаёт `docs/cloudshop/CloudShop_Postman_Guide.md` |
| Документировать API, OpenAPI-спецификация, публикация документации | `api-builder` → `api-documentation` (+ `collection-schema-v3` для файлов v3) |
| Ручная правка коллекций v3 приложения в `postman/collections/` (MBM Cart/Catalog/Checkout/Staff Requests API) или окружений в `postman/environments/` | `collection-schema-v3`. Коллекции CloudShop в `postman/assets/` остаются v2.1 и GET-only, в v3 их не переводят |
| Mock CloudShop или собственного API, сценарии ошибок без реального сервера | `api-mocking` |
| Запуск коллекции по строкам CSV/JSON/таблицы PostgreSQL | `datasets` |
| Postman в GitHub Actions, гейт на PR | `ci-integration` → `api-testing` |
| Регулярные проверки живого API | `api-monitoring` (⚠ реальный трафик, облачный ресурс: только с согласия) |
| Нагрузочное тестирование | `performance-testing` (⚠ реальный трафик: сначала подтверди цель и масштаб, для CloudShop предложи mock) |
| Коммит, PR, «всё готово» | `finish-task` (включает `verification-before-completion`, `PROJECT_CONTEXT.md` и цепочку PR) → встроенные `code-review` / `security-review` по необходимости. Для чистки изменённого кода — встроенный `simplify` или субагент `code-simplifier` |
| Postman Flows | `flows` |
| Поиск публичных API или сущностей Postman | `api-discovery` |
| Насколько коллекция или спецификация понятна AI-агентам | `ai-readiness` |
| Работа через инструменты Postman MCP | `postman-mcp-server` |
| Нужна возможность, которой нет ни в одном навыке | `find-skills` (только предложить установку, не ставить без согласия) |
| Создать или улучшить навык | `skill-creator` (встроенный навык Claude Code) |

Если запрос смешанный, разбей его на части и пройди строки по очереди.
Пример: «добавь эндпоинт истории заказов и проверь в браузере» →
`api-builder` → `java-backend-skills:spring-web` → `api-testing` → `webapp-testing` →
`verification-before-completion`.

Для чистых вопросов, объяснений в режиме «Обучение» и однострочных правок
навыки читать не нужно, хватает своих знаний и кода. Исключение — когда
объяснение должно точно передать правило навыка.

## Каталог

### Postman-навыки — `postman/skills/` (в репозитории)

| Навык | Путь | Когда |
| --- | --- | --- |
| api-builder | `postman/skills/api-builder/SKILL.md` | **Точка входа для любой нетривиальной API-задачи по `AGENTS.md`**: сначала контракт, потом код, потом публикация |
| api-engineer | `postman/skills/api-engineer/SKILL.md` | Общие принципы API-инженерии; в этом репозитории приоритет у `api-builder` |
| api-testing | `postman/skills/api-testing/SKILL.md` | `postman request`, `postman collection run`, сверка трафика с контрактом |
| api-documentation | `postman/skills/api-documentation/SKILL.md` | Документация из коллекции или OpenAPI, публикация |
| api-mocking | `postman/skills/api-mocking/SKILL.md` | `postman mock`: локальные и облачные mock-серверы, сценарии ошибок |
| api-monitoring | `postman/skills/api-monitoring/SKILL.md` | `postman monitor` / `runner` |
| api-discovery | `postman/skills/api-discovery/SKILL.md` | Поиск API и сущностей Postman, Context Graph |
| bootstrap | `postman/skills/bootstrap/SKILL.md` | Установка и авторизация CLI, привязка workspace (`.postman/`) |
| collection-schema-v3 | `postman/skills/collection-schema-v3/SKILL.md` | Формат YAML-коллекций v3 и окружений |
| ci-integration | `postman/skills/ci-integration/SKILL.md` | Шаги Postman CLI в CI |
| datasets | `postman/skills/datasets/SKILL.md` | `postman dataset`, итерации по данным |
| performance-testing | `postman/skills/performance-testing/SKILL.md` | `postman performance run` |
| flows | `postman/skills/flows/SKILL.md` | `postman flows` |
| ai-readiness | `postman/skills/ai-readiness/SKILL.md` | Оценка готовности API для AI-агентов |
| postman-mcp-server | `postman/skills/postman-mcp-server/SKILL.md` | Понятия Postman и выбор MCP-инструментов (служебный) |

### Пользовательские навыки — `~/.agents/skills/` (вне репозитория)

| Навык | Путь | Когда |
| --- | --- | --- |
| spring-boot-skill | `~/.agents/skills/spring-boot-skill/SKILL.md` | Запасной для Spring Boot 4.x, если нет плагина java-backend-skills |
| systematic-debugging | `~/.agents/skills/systematic-debugging/SKILL.md` | Любая ошибка или падение, до предложения исправления |
| verification-before-completion | `~/.agents/skills/verification-before-completion/SKILL.md` | Перед заявлением «готово», коммитом, PR |
| webapp-testing | `~/.agents/skills/webapp-testing/SKILL.md` | Playwright: проверка локального сайта, скриншоты, логи браузера |
| web-design-guidelines | `~/.agents/skills/web-design-guidelines/SKILL.md` | Аудит UI, доступности и UX |
| frontend-design | `~/.agents/skills/frontend-design/SKILL.md` | Новый или переработанный визуальный дизайн |
| find-skills | `~/.agents/skills/find-skills/SKILL.md` | Поиск и установка недостающих навыков |

### Навыки проекта — `.claude/skills/` (Claude Code видит их сам)

| Навык | Путь | Когда |
| --- | --- | --- |
| project-skills | `.claude/skills/project-skills/SKILL.md` | Этот каталог |
| finish-task | `.claude/skills/finish-task/SKILL.md` | Проверки, `PROJECT_CONTEXT.md`, коммит, PR в цепочке веток |

### Плагины Claude Code (вызываются через Skill)

| Плагин | Что даёт |
| --- | --- |
| java-backend-skills | 15 навыков `java-backend-skills:*` из таблицы выше. Для Java/Spring приоритет у них, `spring-boot-skill` остаётся запасным |
| frontend-design | `frontend-design:frontend-design`, тот же навык, что в `~/.agents/skills/`. Приоритет у плагина |
| code-simplifier | Субагент `code-simplifier`: упрощает недавно изменённый код без изменения поведения |
| playwright | MCP-браузер для интерактивной проверки сайта; готовые сценарии остаются в `tests/browser/` |
| context7 | Актуальная документация Spring Boot 4, React 19, Tailwind 4, Vite 8 — перед API, в котором не уверен |
| feature-dev | `feature-dev:feature-dev` и агенты `code-explorer` / `code-architect` / `code-reviewer` для крупных фич (роли explorer/worker из `AGENTS.md`) |
| pr-review-toolkit | `pr-review-toolkit:review-pr` и агенты по тестам, ошибкам (`silent-failure-hunter`), типам — перед PR с логикой |
| security-guidance | Предупреждения о небезопасном коде во время правок |
| hookify | Локальные правила `.claude/hookify.*.local.md` блокируют продажу CloudShop, удаление журнала, секреты в Git, force/main push |
| jdtls-lsp, typescript-lsp, pyright-lsp | Инструмент LSP: определения, ссылки и ошибки без полной сборки |

Плагины установлены у пользователя, а не в репозитории. На другой машине
их может не быть, тогда используй запасные навыки из каталога.

### Встроенные навыки Claude Code

Они уже есть в списке доступных навыков и вызываются через Skill:
`skill-creator`, `code-review`, `security-review`, `simplify`, `run`, а
также `pdf`, `docx`, `xlsx`, `pptx`, `docs` для документов. Для проекта
полезнее всего `code-review` и `security-review` перед PR и `run` для
запуска приложения. Лаунчеры лежат в `scripts/`: `run_catalog.py`,
`run_site.py`, `run_sync.py`, `build_site.py`, `build_prototype.py`.

## Ограничения проекта, которые важнее любого навыка

- **Postman в облако.** `postman workspace push`, облачные mock-серверы,
  мониторы и публикацию документации запускай только с явного согласия
  пользователя.
- **Коллекция CloudShop остаётся GET-only.** Тест создания продажи
  (`tools/cloudshop/cloudshop_test_sale.py`) требует разрешения владельца.
  Не включай его в массовые прогоны и не повторяй автоматически, если
  результат отправки неясен. Журнал `.cloudshop-test-sales/` защищает от
  повторных продаж, его не удаляют.
- **Нагрузочные тесты и мониторы** создают реальный трафик. Не направляй их
  на CloudShop без согласия: сначала подтверди цель и масштаб.
- **Секреты.** Токены и данные клиентов не попадают в Git, логи, отчёты и
  экспорт окружений. В `CloudShop_Local.postman_environment.json` поля
  учётных данных остаются пустыми.
- **Проверки.** `./mvnw verify` (включая Spotless) и
  `python3 -m unittest discover -s tests -v`. Если тесты с БД пропущены
  из-за отсутствия Docker или `mbm_test`, скажи об этом прямо.
- **Субагенты** в режиме «Разработка»: не больше двух одновременно и без
  общих редактируемых файлов. Передавай им пути нужных навыков из этого
  каталога, потому что сами они навыки не найдут.

## Поддержка каталога

Если навык появился, пропал или переименован, обнови таблицы. Список
можно проверить так:

```sh
ls .claude/skills postman/skills ~/.agents/skills
```

Postman-навыки обновляет `postman skills update`: `api-builder` предлагает
эту команду первым шагом. Она переписывает файлы в `postman/skills/`,
поэтому `postman skills status` можно запускать свободно, а обновление —
только с согласия пользователя и отдельным изменением, не внутри
другой задачи. После обновления сверь описания с каталогом.
