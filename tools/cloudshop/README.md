# Инструменты проверки CloudShop

Это вспомогательные инструменты проверки интеграции, а не backend сайта. Запускайте их из корня проекта. Шаблон окружения и GET-коллекции находятся в `postman/assets/`; токен вводится локально и скрыто.

| Инструмент | Назначение |
|---|---|
| `cloudshop_check_stores.py` | Чтение списка мест |
| `cloudshop_find_test_product.py` | Поиск контрольного товара |
| `cloudshop_check_sale_fields.py` | Чтение полей для проверенной тестовой продажи |
| `cloudshop_verify_test_sale.py` | Чтение существующей тестовой продажи |
| `cloudshop_check_sync.py` | GET-проверки методов синхронизации |
| `cloudshop_audit_catalog.py` | Аудит полного каталога |
| `cloudshop_inspect_catalog_fields.py` | Чтение неоднозначных полей из сводки аудита |
| `cloudshop_check_updated_completeness.py` | Сравнение updated с полным каталогом |
| `cloudshop_snapshot_catalog.py` | Снимок и сравнение каталога |
| `cloudshop_verify_time_test.py` | Чтение существующего теста времени |
| `cloudshop_test_sale.py` | Предпросмотр и исторический инструмент записи; не включать в массовые прогоны |

Например, чтение мест:

```sh
python3 tools/cloudshop/cloudshop_check_stores.py
```

Локальные тесты используют имитацию HTTP-клиента и не обращаются к CloudShop:

```sh
python3 -m unittest discover -s tests -v
```

`.cloudshop-test-sales/` и `.cloudshop-audit/` остаются в корне проекта. Журнал тестовых продаж предотвращает повтор отправки: не удаляйте и не переносите его ради нового запуска. Тестовые продажи уже существуют; любое новое внешнее создание требует отдельного согласованного сценария. Не добавляйте `--send` в обычные проверки.

Подробные команды: [CLI-инструкция](../../docs/cloudshop/CloudShop_Postman_CLI_Guide.md). Рабочее приложение запускается через `scripts/run_catalog.py`, импорт — через `scripts/run_sync.py`.
