"""Подтверждение контактов Discord и GitHub в профиле Orion."""

import base64
import hashlib
import os
from pathlib import Path
from urllib.parse import urlencode

from aiohttp import ClientSession, ClientTimeout


PROVIDERS = {
    'discord': ('https://discord.com/oauth2/authorize', 'https://discord.com/api/oauth2/token',
                'https://discord.com/api/v10/users/@me', 'username'),
    'github': ('https://github.com/login/oauth/authorize', 'https://github.com/login/oauth/access_token',
               'https://api.github.com/user', 'login'),
}


def configuration(provider, trusted_origins):
    origin = os.environ.get('ORION_SOCIAL_ORIGIN', '').rstrip('/')
    client = os.environ.get(f'ORION_{provider.upper()}_CLIENT_ID')
    secret_file = os.environ.get(f'ORION_{provider.upper()}_SECRET_FILE')
    if not client or not secret_file or not origin.startswith('https://') or origin not in trusted_origins:
        return None
    return client, Path(secret_file), f'{origin}/api/profile/oauth/{provider}/callback'


def authorization_url(provider, config, state, verifier):
    client, _, callback = config
    params = {'client_id': client, 'redirect_uri': callback, 'response_type': 'code', 'state': state}
    if provider == 'discord':
        params['scope'] = 'identify'
    else:
        challenge = hashlib.sha256(verifier.encode('ascii')).digest()
        params.update(code_challenge=base64.urlsafe_b64encode(challenge).decode('ascii').rstrip('='),
                      code_challenge_method='S256', allow_signup='false')
    return PROVIDERS[provider][0] + '?' + urlencode(params)


async def verified_identity(provider, config, code, verifier):
    client, secret_file, callback = config
    _, token_url, user_url, name_key = PROVIDERS[provider]
    data = {'client_id': client, 'client_secret': secret_file.read_text(encoding='utf-8').strip(),
            'code': code, 'redirect_uri': callback, 'grant_type': 'authorization_code'}
    if provider == 'github':
        data['code_verifier'] = verifier
    async with ClientSession(timeout=ClientTimeout(total=9), headers={'User-Agent': 'Orion Client'}) as http:
        async with http.post(token_url, data=data, headers={'Accept': 'application/json'}, allow_redirects=False) as response:
            if response.status != 200:
                return None
            token = (await response.json()).get('access_token')
            if not isinstance(token, str) or not token:
                return None
        async with http.get(user_url, headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/json'},
                            allow_redirects=False) as response:
            if response.status != 200:
                return None
            identity = await response.json()
    provider_id = str(identity.get('id', ''))
    name = identity.get(name_key)
    if not provider_id.isascii() or not provider_id.isdigit() or len(provider_id) > 32:
        return None
    if not isinstance(name, str) or not 1 <= len(name) <= 39:
        return None
    return provider_id, name
