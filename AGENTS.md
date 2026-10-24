# Repository Guidelines

## Project Structure & Module Organization

This repository contains a partner-only (B2B) Java 21 / Spring Boot 4.0.8 portal: CloudShop synchronization, warehouse (СКЛАД) catalog, partner sign-in, server-session cart, checkout, a protected staff request view, a React storefront, and delivery of partner orders to CloudShop as unposted sales, enabled only by `delivery.enabled`. See `docs/B2B_Partner_Portal.md`.

- `docs/Autoparts_Technical_Specification.md`: proposed scope, architecture, acceptance criteria, and operational requirements.
- `docs/cloudshop/CloudShop_Postman_Guide.md`: import instructions, manual verification steps, and integration caveats.
- `postman/assets/CloudShop_ReadOnly.postman_collection.json`: six numbered GET requests with response checks.
- `postman/assets/CloudShop_Local.postman_environment.json`: local configuration template; keep credentials empty in committed copies.

- `src/main/java/kz/mbmmotors/`: application modules `catalog`, `cart`, `order`, `delivery`, `alert`, `partner`, `admin`, `cloudshop`, `sync`, and `config`.
- `src/main/resources/`: configuration, Flyway migrations, and static error pages (the interface is the React app in `frontend/`).
- `frontend/`: React, TypeScript, Vite, and Tailwind storefront and staff UI.
- `src/test/java/`: Java tests; `tests/`: Python tool tests; `tests/browser/`: Playwright scenario checks.
- `docs/`: specification and checkout design; `docs/client/`: Notion-ready client questions; `docs/cloudshop/`: verification guides.
- `tools/cloudshop/`: Python verification tools; `scripts/`: application launch/import helpers.
- `deploy/production/`: Docker Compose, Caddy, and backup files for the server; see `docs/Deployment.md`.
- `postman/assets/`: existing v2.1 GET-only collections and the environment template; `postman/collections/`: v3 collections of the application API; `postman/skills/`: Postman skills.
- `.claude/skills/project-skills/SKILL.md`: catalog and routing table of all project and user skills.
- `PROJECT_CONTEXT.md`: current state, decisions, and next steps; `docs/Project_History.md`: archived stage records and historical evidence.
- `.github/workflows/ci.yml`: CI for pushes to `main` and pull requests (`./mvnw verify` with PostgreSQL, frontend build, Python tests).

Keep `.env`, `.cloudshop-audit/`, and `.cloudshop-test-sales/` local and in the repository root. The sale journal is required to prevent repeated submissions. PostgreSQL runs through `compose.yml`; Maven Wrapper and launch configuration stay in the root.

## Build, Test, and Development Commands

Run commands from the repository root:

```sh
./mvnw verify
python3 -m unittest discover -s tests -v
python3 scripts/run_catalog.py
```

Frontend and the packaged site (Node.js >= 22.12):

```sh
(cd frontend && npm ci && npm run build)
python3 scripts/build_site.py
python3 scripts/run_site.py
```

Browser scenario checks live in `tests/browser/`; `tests/browser/run_checks.py` starts an isolated server on `mbm_test` with synthetic fixtures (see `docs/Browser_Checks.md`). Use the `project-skills` skill to pick skills for a task and the `finish-task` skill to verify, update `PROJECT_CONTEXT.md`, commit, and open a pull request.

See `docs/Development_Guide.md` for PostgreSQL setup and import commands; `README.md` is the repository overview. The catalog launcher requests local credentials privately. Database tests require Docker or a separate `mbm_test` database; report skipped tests explicitly. Validate edited JSON:

```sh
python3 -m json.tool postman/assets/CloudShop_ReadOnly.postman_collection.json > /dev/null
python3 -m json.tool postman/assets/CloudShop_Local.postman_environment.json > /dev/null
```

For API verification, import both JSON files into Postman, select the supplied environment, and follow `docs/cloudshop/CloudShop_Postman_Guide.md`. Supply credentials and verified store/product IDs locally. Keep documented build and test commands aligned with the application.

## Coding Style & Naming Conventions

Use two-space indentation in JSON and preserve the Postman collection v2.1 structure. Keep request names numbered (`01 — Магазины`) to preserve the verification sequence. Maintain the existing Russian language in project documents and request descriptions. Use descriptive Markdown headings and fenced examples. Java formatting is checked by Spotless during `./mvnw verify`; use `./mvnw spotless:apply` when needed.

## Testing Guidelines

Java tests use JUnit, Mockito, Spring MVC tests, and PostgreSQL integration tests. Python tools use unittest. JaCoCo reports coverage without a configured minimum. Existing Postman `pm.test` scripts check HTTP 200 and JSON parsing. Run requests in the documented order and manually verify product fields, pagination, and stock for the selected store. Report response codes and sanitized observations; never include tokens or customer data.

## Commit & Pull Request Guidelines

