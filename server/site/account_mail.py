"""Письма для подтверждения почты и восстановления аккаунта GleamClient."""

from email.message import EmailMessage
from html import escape


COPY = {
    'ru': {
        'verify': (
            'Подтверждение почты',
            'Подтвердите адрес электронной почты для восстановления доступа к аккаунту GleamClient.',
            'Подтвердить почту',
            'Ссылка действует 24 часа.',
        ),
        'reset': (
            'Сброс пароля',
            'Перейдите по ссылке, чтобы задать новый пароль для аккаунта GleamClient.',
            'Задать новый пароль',
            'Ссылка одноразовая и действует 30 минут.',
        ),
        'changed': (
            'Пароль изменён',
            'Пароль аккаунта GleamClient изменён. При следующем входе используйте новый пароль.',
            '',
            '',
        ),
        'ignore': 'Если вы не отправляли запрос, это письмо можно проигнорировать.',
        'contact': 'Если вы не меняли пароль, обратитесь в',
        'support': 'поддержку GleamClient',
        'account': 'Аккаунт GleamClient',
        'privacy': 'Конфиденциальность',
        'terms': 'Условия использования',
    },
    'en': {
        'verify': (
            'Verify your email',
            'Verify this address so you can use it to recover your GleamClient account.',
            'Verify email',
            'This link expires in 24 hours.',
        ),
        'reset': (
            'Reset your password',
            'Follow the link to set a new password for your GleamClient account.',
            'Set a new password',
            'This single-use link expires in 30 minutes.',
        ),
        'changed': (
            'Your password was changed',
            'Your GleamClient account password has been changed. Sign in with your new password to continue.',
            '',
            '',
        ),
        'ignore': 'If you didn’t request this, you can ignore this email.',
        'contact': 'If someone else changed your password, contact',
        'support': 'GleamClient support',
        'account': 'GleamClient account',
        'privacy': 'Privacy',
        'terms': 'Terms of use',
    },
}
SUPPORT_URL = 'https://discord.gg/JMjFrMWsku'


def account_mail(purpose, language, origin, username, action_url=None):
    copy = COPY[language]
    title, intro, action, expiry = copy[purpose]
    action_html = ''
    footer_html = escape(copy['ignore'])
    if action_url:
        body = f'{intro}\n\n{action}\n{action_url}\n\n{expiry}\n\n{copy["ignore"]}'
        action_html = f'''
        <table role="presentation" align="center" cellspacing="0" cellpadding="0" style="margin:24px auto 0">
          <tr><td align="center" bgcolor="#e7a66e" style="border-radius:8px">
            <a href="{escape(action_url, quote=True)}" style="display:inline-block;border:14px solid #e7a66e;border-left-width:24px;border-right-width:24px;border-radius:8px;color:#22231e;font-size:15px;line-height:20px;font-weight:bold;text-decoration:none">{escape(action)}</a>
          </td></tr>
        </table>
        <p style="margin:12px 0 0;color:#5e6055;font-size:13px;line-height:20px">{escape(expiry)}</p>'''
    else:
        body = f'{intro}\n\n{copy["contact"]} {copy["support"]}.\n{SUPPORT_URL}'
        footer_html = f'{escape(copy["contact"])} <a href="{SUPPORT_URL}" style="color:#86501e;text-decoration:underline">{escape(copy["support"])}</a>.'

    site = escape(origin, quote=True)
    message = EmailMessage()
    message['Subject'] = title
    message.set_content(f'GleamClient\n\n{title}\n{copy["account"]}: {username}\n\n{body}\n')
    message.add_alternative(f'''<!doctype html>
<html lang="{language}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{escape(title)}</title>
</head>
<body style="margin:0;padding:0;background:#eeede8">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" bgcolor="#eeede8">
    <tr><td align="center" style="padding:32px 12px">
      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" bgcolor="#ffffff" style="max-width:480px;border:1px solid #dddcd4;border-radius:12px;font-family:Arial,Helvetica,sans-serif">
        <tr><td align="center" style="padding:23px 20px;border-bottom:1px solid #eeede8">
          <table role="presentation" align="center" cellspacing="0" cellpadding="0"><tr>
            <td valign="middle" style="padding-right:9px">
              <img src="{site}/assets/orion-badge.png" width="35" height="31" alt="" style="display:block;border:0">
            </td>
            <td valign="middle" align="left" style="color:#20211e">
              <span style="display:block;font-size:27px;line-height:24px;font-weight:bold;letter-spacing:-1px">orion</span>
              <span style="display:block;margin-top:4px;color:#5e6055;font-size:9px;line-height:11px;letter-spacing:2.4px">CLIENT</span>
            </td>
          </tr></table>
        </td></tr>
        <tr><td align="center" style="padding:29px 24px 24px;color:#20211e">
          <h1 style="margin:0;font-size:28px;line-height:34px;letter-spacing:-.6px">{escape(title)}</h1>
          <p style="max-width:340px;margin:12px auto 21px;color:#505247;font-size:15px;line-height:23px">{escape(intro)}</p>
          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:340px;margin:0 auto">
            <tr><td align="left" bgcolor="#f5f3ed" style="padding:13px 15px;border:1px solid #e9e5db;border-radius:8px">
              <span style="display:block;color:#5e6055;font-size:12px;line-height:17px">{escape(copy['account'])}</span>
              <strong style="display:block;margin-top:3px;color:#20211e;font-size:15px;line-height:20px;overflow-wrap:anywhere">{escape(username)}</strong>
            </td></tr>
          </table>
          {action_html}
          <p style="max-width:330px;margin:23px auto 0;color:#5e6055;font-size:13px;line-height:20px">{footer_html}</p>
        </td></tr>
        <tr><td align="center" bgcolor="#f8f7f3" style="padding:14px 18px;border-top:1px solid #eeede8;border-radius:0 0 12px 12px;color:#5e6055;font-size:12px;line-height:22px">
          <a href="{site}/privacy.html" style="color:#505247;text-decoration:underline">{escape(copy['privacy'])}</a>
          &nbsp;&nbsp;·&nbsp;&nbsp;
          <a href="{site}/terms.html" style="color:#505247;text-decoration:underline">{escape(copy['terms'])}</a>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>''', subtype='html')
    return message
