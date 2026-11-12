"""Local IP country lookup and indicative prices from the Bank of Russia."""

import asyncio
import ipaddress
import mmap
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from aiohttp import ClientError, ClientSession, ClientTimeout


COUNTRY_DB = Path('/var/lib/orion-site/country-ranges.bin')
EURO = frozenset('AD AT BE BG CY DE EE ES FI FR GR HR IE IT LT LU LV MC ME MT NL PT SI SK SM VA XK'.split())
CURRENCIES = dict(item.split(':') for item in (
    'AM:AMD AU:AUD AZ:AZN BH:BHD BR:BRL BY:BYN CA:CAD CH:CHF CN:CNY CZ:CZK DK:DKK EG:EGP '
    'GB:GBP GE:GEL HK:HKD HU:HUF ID:IDR IN:INR JP:JPY KG:KGS KR:KRW KZ:KZT MD:MDL '
    'MN:MNT MX:MXN MY:MYR NO:NOK NZ:NZD PL:PLN QA:QAR RO:RON RS:RSD SA:SAR SE:SEK '
    'SG:SGD TH:THB TJ:TJS TM:TMT TR:TRY UA:UAH AE:AED US:USD UZ:UZS VN:VND ZA:ZAR'
).split())
RECORD_SIZE = 34
_country_data = None
_rates = {}
_refresh_at = 0
_rate_lock = asyncio.Lock()


def country_for_ip(value):
    global _country_data
    try:
        address = ipaddress.ip_address(value)
        if isinstance(address, ipaddress.IPv6Address) and address.ipv4_mapped:
            address = address.ipv4_mapped
        if _country_data is None:
            with COUNTRY_DB.open('rb') as file:
                _country_data = mmap.mmap(file.fileno(), 0, access=mmap.ACCESS_READ)
    except (ValueError, OSError):
        return None
    data = _country_data
    boundary = int.from_bytes(data[:4], 'big')
    count = (len(data) - 4) // RECORD_SIZE
    low, high = (0, boundary) if address.version == 4 else (boundary, count)
    key = int(address).to_bytes(16, 'big')
    while low < high:
        middle = (low + high) // 2
        offset = 4 + middle * RECORD_SIZE
        if key < data[offset:offset + 16]:
            high = middle
        elif key > data[offset + 16:offset + 32]:
            low = middle + 1
        else:
            return data[offset + 32:offset + 34].decode('ascii')
    return None


async def rates():
    global _rates, _refresh_at
    async with _rate_lock:
        if time.monotonic() < _refresh_at:
            return _rates
        try:
            async with ClientSession(timeout=ClientTimeout(total=4)) as http:
                async with http.get('https://www.cbr.ru/scripts/XML_daily.asp', allow_redirects=False) as response:
                    response.raise_for_status()
                    document = ET.fromstring(await response.read())
            _rates = {
                entry.findtext('CharCode'): float(entry.findtext('Value').replace(',', '.')) / int(entry.findtext('Nominal'))
                for entry in document.findall('Valute')
            }
            _refresh_at = time.monotonic() + 3600
        except (ClientError, asyncio.TimeoutError, ValueError, TypeError, AttributeError, ZeroDivisionError, ET.ParseError):
            _refresh_at = time.monotonic() + 120
        return _rates


async def pricing_for_ip(ip):
    country = country_for_ip(ip)
    currency = 'EUR' if country in EURO else CURRENCIES.get(country, 'RUB')
    rate = (await rates()).get(currency) if currency != 'RUB' else None
    if not rate or rate <= 0:
        return {'currency': 'RUB', 'rubles_per_unit': 1}
    return {'currency': currency, 'rubles_per_unit': rate}
