"""Poll FunPay as the seller and reconcile built-in delivery with Gleam."""
import hashlib
import json
import logging
import os
import re
import time
from tempfile import NamedTemporaryFile
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import urlsplit

import requests


LOG = logging.getLogger('gleam.funpay')
PLANS = {
    'month': ('1 месяц', '1 month'),
    'quarter': ('3 месяца', '3 months'),
    'year': ('1 год', '1 year'),
    'lifetime': ('навсегда', 'lifetime'),
}
PRODUCT = 'GleamClient Plus'
KEY = re.compile(r'GLP-[A-F0-9]{32}')
SESSION_VALUE = re.compile(r'[A-Za-z0-9_,-]{12,256}')
STATUSES = {'PAID': 'paid', 'CLOSED': 'closed', 'REFUNDED': 'refunded', 'PARTIALLY_REFUNDED': 'review'}


class SiteError(Exception):
    def __init__(self, status):
        self.status = status


class Site:
    def __init__(self, origin, token):
        self.origin = origin
        self.session = requests.Session()
        self.session.headers.update({'Authorization': 'Bearer ' + token, 'Origin': origin, 'X-Orion-Request': '1'})

    def call(self, path, payload=None):
        response = self.session.request('GET' if payload is None else 'POST', self.origin + '/api/funpay/worker/' + path,
                                        json=payload, timeout=20, allow_redirects=False)
        if response.status_code != 200:
            raise SiteError(response.status_code)
        return response.json()


def new_lot_fields(account, node, choices):
    from bs4 import BeautifulSoup
    from FunPayAPI.common.enums import SubCategoryTypes
    from FunPayAPI.common.utils import parse_currency
    from FunPayAPI.types import LotFields

    response = account.method('get', f'lots/offerEdit?node={node}', {}, {}, raise_not_200=True)
    form = BeautifulSoup(response.content, 'lxml').select_one('form.form-offer-editor')
    if not form:
        raise ValueError('FunPay did not return a listing form')
    fields = {}
    for field in form.select('input[name]:not([disabled]), textarea[name]:not([disabled])'):
        value = field.get_text() if field.name == 'textarea' else field.get('value', '')
        if field.get('type') == 'checkbox':
            value = 'on' if field.has_attr('checked') else ''
        fields[field['name']] = value
    for select in form.select('select[name]:not([disabled])'):
        name = select['name']
        group = select.find_parent(class_='form-group')
        if group and 'hidden' in group.get('class', ()):
            continue
        options = select.select('option:not([disabled])')
        if name in choices:
            option = next((item for item in options if item.get('value') == choices[name]), None)
            if option is None:
                raise ValueError(f'Configured FunPay option is unavailable: {name}')
        else:
            option = next((item for item in options if item.has_attr('selected')), options[0] if options else None)
        if option is not None:
            fields[name] = option.get('value', option.get_text(strip=True))
    if not choices.keys() <= fields.keys() or fields.get('node_id') != str(node) or fields.get('offer_id') not in ('', '0'):
        raise ValueError('Unexpected FunPay listing form')
    currency = form.select_one('.form-control-feedback')
    if not currency or 'auto_delivery' not in fields:
        raise ValueError('FunPay listing form has no currency or automatic delivery')
    category = account.get_subcategory(SubCategoryTypes.COMMON, node)
    return LotFields(0, fields, subcategory=category, currency=parse_currency(currency.get_text(strip=True)))


def lot_titles(plan, promo):
    russian, english = PLANS[plan]
    return f'{PRODUCT} · {russian} · {promo}', f'{PRODUCT} - {english} - {promo}'


def describe_lot(fields, plan, promo):
    russian, english = PLANS[plan]
    fields.title_ru, fields.title_en = lot_titles(plan, promo)
    fields.description_ru = (
        f'Ключ GleamClient Plus для Minecraft Java Edition\n'
        f'Срок доступа — {russian}\n\n'
        'Что входит в Plus\n'
        '• Значок Plus рядом с ником в меню аккаунтов\n'
        '• До 10 конфигов в каталоге\n'
        '• Возможность оставлять комментарии в профилях игроков\n\n'
        'Как активировать\n'
        '1. После оплаты FunPay автоматически выдаст ключ в заказе.\n'
        '2. Войди в свой аккаунт на gleamclient.xyz и открой «Обзор» в личном кабинете.\n'
        '3. Вставь ключ в блоке GleamClient Plus и нажми «Активировать ключ».'
    )
    fields.description_en = (
        f'GleamClient Plus key for Minecraft Java Edition\n'
        f'Access duration: {english}\n\n'
        'What Plus includes\n'
        '- Plus badge next to your name in the account menu\n'
        '- Up to 10 configs in the catalog\n'
        '- The ability to comment on player profiles\n\n'
        'How to redeem\n'
        '1. After payment, FunPay automatically delivers the key in your order.\n'
        '2. Sign in at gleamclient.xyz and open the Overview tab in your account.\n'
        '3. Paste the key in the GleamClient Plus section and select Redeem key.'
    )


