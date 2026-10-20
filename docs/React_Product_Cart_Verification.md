# Проверка React-карточки и корзины

Дата: 06.10.2026. [PR #4](https://github.com/qolshatyr/mbm_motors/pull/4), коммит `1bde70f`. Ветка: `feature/react-product-cart`, база — `feature/frontend-foundation` (PR #3).

## Выполнено

Карточка `/products/:id` и корзина `/basket` используют JSON API существующих сервисов. Сохранены серверная сессия, CSRF, расчёт сумм и проверка цены. `currentPrice` передаётся строкой без потери точности. Удаление/очистка имеют подтверждение; POST не повторяется автоматически. После ошибки интерфейс перечитывает фактический снимок.

## Проверки

| Проверка | Результат |
|---|---|
| Maven verify / Spotless | 55 тестов: 54 успешны, 1 Docker-вариант пропущен; реальные PostgreSQL-тесты выполнены на mbm_test |
| Python unittest | 20 успешны |
| TypeScript / Vite build | Успешно, 83 модуля |
| npm audit | 0 известных уязвимостей |
| OpenAPI / collection lint | Локальная проверка успешна; облачные Governance rules недоступны |
| Postman catalog | 6 requests, 13 assertions, 0 failures |
| Postman cart | 8 requests, 17 assertions, 0 failures |
| Chromium catalog | 10 групп успешны; 360/375/768/1440 px |
| Chromium real cart | 6 групп успешны; добавление, deep link, счётчик, валидация, CSRF, отдельные сессии, диалоги и фокус |
| Chromium isolated mbm_test | Количество и суммы, повторная смена цены, уменьшение остатка, неоднозначный ответ POST, устаревание, отсутствие резервирования — успешны |
| Независимое ревью | Существенных дефектов не найдено |

Рабочий каталог перед браузерной проверкой обновлён настоящей GET-only INCREMENTAL синхронизацией: SUCCESS 06.10.2026 18:47:56 UTC, 0 изменений, 8 797 товаров. Рабочая БД при проверке цены и устаревания не редактировалась: использовалась синтетическая mbm_test и отдельный frontend 5175/backend 8081. Эти временные серверы остановлены. Рабочие 8080/5173 оставлены без фоновой синхронизации, данные снова устаревают через 15 минут. Продажи, покупателей и заявки не создавали.

Браузерные проверки сохранены в `tests/browser/check_frontend.py` и `tests/browser/check_product_cart.py`. Изолированные сценарии цены/сбоев выполнены временным `/tmp/mbm-react-price-real.py`; они не входят в постоянный smoke. Дополнительно frontend проверен с моделируемыми ответами: точная цена `3001.0000`, один POST при 403 и получение нового CSRF при ручном повторе.

## Postman: исправление тестового запуска

Первый прогон корзины: 8 requests / 16 assertions / 10 failures — сервер возвращал 403 вместо ожидаемых статусов. Применён systematic-debugging: локальный диагностический proxy выводил только признаки наличия/совпадения токена и cookie, без значений. Подтверждено: CSRF-заголовок совпадает, но CLI не отправляет Cookie, хотя `pm.cookies.has` сообщает её наличие. Коллекция теперь сохраняет cookie собственной сессии из GET CSRF и явно передаёт её в последующих запросах. Серверная защита не ослаблялась. Финальный прогон успешен; заголовок отсутствует в отдельном отрицательном тесте CSRF. Токен/cookie не записаны в файлы, committed variables пустые. По инструкции Postman api-engineer отправлен обезличенный bug report о CLI, без данных проекта.

Коллекция корзины запускается целиком: шаг 01 получает собственную новую сессию. Тесты используют неверный ID/дробное количество и пустую корзину; они не добавляют реальные товары и не создают CloudShop документы. Результаты остаются локально, `--no-report-events`.

## Итоговый вывод CLI

### Каталог

```text
Running your collection...

Root 59757d10b550a0b157ff28ed

  GET http://localhost:8080/api/catalog/products?page=1.5 [400 Bad Request, 822 B, 5 ms]
  Pass  HTTP 400
  Pass  ProblemDetail

Root b88a8103bdfef92059739a0c

  GET http://localhost:8080/api/catalog/products?store=unknown [400 Bad Request, 822 B, 4 ms]
  Pass  HTTP 400
  Pass  ProblemDetail

Root 58f70c42e2669f661a9dfb6a

  GET http://localhost:8080/api/catalog/products?page=1000000 [404 Not Found, 801 B, 9 ms]
  Pass  HTTP 404
  Pass  ProblemDetail

Root 101557c02d2b3bc277116cef

  GET http://localhost:8080/api/catalog/stores [200 OK, 831 B, 3 ms]
  Pass  HTTP 200
  Pass  Список мест хранения

Root 6883ae909dd6b173915a257a

  GET http://localhost:8080/api/catalog/products?q=&store=64feaaa51c9799143b532fcf&available=false&page=0 [200 OK, 6.73 KB, 20 ms]
  Pass  HTTP 200
  Pass  Каталог и пагинация
  Pass  Свежесть и серверное отображение

Root 52508c21ee9d393b33fba36f

  GET http://localhost:8080/api/catalog/products/6a6e12abb0b00726916de87c?store=64feaaa51c9799143b532fcf [200 OK, 1.5 KB, 3 ms]
  Pass  HTTP 200
  Pass  Карточка и предложения

-------------------------------------------------------------------
|                         |          executed |            failed |
--------------------------+-------------------+--------------------
|              iterations |                 1 |                 0 |
--------------------------+-------------------+--------------------
|                requests |                 6 |                 0 |
--------------------------+-------------------+--------------------
|            test-scripts |                 6 |                 0 |
--------------------------+-------------------+--------------------
|      prerequest-scripts |                 0 |                 0 |
--------------------------+-------------------+--------------------
|              assertions |                13 |                 0 |
-------------------------------------------------------------------
| total run duration: 322 ms                                      |
-------------------------------------------------------------------
| total data received: 8.29 KB (approx)                           |
-------------------------------------------------------------------
| average response time: 7 ms [min: 3 ms, max: 20 ms, s.d.: 6 ms] |
-------------------------------------------------------------------
```

### Корзина

```text
Running your collection...

Root 752194a1baac221af7bf6f15

  GET http://localhost:8080/api/csrf [200 OK, 703 B, 5 ms]
  Pass  HTTP 200
  Pass  Маскированный токен и no-store
  Pass  Сессионная cookie получена

Root af046872aadb174c00b84c8c

  GET http://localhost:8080/api/cart [200 OK, 598 B, 3 ms]
  Pass  HTTP 200
  Pass  Пустая сессионная корзина

Root 2a11f1e76c3d49d9d8d1688c

  POST http://localhost:8080/api/cart/add [409 Conflict, 721 B, 3 ms]
  Pass  HTTP 409
  Pass  ProblemDetail

Root c5c0c0705609b9fa8a1086bf

  POST http://localhost:8080/api/cart/update [400 Bad Request, 856 B, 3 ms]
  Pass  HTTP 400
  Pass  ProblemDetail

Root 2568d77f63b7e71dea2d1485

  POST http://localhost:8080/api/cart/confirm-price [409 Conflict, 719 B, 3 ms]
  Pass  HTTP 409
  Pass  ProblemDetail

Root 9989259d40652251d41bbbb4

  POST http://localhost:8080/api/cart/remove [200 OK, 640 B, 3 ms]
  Pass  HTTP 200
  Pass  Пустая сессионная корзина

Root cf96479a016e1aab419486b2

  POST http://localhost:8080/api/cart/clear [200 OK, 626 B, 3 ms]
  Pass  HTTP 200
  Pass  Пустая сессионная корзина

Root 99b36d281dc327e956f51b32

  POST http://localhost:8080/api/cart/clear [403 Forbidden, 697 B, 1 ms]
  Pass  HTTP 403
  Pass  ProblemDetail CSRF

------------------------------------------------------------------
|                         |          executed |           failed |
--------------------------+-------------------+-------------------
|              iterations |                 1 |                0 |
--------------------------+-------------------+-------------------
|                requests |                 8 |                0 |
--------------------------+-------------------+-------------------
|            test-scripts |                 8 |                0 |
--------------------------+-------------------+-------------------
|      prerequest-scripts |                 0 |                0 |
--------------------------+-------------------+-------------------
|              assertions |                17 |                0 |
------------------------------------------------------------------
| total run duration: 332 ms                                     |
------------------------------------------------------------------
| total data received: 1.38 KB (approx)                          |
------------------------------------------------------------------
| average response time: 3 ms [min: 1 ms, max: 5 ms, s.d.: 1 ms] |
------------------------------------------------------------------
```

## Ограничения и следующий шаг

Production-раздача React и fallback для deep links, SEO/SSR ещё не настроены. Следующая работа — локальное оформление: контакты покупателя, снимок корзины, сохранение заявки и защита от повторной отправки. Самовывоз клиент ещё согласует. Отдельные карточки CloudShop уже выбраны, но их создание и отправка продаж пока не подключаются.
