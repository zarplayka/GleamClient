'use strict';

if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
  const entrance = new IntersectionObserver((entries, observer) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add('section-entered');
      observer.unobserve(entry.target);
    }
  }, { threshold: .12 });
  document.querySelectorAll('.hero-preview, .gameplay-heading, .gameplay-frame, .plans-heading, .plan-card, .download-guide, .faq-heading, .faq-list, .faq-support')
    .forEach(element => entrance.observe(element));
}

const translate = source => window.OrionLocale?.text(source) ?? source;
const menuButton = document.querySelector('.menu-button');
const navigation = document.querySelector('#navigation');
function closeMenu() {
  if (!menuButton || !navigation) return;
  menuButton.setAttribute('aria-expanded', 'false');
  menuButton.setAttribute('aria-label', translate('Открыть меню'));
  navigation.classList.remove('is-open');
}
menuButton?.addEventListener('click', () => {
  const open = menuButton.getAttribute('aria-expanded') !== 'true';
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', translate(open ? 'Закрыть меню' : 'Открыть меню'));
  navigation.classList.toggle('is-open', open);
});
navigation?.querySelectorAll('a').forEach(link => link.addEventListener('click', closeMenu));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && menuButton?.getAttribute('aria-expanded') === 'true') {
    closeMenu();
    menuButton.focus();
  }
});
window.matchMedia('(min-width: 768px)').addEventListener('change', closeMenu);

const playerSearch = document.querySelector('#player-search');
if (playerSearch) {
  const trigger = document.querySelector('#player-search-trigger');
  const input = document.querySelector('#player-search-query');
  const panel = document.querySelector('#player-search-panel');
  const results = document.querySelector('#player-search-results');
  const status = document.querySelector('#player-search-status');
  const count = document.querySelector('#player-search-count');
  input.placeholder = translate('Поиск игроков');
  let timer;
  let controller;

  function search() {
    clearTimeout(timer);
    controller?.abort();
    results.replaceChildren();
    count.textContent = '';
    const query = input.value.trim();
    if (!/^[a-z0-9_]{2,24}$/i.test(query)) {
      status.textContent = translate('Введите хотя бы два символа ника');
      return;
    }
    status.textContent = translate('Ищем игроков…');
    const current = new AbortController();
    controller = current;
    timer = setTimeout(async () => {
      try {
        const response = await fetch('/api/profile-search?q=' + encodeURIComponent(query), {signal: current.signal});
        if (!response.ok) throw new Error('Search failed');
        const {items} = await response.json();
        if (current.signal.aborted) return;
        status.textContent = items.length ? '' : translate('Игроки не найдены');
        count.textContent = items.length ? new Intl.NumberFormat(document.documentElement.lang).format(items.length) : '';
        for (const player of items) {
          const item = document.createElement('li');
          const link = document.createElement('a');
          link.href = '/auth.html?profile=' + encodeURIComponent(player.public_id);
          const avatar = document.createElement('img');
          avatar.alt = '';
          avatar.src = player.has_avatar ? '/api/profile/' + encodeURIComponent(player.public_id) + '/avatar' : '/assets/orion-badge.png';
          const person = document.createElement('span');
          person.className = 'player-search-person';
          const name = document.createElement('strong');
          name.textContent = player.username;
          const id = document.createElement('small');
          id.textContent = '#' + player.public_id;
          person.append(name, id);
          link.append(avatar, person);
          if (player.role === 'owner' || player.role === 'admin') {
            const role = document.createElement('small');
            role.className = 'player-search-role';
            role.textContent = player.role === 'owner' ? 'Owner' : 'Admin';
            link.append(role);
          }
          const arrow = document.createElement('span');
          arrow.className = 'player-search-arrow';
          arrow.setAttribute('aria-hidden', 'true');
          arrow.textContent = '›';
          link.append(arrow);
          item.append(link);
          results.append(item);
        }
      } catch {
        if (!current.signal.aborted) status.textContent = translate('Поиск временно недоступен');
      }
    }, 220);
  }

  function openSearch() {
    if (!panel.hidden) return;
    closeMenu();
    playerSearch.classList.add('is-open');
    panel.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    search();
  }

  function closeSearch() {
    if (panel.hidden) return;
    panel.hidden = true;
    playerSearch.classList.remove('is-open');
    trigger.setAttribute('aria-expanded', 'false');
    clearTimeout(timer);
    controller?.abort();
  }

  trigger.addEventListener('click', () => {
    if (!panel.hidden) {
      closeSearch();
      return;
    }
    openSearch();
    input.focus();
  });
  input.addEventListener('focus', openSearch);
  input.addEventListener('click', openSearch);
  input.addEventListener('input', () => {
    if (panel.hidden) openSearch();
    else search();
  });
  playerSearch.addEventListener('focusout', event => {
    if (!playerSearch.contains(event.relatedTarget)) closeSearch();
  });
  document.addEventListener('pointerdown', event => {
    if (!playerSearch.contains(event.target)) closeSearch();
  });
  playerSearch.addEventListener('keydown', event => {
    if (panel.hidden) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      if (trigger.getClientRects().length) trigger.focus();
      else input.focus();
      closeSearch();
    }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    const links = [...results.querySelectorAll('a')];
    if (!links.length) return;
    const index = links.indexOf(document.activeElement);
    const next = event.key === 'ArrowDown' ? links[Math.min(index + 1, links.length - 1)]
      : index === -1 ? links.at(-1) : links[index - 1] ?? input;
    event.preventDefault();
    next.focus();
  });
  document.addEventListener('keydown', event => {
    if (event.key !== '/' || event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey ||
        event.target.closest?.('input, textarea, select, [contenteditable]')) return;
    event.preventDefault();
    openSearch();
    input.focus();
  });
  document.addEventListener('orion:languagechange', () => {
    input.placeholder = translate('Поиск игроков');
    if (!panel.hidden) search();
  });
}