The Git repository starts with the existing application snapshot on `main`; no earlier project history is available. Use concise imperative subjects, such as `Clarify catalog stock verification`. Use task branches and pull requests; see `docs/GitHub_Workflow.md`. Describe changed files, validation performed, related requirements or issues, and unresolved assumptions in pull requests.

## Security & Configuration

Keep API tokens out of Git, browser code, logs, and shared environment exports. Preserve the GET-only collection. The guide's separate sale-creation test requires owner authorization; do not include it in bulk runs or automatically retry uncertain submissions.

<API_TASKS>
Before responding to any non-trivial API engineering task — designing, implementing, mocking, testing, monitoring, documenting, or deploying an API or Postman Flow — read `postman/skills/api-builder/SKILL.md` and follow it. It is the default entry point for API work in this repository. Pure questions and trivial one-line edits do not need it.
</API_TASKS>

# Режимы работы

Поддерживай два режима: «Обучение» и «Разработка».
Если режим не указан, используй «Разработку».
Сохраняй выбранный режим в текущем диалоге до переключения.
Конкретная просьба пользователя имеет приоритет над поведением режима.
Режимы дополняют общие правила и не изменяют разрешения на инструменты.

## Общие правила выбора skills

- Самостоятельно выбирай установленные skills, подходящие задаче
  и текущему режиму. Перед применением прочитай их SKILL.md.
- Каталог навыков и таблица «задача → навыки» —
  `.claude/skills/project-skills/SKILL.md`. Навыки из `postman/skills/`
  и `~/.agents/skills/` агент сам не находит: читай их по путям оттуда.
- Используй только необходимые навыки.
- Проверяй совместимость навыка с версиями языка, фреймворка
  и архитектурой проекта.
- При поиске причины ошибки используй systematic-debugging.
- При работе со Spring Boot используй подходящий Spring skill.
- При проверке поведения веб-приложения используй webapp-testing.
- При проверке удобства и доступности интерфейса используй
  web-design-guidelines.
- При создании или существенном изменении визуального дизайна
  используй frontend-design.
- Перед завершением изменений используй
  verification-before-completion.
- Если подходящего навыка нет, продолжай доступными средствами.
  Сообщай об отсутствии, только если это влияет на результат.
- Кратко сообщай, какие навыки применяешь и зачем.
- Применение навыка само по себе не является разрешением
  изменять файлы, переключать режим или расширять задачу.

## Режим: Обучение

Цель — помочь мне понять материал и научиться решать задачи самостоятельно.

- Сначала объясни идею и необходимые понятия, затем переходи к коду.
- Разбивай сложную тему на небольшие логические шаги.
- Объясняй причины решений, движение данных и связь между
  строками кода.
- Используй небольшие примеры, которые можно запустить и проверить.
- Если я решаю задачу сам, сначала дай подсказку и помоги
  проверить мой подход.
- Если я прямо прошу полное решение или подробный разбор,
  предоставь его.
- При ошибке объясни, где нарушается логика и как её обнаружить.
- После сложного объяснения предложи короткое упражнение
  для закрепления.
- При запросе объяснения не изменяй файлы проекта без просьбы.
- Используй skills для точности объяснений и анализа,
  сохраняя учебный формат ответа.
- Субагентов используй по моей прямой просьбе. По умолчанию
  веди объяснение самостоятельно и последовательно.

## Режим: Разработка

Цель — выполнить задачу и получить проверенный результат.

- Изучи проект, инструкции и способ сборки.
- Для сложной задачи сначала составь краткий план.
- Определи необходимые изменения и реализуй их.
- Самостоятельно выполняй понятные обратимые действия
  в рамках задачи.
- Сохраняй существующие соглашения и пользовательские изменения.
- Проверяй поведение подходящими тестами, сборкой
  или воспроизведением сценария.
- Исправляй найденные проблемы в рамках задачи до завершения.
- Давай технические объяснения, необходимые для оценки решения.
- В конце сообщай, что изменено, как проверено
  и какие ограничения остались.
- Если проверку выполнить нельзя, прямо укажи причину.

### Субагенты в режиме разработки

- Для сложных задач самостоятельно делегируй независимые части
  работы субагентам, когда это улучшает скорость или качество.
- Простые изменения выполняй самостоятельно.
- Выбирай подходящего доступного агента по его описанию.
- Используй explorer для изучения кода, worker — для реализации,
  если эти агенты доступны.
- Делегируй исследование кода, проверку документации,
  реализацию независимых частей и ревью.
- Используй не более двух субагентов одновременно.
- Для каждой подзадачи укажи цель, границы работы
  и ожидаемый результат.
- При делегировании изменений разделяй ответственность
  по файлам. Не поручай нескольким агентам одновременно
  редактировать одни и те же файлы.
- Передавай субагентам необходимые ограничения проекта.
- Кратко сообщай, что делегировано и зачем.
- Перед завершением собери результаты, проверь их согласованность
  и выполни подходящие проверки итогового состояния проекта.
- Ответственность за итоговый результат остаётся
  у основного агента.