class Worker:
    def __init__(self, site, account, node, rejected_error, choices=None):
        self.site = site
        self.account = account
        self.node = node
        self.rejected_error = rejected_error
        self.choices = choices or {}
        self.cursor = None
        self.details = {}

    def jobs(self):
        from FunPayAPI.common.exceptions import UnauthorizedError

        jobs = self.site.call('jobs')['jobs']
        if not jobs:
            return
        offers = self.account.get_my_subcategory_lots(self.node, locale='ru')
        for job in jobs:
            try:
                self.publish(job, offers)
            except UnauthorizedError:
                raise
            except Exception as exc:
                LOG.warning('Лот %s ожидает сверки (%s)', job['id'], type(exc).__name__)

    def publish(self, job, offers):
        lot_id = job['id']
        path = 'lots/' + lot_id
        titles = lot_titles(job['plan'], job['promo'])
        prefixes = tuple(f'{title}, ' for title in titles) + (f'{PRODUCT} [GL-{lot_id}] ',)

        def matches(offer):
            summary = (offer.description or '').strip()
            return summary in titles or summary.startswith(prefixes)

        existing = [offer for offer in offers if matches(offer)]
        if len(existing) == 1 and job['state'] == 'creating' and existing[0].auto:
            self.site.call(path, {'action': 'link', 'offer_id': int(existing[0].id)})
            return
        if existing or job['state'] != 'queued':
            return
        fields = new_lot_fields(self.account, self.node, self.choices)
        if fields.auto_delivery is None:
            LOG.error('В категории %s нет поля автовыдачи', self.node)
            return
        if fields.currency.name != 'RUB':
            LOG.error('Цена лота в категории %s должна быть в рублях', self.node)
            return
        keys = self.site.call(path, {'action': 'claim'})['keys']
        describe_lot(fields, job['plan'], job['promo'])
        fields.price = job['price'] / 100
        fields.auto_delivery = True
        fields.active = True
        fields.secrets = keys
        try:
            self.account.save_offer(fields)
        except self.rejected_error as exc:
            self.site.call(path, {'action': 'rejected'})
            details = [getattr(exc, 'error_message', None)]
            details.extend(f'{name}: {message}' for name, message in getattr(exc, 'errors', {}).items())
            safe_details = KEY.sub('[ключ скрыт]', ' · '.join(str(detail) for detail in details if detail))
            LOG.warning('FunPay отклонил лот %s (%s)', lot_id, ' '.join(safe_details.split())[:500] or 'причина не указана')
            return
        offers = self.account.get_my_subcategory_lots(self.node, locale='ru')
        confirmed = [offer for offer in offers if matches(offer)]
        if len(confirmed) == 1 and confirmed[0].auto:
            self.site.call(path, {'action': 'link', 'offer_id': int(confirmed[0].id)})

    def sales(self):
        next_page, recent, locale, categories = self.account.get_sales(section=f'lot-{self.node}')
        historical = []
        if self.cursor:
            try:
                cursor, historical, _, _ = self.account.get_sales(
                    start_from=self.cursor, section=f'lot-{self.node}', locale=locale, subcategories=categories)
                self.cursor = cursor if cursor != self.cursor else None
            except Exception as exc:
                self.cursor = None
                LOG.warning('История продаж недоступна (%s)', type(exc).__name__)
        else:
            self.cursor = next_page
        now = time.monotonic()
        self.details = {key: value for key, value in self.details.items() if value[0] > now}
        pending = []
        for shortcut in {order.id: order for order in recent + historical}.values():
            if PRODUCT.casefold() not in (shortcut.description or '').casefold():
                continue
            cached = self.details.get(shortcut.id)
            if not cached or cached[1] != shortcut.status.name:
                pending.append(shortcut)
        for start in range(0, len(pending), 10):
            batch = pending[start:start + 10]
            orders = self.account.get_orders_by_ids(*(shortcut.id for shortcut in batch), include_review=False)
            for shortcut in batch:
                self.reconcile(shortcut, orders.get(shortcut.id), now)

    def reconcile(self, shortcut, order, now):
        if order is None:
            LOG.warning('FunPay не вернул сведения по заказу %s', shortcut.id)
        else:
            try:
                tracked = self.record(order)
                self.details[shortcut.id] = (now + (240 if tracked else 30), shortcut.status.name)
            except Exception as exc:
                LOG.warning('Заказ %s ожидает сверки (%s)', shortcut.id, type(exc).__name__)

    def record(self, order):
        if order.seller_id != self.account.id or order.status.name not in STATUSES:
            return False
        status = STATUSES[order.status.name]
        keys = [key.strip() for key in order.order_secrets]
        if keys and (any(not KEY.fullmatch(key) for key in keys)
                     or (order.amount is not None and len(keys) != order.amount)):
            LOG.warning('Автовыдача заказа %s требует проверки', order.id)
            keys = []
        amount = 0
        if order.currency.name == 'RUB':
            try:
                kopecks = Decimal(str(order.sum)) * 100
                if kopecks.is_finite() and kopecks == kopecks.to_integral_value():
                    amount = int(kopecks)
            except InvalidOperation:
                pass
        if amount <= 0 and status in ('paid', 'closed'):
            LOG.warning('Сумма заказа %s требует проверки', order.id)
            return False
        result = self.site.call('orders/' + order.id, {'status': status, 'amount': amount, 'keys': keys})
        if not result['tracked'] and status in ('paid', 'closed'):
            LOG.warning('Ключи заказа %s пока не сверены', order.id)
        return result['tracked']