const plusPeriods = document.querySelector('.plus-periods');
if (plusPeriods) {
  const price = document.querySelector('#plus-price');
  const display = document.querySelector('#plus-price-display');
  const freePrice = document.querySelector('#free-price');
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let currency = 'RUB';
  let rate = 1;
  let currentPrice;
  let frame;

  const formatPrice = amount => currency === 'RUB' ? Math.round(amount) + ' ₽'
    : new Intl.NumberFormat(document.documentElement.lang, {style: 'currency', currency}).format(amount / rate);
  const showPrice = amount => {
    currentPrice = amount;
    display.textContent = formatPrice(amount);
  };

  const updatePlusPrice = animate => {
    const target = Number(plusPeriods.querySelector('input:checked').value);
    cancelAnimationFrame(frame);
    price.value = formatPrice(target);
    if (freePrice) freePrice.textContent = formatPrice(0);
    if (!animate || reducedMotion.matches || currentPrice === target) {
      showPrice(target);
      return;
    }

    const start = currentPrice;
    const startedAt = performance.now();
    const tick = now => {
      const progress = Math.min(1, (now - startedAt) / 440);
      const eased = 1 - (1 - progress) ** 3;
      showPrice(start + (target - start) * eased);
      if (progress < 1) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
  };
  plusPeriods.addEventListener('change', () => updatePlusPrice(true));
  window.addEventListener('pageshow', () => updatePlusPrice(false));
  reducedMotion.addEventListener('change', () => updatePlusPrice(false));
  document.addEventListener('orion:languagechange', () => updatePlusPrice(false));
  updatePlusPrice(false);
  fetch('/api/pricing', {signal: AbortSignal.timeout(6000)})
    .then(response => response.ok ? response.json() : null)
    .then(data => {
      if (!data || !/^[A-Z]{3}$/.test(data.currency) || !(data.rubles_per_unit > 0)) return;
      currency = data.currency;
      rate = data.rubles_per_unit;
      updatePlusPrice(false);
      document.querySelector('#currency-note').hidden = currency === 'RUB';
    }).catch(() => {});
}

const gameplay = document.querySelector('.gameplay-frame');
if (gameplay) {
  const range = gameplay.querySelector('.gameplay-range');
  const baseline = gameplay.querySelector('.gameplay-baseline');
  const orion = gameplay.querySelector('.gameplay-orion');
  const updateSplit = () => gameplay.style.setProperty('--split', range.value + '%');
  range.addEventListener('input', updateSplit);
  window.addEventListener('pageshow', updateSplit);
  const prepareComparison = async () => {
    baseline.loading = 'eager';
    try {
      await baseline.decode();
    } catch {
      return;
    }
    updateSplit();
    baseline.hidden = false;
    range.hidden = false;
    gameplay.classList.add('is-ready');
  };
  if (orion.complete && orion.naturalWidth) prepareComparison();
  else orion.addEventListener('load', prepareComparison, {once: true});
}

function refreshTranslatedLabels() {
  menuButton?.setAttribute('aria-label', translate(menuButton.getAttribute('aria-expanded') === 'true' ? 'Закрыть меню' : 'Открыть меню'));
}
document.addEventListener('orion:languagechange', refreshTranslatedLabels);
refreshTranslatedLabels();

// Нативный select остаётся рабочим запасным вариантом без JavaScript.
document.querySelectorAll('[data-language]').forEach((select, index) => {
  const wrapper = document.createElement('div');
  wrapper.className = 'language-picker';
  const trigger = document.createElement('button');
  trigger.type = 'button';
  trigger.className = 'language-trigger';
  trigger.setAttribute('aria-haspopup', 'listbox');
  trigger.setAttribute('aria-expanded', 'false');
  const list = document.createElement('div');
  list.id = `language-options-${index}`;
  list.className = 'language-options';
  list.setAttribute('role', 'listbox');
  list.hidden = true;
  trigger.setAttribute('aria-controls', list.id);
  const icon = value => value === 'auto'
    ? '<svg class="language-globe" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/></svg>'
    : value === 'de' || value === 'es'
      ? `<i aria-hidden="true" class="language-flag language-flag-${value}"></i>`
      : `<img src="/assets/flag-${value === 'ru' ? 'ru' : 'gb'}.svg" alt="" class="language-flag" width="20" height="14">`;
  const buttons = [...select.options].map(option => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'language-option';
    button.setAttribute('role', 'option');
    button.dataset.value = option.value;
    // Все имена ресурсов и значения здесь заданы приложением.
    button.innerHTML = icon(option.value) + '<span></span><svg class="language-check" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m4 10 4 4 8-8"/></svg>';
    button.addEventListener('click', () => {
      select.value = option.value;
      select.dispatchEvent(new Event('change', {bubbles: true}));
      close(true);
    });
    list.append(button);
    return button;
  });
  function update() {
    const current = select.options[select.selectedIndex];
    trigger.innerHTML = icon(select.value) + '<span></span><svg class="language-chevron" viewBox="0 0 20 20" fill="none" aria-hidden="true"><path d="m5 7 5 5 5-5"/></svg>';
    trigger.querySelector('span').textContent = current.textContent;
    trigger.setAttribute('aria-label', `${translate('Язык сайта')}: ${current.textContent}`);
    list.setAttribute('aria-label', translate('Язык сайта'));
    buttons.forEach((button, i) => {
      button.querySelector('span').textContent = select.options[i].textContent;
      button.setAttribute('aria-selected', String(button.dataset.value === select.value));
      button.tabIndex = -1;
    });
  }
  function close(focus = false) {
    list.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    if (focus) trigger.focus();
  }
  function open() {
    list.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    buttons.find(button => button.dataset.value === select.value)?.focus();
  }
  trigger.addEventListener('click', () => list.hidden ? open() : close());
  trigger.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); open(); }
  });
  list.addEventListener('keydown', event => {
    const current = buttons.indexOf(document.activeElement);
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); return; }
    if (event.key === 'Tab') { close(); return; }
    let next;
    if (event.key === 'ArrowDown') next = (current + 1) % buttons.length;
    if (event.key === 'ArrowUp') next = (current + buttons.length - 1) % buttons.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = buttons.length - 1;
    if (next !== undefined) { event.preventDefault(); buttons[next].focus(); }
  });
  document.addEventListener('pointerdown', event => { if (!wrapper.contains(event.target)) close(); });
  wrapper.addEventListener('focusout', event => { if (!wrapper.contains(event.relatedTarget)) close(); });
  select.before(wrapper);
  wrapper.append(trigger, list);
  select.hidden = true;
  document.addEventListener('orion:languagechange', update);
  update();
});

