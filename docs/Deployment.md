# Развёртывание партнёрского портала

Бюджетный вариант для первой версии: один VPS, Docker Compose, PostgreSQL, приложение
и Caddy с автоматическим HTTPS. Файлы — в `deploy/production/`. Ничего из этой
инструкции не выполняется автоматически: каждое действие на сервере и в CloudShop
делает человек.

## Сервер

| Параметр | Минимум |
|---|---|
| ОС | Ubuntu 24.04 LTS |
| Ресурсы | 2 vCPU, 2–4 ГБ RAM, 30–40 ГБ SSD |
| Сеть | Публичный IPv4, открыты 22, 80, 443 |

Подойдёт самый дешёвый тариф казахстанского хостера или зарубежного облака с такими
параметрами; цены и тарифы проверьте на сайте провайдера в день заказа. Сервер в
Казахстане упрощает соблюдение требований к хранению персональных данных — уточните
это у владельца.

## Адрес сайта без домена

Пока домена нет, используйте бесплатный адрес `<IP-через-дефисы>.sslip.io`, например
`203-0-113-10.sslip.io`: он указывает на IP сервера, и Caddy получает для него
сертификат Let's Encrypt. Когда домен будет куплен, создайте A-запись на IP сервера,
замените `SITE_ADDRESS` в `.env` и выполните `docker compose up -d caddy`.

## Первая установка

На своей машине соберите JAR с интерфейсом:

```sh
python3 scripts/build_site.py
scp target/mbm-motors-0.0.1-SNAPSHOT.jar user@server:/opt/mbm/app.jar
scp deploy/production/{compose.yml,Caddyfile,backup.sh,env.example} user@server:/opt/mbm/
```

На сервере (Docker Engine с плагином Compose установлен по официальной инструкции Docker):

```sh
cd /opt/mbm
cp env.example .env && chmod 600 .env   # заполнить SITE_ADDRESS, DB_PASSWORD
docker compose up -d
docker compose logs -f app              # дождаться «Started Application»
```

Проверьте `https://<SITE_ADDRESS>/login`: должна открыться страница входа партнёра.

## Каталог

Заполните `CLOUDSHOP_TOKEN` и `CATALOG_SYNC_ENABLED=true`, затем
`docker compose up -d app`. Приложение импортирует товары и обновляет их каждые 5 минут
только запросами GET. На сайте видны только товары СКЛАДА; позиции с ценой 0 ₸
показываются как «Цена уточняется» и не заказываются.

## Партнёры

Учётные записи заводит сотрудник. Пароль вводится скрыто и не попадает в историю
команд:

```sh
read -rsp 'Пароль партнёра: ' MBM_PARTNER_PASSWORD; echo; export MBM_PARTNER_PASSWORD
docker compose run --rm \
  -e MBM_PARTNER_LOGIN=turbo -e MBM_PARTNER_COMPANY='СТО «Turbo»' \
  -e MBM_PARTNER_CONTACT='Ерлан' -e MBM_PARTNER_PHONE='+77011234567' \
  -e MBM_PARTNER_PASSWORD app \
  java -jar /app/app.jar --spring.main.web-application-type=none --partner-add
unset MBM_PARTNER_PASSWORD
```

Чтобы заявки партнёра уходили в CloudShop, ему нужна карточка покупателя. Если карточка
уже создана в CloudShop вручную, привяжите её ID (24 символа из адреса карточки):

```sh
docker compose run --rm -e MBM_PARTNER_LOGIN=turbo \
  -e MBM_CLOUDSHOP_CUSTOMER_ID=<id> app \
  java -jar /app/app.jar --spring.main.web-application-type=none --partner-link-customer
```

Либо создайте карточку через API один раз — `--partner-create-customer` вместо
`--partner-link-customer`, без `MBM_CLOUDSHOP_CUSTOMER_ID`. Если команда сообщила о неясном
ответе, не повторяйте её: найдите карточку в CloudShop и привяжите вручную.

На машине разработчика те же команды выполняют `scripts/partner_add.py` и
`scripts/partner_customer.py`.

## Включение передачи заявок

1. Проверьте, что токен CloudShop разрешает создавать продажи.
2. **С разрешения владельца** проведите одну контрольную заявку: включите
   `MBM_DELIVERY_ENABLED=true`, `docker compose up -d app`, оформите заявку тестовым
   партнёром на дешёвую позицию.
3. В CloudShop найдите непроведённую продажу: склад СКЛАД, покупатель партнёра, в
   комментарии «Заявка MBM-…», контакт и ключ. Сверьте позиции и цены.
4. Если результат неясен (в служебном разделе состояние UNKNOWN), не отправляйте заявку
   повторно: сверьте CloudShop вручную.
5. Если контрольная продажа не нужна, владелец удаляет её в CloudShop сам.

Сайт не проводит продажи и не добавляет оплату; проведение и резерв остаются за
сотрудником в CloudShop.

## Резервные копии

```sh
chmod +x /opt/mbm/backup.sh
crontab -e   # 30 3 * * * /opt/mbm/backup.sh >> /opt/mbm/backup.log 2>&1
```

Копии хранятся 14 дней в `/opt/mbm/backups/`. Раз в неделю копируйте свежий файл на другой
носитель. Проверка восстановления на отдельной базе:

```sh
docker compose exec -T db psql -U mbm -d postgres -c 'create database restore_check'
gunzip -c backups/<файл>.sql.gz | docker compose exec -T db psql -U mbm -d restore_check
```

## Обновление версии

```sh
scp target/mbm-motors-0.0.1-SNAPSHOT.jar user@server:/opt/mbm/app.jar.new
ssh user@server 'cd /opt/mbm && ./backup.sh && mv app.jar.new app.jar && docker compose up -d --force-recreate app'
```

Flyway применяет новые миграции при запуске. Перед обновлением всегда делайте копию базы.
