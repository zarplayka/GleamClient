'use strict';

function createServerBrowser({ byId, showScene, launchScene, reducedMotion }) {
  const catalog = [
    {
      id: 'copper', name: 'Медный берег', address: 'play.coppercoast.example', favorite: true,
      description: 'Ламповое выживание с поселениями, рынком игроков и общей картой. Основной мир живёт без вайпов.',
      image: 'launcher-background-1-21-11.webp', position: '60% 47%', accent: '#f2d09c',
      status: 'online', players: 87, slots: 160, ping: 34, version: '1.21.11'
    },
    {
      id: 'aurora', name: 'Аврора SMP', address: 'mc.aurora.example', favorite: false,
      description: 'Сезонное ванильное выживание. На сервере строят железные дороги, торгуют ресурсами и исследуют мир небольшими командами.',
      image: 'launcher-background-26-1-2.webp', position: '57% 45%', accent: '#ffc3da',
      status: 'online', players: 42, slots: 100, ping: 61, version: '26.1.2'
    },
    {
      id: 'obsidian', name: 'Обсидиан Арена', address: 'arena.obsidian.example:25572', favorite: false,
      description: 'Дуэли, арены и командные турниры. Сейчас сервер обновляет карту и список игровых режимов.',
      image: 'launcher-background-1-21-4.webp', position: '63% 51%', accent: '#f4c4a4',
      status: 'maintenance', players: 0, slots: 120, ping: null, version: '1.21.4'
    },
    {
      id: 'archipelago', name: 'Архипелаг', address: 'isles.archipelago.example', favorite: true,
      description: 'Морское выживание на островах. Соберите команду, найдите тихую бухту и проложите собственные торговые маршруты.',
      image: '../assets/auth-wild.jpg', position: '68% 47%', accent: '#9fe4e0',
      status: 'online', players: 23, slots: 64, ping: 79, version: '26.3'
    },
    {
      id: 'redstone', name: 'Редстоун Лаб', address: 'lab.redstone.example', favorite: false,
      description: 'Творческий полигон для механизмов и автоматических ферм. У каждого игрока есть отдельный участок.',
      image: 'launcher-background-26-2.webp', position: '66% 49%', accent: '#f4de9f',
      status: 'offline', players: 0, slots: 32, ping: null, version: '26.2'
    }
  ];
  const fallbackScene = { id: 'network-custom', image: '../assets/auth-portal.jpg', position: '58% 49%', accent: '#a6d2ff' };
  const statusLabel = {
    online: 'В сети', maintenance: 'Обслуживание', offline: 'Не в сети', unknown: 'Не проверен'
  };
  const byAddress = new Map(catalog.map(server => [server.address, server]));
  const storageKey = 'gleam-worlds-preview-servers';
  const list = byId('server-list');
  const filters = [...document.querySelectorAll('[data-server-filter]')];
  const editor = byId('server-editor');
  const form = byId('server-form');
  const nameInput = byId('input-server-name');
  const addressInput = byId('input-server-address');
  const descriptionInput = byId('input-server-description');
  let servers = loadServers();
  let visible = [];
  let selectedId = servers[0]?.id;
  let filter = 'all';
  let operation = 'add';
  let editingId = null;

  function parseAddress(value) {
    const address = value.trim();
    if (address.length > 259 || !address) return null;

    let host;
    let port;
    if (address.startsWith('[')) {
      const match = /^(\[[a-f0-9:.]+\])(?::([0-9]+))?$/i.exec(address);
      if (!match) return null;
      [, host, port] = match;
    } else if (address.indexOf(':') !== address.lastIndexOf(':')) {
      if (!/^[a-f0-9:.]+$/i.test(address)) return null;
      host = '[' + address + ']';
    } else {
      const match = /^([\p{L}\p{N}.-]+)(?::([0-9]+))?$/u.exec(address);
      if (!match) return null;
      [, host, port] = match;
      host = host.replace(/\.$/, '');
      if (/^[0-9.]+$/.test(host)) {
        const octets = host.split('.');
        if (octets.length !== 4 || octets.some(octet => !/^(0|[1-9]\d{0,2})$/.test(octet) || Number(octet) > 255)) return null;
      }
    }

    if (port && (port.length > 5 || Number(port) < 1 || Number(port) > 65535)) return null;
    try {
      const hostname = new URL('http://' + host + '/').hostname;
      const validLabel = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i;
      if (!hostname.startsWith('[') && (hostname.length > 253 || hostname.split('.').some(label => !validLabel.test(label)))) return null;
      return hostname + (port && Number(port) !== 25565 ? ':' + Number(port) : '');
    } catch {
      return null;
    }
  }

  function loadServers() {
    try {
      const stored = JSON.parse(localStorage.getItem(storageKey));
      if (Array.isArray(stored)) {
        const usedIds = new Set();
        const usedAddresses = new Set();
        return stored.flatMap(record => {
          if (typeof record?.id !== 'string' || typeof record.name !== 'string' || typeof record.address !== 'string') return [];
          const name = record.name.trim().slice(0, 45);
          const address = parseAddress(record.address);
          if (!name || !address || usedIds.has(record.id) || usedAddresses.has(address)) return [];
          usedIds.add(record.id);
          usedAddresses.add(address);
          return [{
            id: record.id, name, address,
            description: typeof record.description === 'string' ? record.description.slice(0, 220) : '',
            favorite: record.favorite === true
          }];
        });
      }
    } catch { /* Local storage may be unavailable in the browser. */ }
    return catalog.map(({ id, name, address, description, favorite }) => ({ id, name, address, description, favorite }));
  }

  function saveServers() {
    try { localStorage.setItem(storageKey, JSON.stringify(servers)); } catch { /* Keep the session usable without storage. */ }
  }

  function activeServer() {
    return visible.find(server => server.id === selectedId);
  }

  function displayStatus(node, status, text) {
    node.dataset.status = status;
    node.textContent = text;
  }

  function paintSelection(animate) {
    const server = activeServer();
    byId('server-feature').hidden = !server;
    if (!server) {
      showScene(fallbackScene);
      return;
    }

    const details = byAddress.get(server.address);
    const status = details?.status || 'unknown';
    byId('server-name').textContent = server.name;
    byId('server-description').textContent = server.description;
    byId('server-description').hidden = !server.description;
    byId('server-address').textContent = server.address;
    byId('server-players').textContent = details ? `${details.players} / ${details.slots}` : '—';
    byId('server-ping').textContent = details?.ping == null ? '—' : `${details.ping} мс`;
    byId('server-version').textContent = details?.version || '—';
    byId('favorite-server').setAttribute('aria-pressed', server.favorite);
    displayStatus(byId('server-status'), status, statusLabel[status]);
    showScene(details ? { ...details, id: 'network-' + details.id } : fallbackScene);

    for (const card of list.children) {
      const current = card.dataset.server === selectedId;
      card.setAttribute('aria-current', current);
      card.tabIndex = current ? 0 : -1;
    }

    if (animate && !reducedMotion.matches) {
      const identity = byId('server-identity');
      identity.getAnimations().forEach(animation => animation.cancel());
      identity.animate([{ opacity: .22, transform: 'translateY(12px)' }, { opacity: 1, transform: 'translateY(0)' }],
        { duration: 410, easing: 'cubic-bezier(.2,.78,.24,1)' });
    }
  }

  function render() {
    visible = filter === 'favorites' ? servers.filter(server => server.favorite) : servers;
    if (!activeServer()) selectedId = visible[0]?.id;
    byId('server-list-title').textContent = filter === 'favorites' ? 'Избранное' : 'Серверы';
    byId('server-count').textContent = visible.length;
    byId('server-empty').hidden = visible.length > 0;
    list.hidden = visible.length === 0;
    for (const button of filters) button.setAttribute('aria-pressed', button.dataset.serverFilter === filter);
    list.replaceChildren(...visible.map(server => {
      const demo = byAddress.get(server.address);
      const card = byId('server-card').content.firstElementChild.cloneNode(true);
      card.dataset.server = server.id;
      const copy = card.querySelector('.server-card-copy');
      copy.querySelector('strong').textContent = server.name;
      copy.querySelector('small').textContent = server.address;
      card.querySelector('.server-card-favorite').hidden = !server.favorite;
      const status = demo?.status || 'unknown';
      displayStatus(card.querySelector('.server-card-status'), status, status === 'online' ? `${demo.players}/${demo.slots}` : statusLabel[status]);
      const art = card.querySelector('.server-card-art');
      art.querySelector('img').hidden = !demo;
      art.querySelector('svg').toggleAttribute('hidden', Boolean(demo));
      if (demo) {
        art.querySelector('img').src = demo.image;
        art.style.setProperty('--cover-position', demo.position);
      }
      return card;
    }));
    paintSelection(false);
  }

  function focusSelected() {
    const card = list.querySelector('[aria-current="true"]');
    if (!card) return;
    card.focus({ preventScroll: true });
    const top = card.getBoundingClientRect().top - list.getBoundingClientRect().top - 5;
    const bottom = top + card.offsetHeight + 10 - list.clientHeight;
    if (top < 0 || bottom > 0) {
      list.scrollBy({ top: top < 0 ? top : bottom, behavior: reducedMotion.matches ? 'instant' : 'smooth' });
    }
  }

  function select(index, focus = false) {
    const server = visible[index];
    if (!server) return;
    const animate = selectedId !== server.id;
    selectedId = server.id;
    paintSelection(animate);
    if (focus) focusSelected();
  }

  function step(delta) {
    select(visible.findIndex(server => server.id === selectedId) + delta, true);
  }

  function setFilter(next) {
    if (filter === next) return;
    filter = next;
    render();
  }

  function connect(server) {
    if (!server) return;
    const demo = byAddress.get(server.address);
    const scene = demo || fallbackScene;
    const result = {
      offline: 'Сервер недоступен в демонстрации',
      maintenance: 'Сервер на обслуживании'
    }[demo?.status] || 'Демонстрационное подключение завершено';
    launchScene(scene, { title: server.name || 'Прямое подключение', address: server.address, message: 'Подключение к серверу…', result });
  }

  function clearError() {
    byId('server-form-error').hidden = true;
    for (const input of [nameInput, addressInput]) {
      input.removeAttribute('aria-invalid');
      input.removeAttribute('aria-describedby');
    }
  }

  function showError(input, message) {
    const error = byId('server-form-error');
    error.hidden = false;
    error.textContent = message;
    input.setAttribute('aria-invalid', 'true');
    input.setAttribute('aria-describedby', error.id);
    input.focus();
  }

  function openEditor(mode, server = null) {
    operation = mode;
    editingId = server?.id || null;
    const direct = mode === 'direct';
    byId('server-editor-title').textContent = { add: 'Добавить сервер', edit: 'Редактировать сервер', direct: 'Прямое подключение' }[mode];
    byId('name-field').hidden = byId('description-field').hidden = direct;
    nameInput.value = server?.name || '';
    addressInput.value = server?.address || '';
    descriptionInput.value = server?.description || '';
    byId('submit-server').textContent = direct ? 'Подключиться' : 'Сохранить';
    clearError();
    editor.showModal();
    (direct ? addressInput : nameInput).focus();
  }

  for (const button of filters) button.addEventListener('click', () => setFilter(button.dataset.serverFilter));
  byId('browse-servers').addEventListener('click', () => { setFilter('all'); focusSelected(); });
  byId('add-server').addEventListener('click', () => openEditor('add'));
  byId('direct-server').addEventListener('click', () => openEditor('direct'));
  byId('edit-server').addEventListener('click', () => openEditor('edit', activeServer()));
  byId('join-server').addEventListener('click', () => connect(activeServer()));
  byId('favorite-server').addEventListener('click', () => {
    const server = activeServer();
    if (!server) return;
    server.favorite = !server.favorite;
    saveServers();
    render();
    if (!activeServer()) filters[1].focus();
  });

  list.addEventListener('click', event => {
    const card = event.target.closest('.server-card');
    if (card) select(visible.findIndex(server => server.id === card.dataset.server));
  });
  list.addEventListener('dblclick', event => {
    if (event.target.closest('.server-card')) connect(activeServer());
  });

  form.addEventListener('input', clearError);
  form.addEventListener('submit', event => {
    event.preventDefault();
    clearError();
    const name = nameInput.value.trim();
    if (operation !== 'direct' && !name) return showError(nameInput, 'Введите название сервера.');
    const address = parseAddress(addressInput.value);
    if (!address) return showError(addressInput, 'Введите домен или IP и при необходимости порт от 1 до 65535.');

    if (operation === 'direct') {
      const known = servers.find(server => server.address === address);
      editor.close();
      connect(known || { address });
      return;
    }

    if (servers.some(server => server.address === address && server.id !== editingId)) {
      return showError(addressInput, 'Сервер с этим адресом уже есть в списке.');
    }

    const record = { name, address, description: descriptionInput.value.trim() };
    const existing = servers.find(server => server.id === editingId);
    if (existing) {
      Object.assign(existing, record);
      selectedId = existing.id;
    } else {
      selectedId = crypto.randomUUID();
      servers.push({ ...record, id: selectedId, favorite: false });
      filter = 'all';
    }
    saveServers();
    editor.close();
    render();
    focusSelected();
  });

  return {
    render,
    step,
    connect: () => connect(activeServer()),
    setFilter,
    back() {
      if (editor.open) editor.close();
      else if (filter === 'favorites') { setFilter('all'); filters[0].focus(); }
      else return false;
      return true;
    },
    onKey(event) {
      if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
        step(event.key === 'ArrowUp' ? -1 : 1);
      } else if (['Home', 'End'].includes(event.key) && list.contains(event.target)) {
        select(event.key === 'Home' ? 0 : visible.length - 1, true);
      } else if (event.key === 'Enter' && (event.target === document.body || list.contains(event.target))) {
        connect(activeServer());
      } else {
        return;
      }
      event.preventDefault();
    }
  };
}