const headerAccount = document.querySelector('#header-account');
if (headerAccount) {
  const signIn = headerAccount.querySelector('#header-signin');
  const trigger = headerAccount.querySelector('#account-trigger');
  const avatar = trigger.querySelector('.account-avatar img');
  const defaultAvatar = avatar.getAttribute('src');
  avatar.addEventListener('error', () => {
    if (!avatar.classList.contains('custom-avatar')) return;
    avatar.classList.remove('custom-avatar');
    avatar.src = defaultAvatar;
  });
  const panel = headerAccount.querySelector('#account-panel');
  const logout = headerAccount.querySelector('#account-logout');
  const error = headerAccount.querySelector('#account-error');
  let csrf = null;
  let signingOut = false;
  let logoutFailed = false;
  const accountCacheKey = 'orion.header-name';
  try {
    const remembered = sessionStorage.getItem(accountCacheKey);
    if (remembered !== null) {
      headerAccount.querySelectorAll('.account-name').forEach(label => { label.textContent = remembered; });
      signIn.hidden = Boolean(remembered);
      trigger.hidden = !remembered;
    }
  } catch {
    // The session is still checked when storage is unavailable.
  }

  function setPanel(open) {
    panel.hidden = !open;
    trigger.setAttribute('aria-expanded', String(open));
  }

  function updateAccount(session) {
    const name = session?.user?.username || '';
    csrf = name ? session.csrf : null;
    headerAccount.querySelectorAll('.account-name').forEach(label => { label.textContent = name; });
    trigger.title = name;
    const avatarUrl = name && session.avatar_url;
    avatar.classList.toggle('custom-avatar', Boolean(avatarUrl));
    const nextAvatar = avatarUrl || defaultAvatar;
    if (avatar.getAttribute('src') !== nextAvatar) avatar.src = nextAvatar;
    if (!name) setPanel(false);
    signIn.hidden = Boolean(name);
    trigger.hidden = !name;
    try { sessionStorage.setItem(accountCacheKey, name); } catch { /* Storage is optional. */ }
    headerAccount.removeAttribute('aria-busy');
  }

  async function refreshAccount() {
    const previousCsrf = csrf;
    try {
      const response = await fetch('/api/me', {credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(5000)});
      if (!response.ok) return;
      const session = await response.json();
      if (!signingOut && csrf === previousCsrf) updateAccount(session);
    } catch {
      // При недоступном API ссылка на вход остаётся доступной.
    } finally {
      if (headerAccount.hasAttribute('aria-busy')) updateAccount(null);
    }
  }

  function renderLogoutError() {
    error.textContent = logoutFailed ? translate('Не удалось выйти. Попробуй ещё раз') : '';
  }

  trigger.addEventListener('click', () => {
    const open = panel.hidden;
    if (open) closeMenu();
    setPanel(open);
  });
  headerAccount.addEventListener('keydown', event => {
    if (event.key !== 'Escape' || panel.hidden) return;
    event.preventDefault();
    event.stopPropagation();
    setPanel(false);
    trigger.focus();
  });
  headerAccount.addEventListener('focusout', event => {
    if (!headerAccount.contains(event.relatedTarget)) setPanel(false);
  });
  document.addEventListener('pointerdown', event => {
    if (!headerAccount.contains(event.target)) setPanel(false);
  });
  logout.addEventListener('click', async () => {
    if (signingOut || !csrf) return;
    signingOut = true;
    logout.setAttribute('aria-disabled', 'true');
    logoutFailed = false;
    renderLogoutError();
    try {
      const response = await fetch('/api/logout', {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {'Content-Type': 'application/json', 'X-Orion-Request': '1', 'X-CSRF-Token': csrf},
        body: '{}',
        signal: AbortSignal.timeout(10000)
      });
      if (!response.ok && response.status !== 401) throw new Error('logout');
      const restoreFocus = headerAccount.contains(document.activeElement);
      updateAccount(null);
      if (restoreFocus) signIn.focus();
    } catch {
      logoutFailed = true;
      renderLogoutError();
    } finally {
      signingOut = false;
      logout.removeAttribute('aria-disabled');
    }
  });
  document.addEventListener('orion:languagechange', renderLogoutError);
  window.addEventListener('pageshow', event => {
    if (event.persisted) {
      setPanel(false);
      refreshAccount();
    }
  });
  refreshAccount();
}
