# Orion Cosmetics — собственный хостинг

Рабочий endpoint: https://orion-cosmetics.51-250-32-198.sslip.io
VPS: 51.250.32.198, SSH-пользователь orionclient

Сервис обслуживает общий значок Orion, ореолы, два вида крыльев, онлайн-присутствие и уведомления об изменениях. Модели и текстуры рендерит Minecraft-клиент; выбранная косметика остаётся в его конфиге и передаётся серверу при подключении. Сервер хранит последние значения в SQLite

## Протокол

- POST /v1/challenge: uuid, name, publicKey — Ed25519 public key в DER/SPKI, base64url
- POST /v1/verify: challenge, signature — одноразовая подпись UTF-8 challenge
- POST /v1/presence: Bearer token; players (до 100 UUID), halo, wings (0/1/2)
- POST /v1/watch: Bearer token; revision, players — ожидание изменения до 20 секунд
- POST /v1/emote: Bearer token; emote (0 — остановка, 1–6 — танец), уведомляет клиентов через revision
- GET /healthz: публичная проверка доступности

Ответ presence содержит online, halos, wings, emotes и revision. В emotes передаются id танца, серверное время started и elapsed в секундах, танец истекает через 12 секунд. UUID в ответах — 32 шестнадцатеричных символа без дефисов, как ожидает OrionBadgePresence

Присутствие истекает через 90 секунд без heartbeat, challenge — через 60 секунд, сессия — через сутки. Токены в базе хранятся в виде SHA-256. Защита challenge подтверждает владение предоставленным ключом; это сохранённый протокол клиента, а не проверка владения аккаунтом Microsoft

## Развёртывание

- Исходники и venv: /opt/orion-cosmetics
- База: /var/lib/orion-cosmetics/state.db
- API слушает только 127.0.0.1:8765
- Caddy публикует HTTPS на 443, перенаправляет HTTP на HTTPS и продлевает сертификат
- systemd unit: orion-cosmetics.service, непривилегированный DynamicUser, автоперезапуск
- Логи: journalctl -u orion-cosmetics; журнал HTTPS — /var/log/caddy/orion-access.log
- Ежедневный backup: orion-cosmetics-backup.timer, 30 дней хранения, /var/backups/orion-cosmetics, доступ только root

Для обновления установите app.py и requirements.txt в /opt/orion-cosmetics, выполните pip install -r requirements.txt внутри установленного venv и перезапустите orion-cosmetics.service. Перед изменением обратного прокси проверяйте Caddyfile командой caddy validate

Для восстановления сначала остановите API, восстановите SQLite через sqlite3.Connection.backup из выбранного снимка и снова запустите API. Не копируйте работающую SQLite-базу обычным cp: она использует WAL

## Проверки

В активном venv: python -m unittest -v test_api

Семь тестов покрывают контракт UUID, подписи и запрет повторного challenge, срок жизни сессий, валидацию, уведомления, offline TTL и перезапуск SQLite. tests/manual/OrionCosmeticsHostingTest.java проверяет публичный HTTPS через production-код Java-клиента

Старые JAR продолжают обращаться к прежнему адресу Sites. Для перехода на общий VPS пользователям нужен обновлённый JAR. Предыдущий сервис автоматически не выключался

SSH-ключи и токены не входят в репозиторий