def required(name):
    value = os.environ.get(name, '').strip()
    if not value:
        raise SystemExit('Укажите ' + name)
    return value


def read_secret(name):
    value = Path(required(name)).read_text(encoding='utf-8').strip()
    if not value:
        raise SystemExit('Пустой файл ' + name)
    return value


def load_seller_session(path, seed):
    if path:
        try:
            state = json.loads(path.read_text(encoding='utf-8'))
            key, php = state['key'], state['php']
            if (state['seed'] == hashlib.sha256(seed.encode()).hexdigest()
                    and SESSION_VALUE.fullmatch(key) and (not php or SESSION_VALUE.fullmatch(php))):
                return key, php
        except (OSError, ValueError, KeyError, TypeError):
            pass
    return seed, None


def save_seller_session(path, seed, account, previous):
    if not path:
        return previous
    key = account.cookies.get('golden_key') or account.golden_key
    php = account.phpsessid or None
    current = key, php
    if (current == previous or not SESSION_VALUE.fullmatch(key)
            or (php and not SESSION_VALUE.fullmatch(php))):
        return previous
    with NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
        try:
            json.dump({'seed': hashlib.sha256(seed.encode()).hexdigest(), 'key': key, 'php': php}, handle)
            handle.close()
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)
    return current


def main():
    from FunPayAPI import Account
    from FunPayAPI.common.exceptions import LotSavingError, UnauthorizedError

    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    origin = required('GLEAM_FUNPAY_SITE_ORIGIN').rstrip('/')
    parsed = urlsplit(origin)
    local = parsed.scheme == 'http' and parsed.hostname in ('127.0.0.1', 'localhost')
    if not (parsed.scheme == 'https' or local) or not parsed.hostname or parsed.username or parsed.path or parsed.query or parsed.fragment:
        raise SystemExit('GLEAM_FUNPAY_SITE_ORIGIN должен содержать адрес сайта без пути')
    node = int(required('GLEAM_FUNPAY_NODE_ID'))
    if node < 1:
        raise SystemExit('GLEAM_FUNPAY_NODE_ID должен быть положительным')
    token = read_secret('GLEAM_FUNPAY_TOKEN_FILE')
    if len(token) < 32:
        raise SystemExit('Токен между сайтом и ботом должен содержать минимум 32 символа')
    site = Site(origin, token)
    seed = read_secret('GLEAM_FUNPAY_ACCOUNT_KEY_FILE')
    state_file = os.environ.get('GLEAM_FUNPAY_SESSION_STATE_FILE')
    state_path = Path(state_file) if state_file else None
    saved = load_seller_session(state_path, seed)

    def seller_account(key, php):
        account = Account(key, locale='ru', requests_timeout=15)
        account.phpsessid = php
        # The upstream adapter retries POSTs by default. A lost response could create a duplicate lot.
        account.session.mount('https://', requests.adapters.HTTPAdapter(max_retries=0))
        return account

    account = seller_account(*saved)
    choices = {field: value for field, value in (
        ('server_id', os.environ.get('GLEAM_FUNPAY_SERVER_ID')),
        ('fields[type]', os.environ.get('GLEAM_FUNPAY_PRODUCT_TYPE')),
    ) if value}
    worker = Worker(site, account, node, LotSavingError, choices)
    refresh_at = 0
    try_seed = saved != (seed, None)
    while True:
        try:
            if time.monotonic() >= refresh_at:
                account.get(update_phpsessid=account.is_initiated)
                refresh_at = time.monotonic() + 45 * 60
                try_seed = False
                saved = save_seller_session(state_path, seed, account, saved)
            worker.sales()
            saved = save_seller_session(state_path, seed, account, saved)
        except UnauthorizedError:
            refresh_at = 0
            if try_seed:
                account.session.close()
                worker.account = account = seller_account(seed, None)
                try_seed = False
                continue
            LOG.warning('FunPay запросил повторный вход продавца')
        except Exception as exc:
            LOG.warning('Сверка продаж недоступна (%s)', type(exc).__name__)
        try:
            if refresh_at:
                worker.jobs()
                saved = save_seller_session(state_path, seed, account, saved)
        except UnauthorizedError:
            refresh_at = 0
            LOG.warning('FunPay запросил повторный вход продавца')
        except Exception as exc:
            LOG.warning('Обработка лотов недоступна (%s)', type(exc).__name__)
        time.sleep(30)


if __name__ == '__main__':
    main()
