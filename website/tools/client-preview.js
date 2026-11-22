'use strict';

const previewSettings = 'gleam-client-preview-look';
const readSession = (key, fallback) => {
  try { return JSON.parse(sessionStorage.getItem(key)) ?? fallback; }
  catch { return fallback; }
};
const saveSession = (key, value) => sessionStorage.setItem(key, JSON.stringify(value));
const openScreen = screen => parent.postMessage({ gleamPreview: screen }, location.origin);

function connectBridge(handle) {
  window.webkit ??= {};
  window.webkit.messageHandlers ??= {};
  window.webkit.messageHandlers.paradox = { postMessage(message) { handle(JSON.parse(message)); } };
}

function hub() {
  const screens = { clickgui: '/clickgui/', title: '/title/', splash: '/title/?splash', hud: '/hud/', widgets: '/widgets/' };
  const frame = document.querySelector('#screen');
  const tabs = [...document.querySelectorAll('[data-preview]')];
  let reveal;
  frame.addEventListener('load', () => {
    reveal?.cancel();
    if (location.hash === '#splash' || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    reveal = frame.animate([{ opacity: .7 }, { opacity: 1 }], { duration: 160, easing: 'ease-out' });
  });
  function navigate() {
    const name = location.hash.slice(1);
    const selected = Object.hasOwn(screens, name) ? name : 'clickgui';
    for (const tab of tabs) {
      if (tab.dataset.preview === selected) {
        tab.setAttribute('aria-current', 'page');
        frame.title = tab.textContent;
      } else tab.removeAttribute('aria-current');
    }
    frame.src = screens[selected];
  }
  const reload = () => frame.contentWindow.location.reload();
  window.addEventListener('hashchange', navigate);
  window.addEventListener('message', event => {
    const screen = event.data?.gleamPreview;
    if (event.origin === location.origin && event.source === frame.contentWindow && Object.hasOwn(screens, screen)) {
      location.hash = screen;
    }
  });
  document.querySelector('#reload-preview').addEventListener('click', reload);
  new EventSource('/changes').onmessage = reload;
  navigate();
}

async function cropAtlas(url, columns, rows, names, start = 0) {
  const image = new Image();
  image.src = url;
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalWidth / columns;
  canvas.height = rows == null ? canvas.width : image.naturalHeight / rows;
  const ctx = canvas.getContext('2d');
  return names.map((name, index) => {
    const cell = start + index;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(image, (cell % columns) * canvas.width, Math.floor(cell / columns) * canvas.height,
      canvas.width, canvas.height, 0, 0, canvas.width, canvas.height);
    return [name + '.png', canvas.toDataURL()];
  });
}

async function clickGui() {
  connectBridge(() => {});
  gameBackdrop('.window', true);
  const json = async url => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${url} (${response.status})`);
    return response.json();
  };
  const languages = ['en', 'de', 'es'];
  const [fixture, ...translates] = await Promise.all([json('/catalog.json'), ...languages.map(id => json(`lang/${id}.json`))]);
  window.orionTranslations = Object.fromEntries(languages.map((id, index) => [id, translates[index]]));
  const accessories = ['angel', 'dragon', 'halo', 'hat', 'dance'];
  const iconSprites = await Promise.all([
    cropAtlas('module-atlas.png', 8, null, fixture.iconOrder, 1),
    cropAtlas('cosmetic-atlas.png', 5, 2, ['item', 'preview'].flatMap(side => accessories.map(id => `cosmetic-${id}-${side}`)))
  ]);
  const sprites = new Map(iconSprites.flat());
  const replaceSprite = img => {
    const src = img.getAttribute('src');
    const data = src?.startsWith('assets/') && sprites.get(src.slice(7));
    if (data) img.src = data;
  };
  new MutationObserver(records => {
    for (const record of records) for (const node of record.addedNodes) {
      if (node instanceof HTMLImageElement) replaceSprite(node);
      else if (node instanceof Element) node.querySelectorAll('img').forEach(replaceSprite);
    }
  }).observe(document.body, { childList: true, subtree: true });

  const uiCatalog = fixture.catalog;
  const byModule = new Map(uiCatalog.map(item => [item.id, item]));
  const visual = readSession(previewSettings, {});
  const position = readSession('gleam-preview-page', {});
  const look = { theme: 'dark', density: 'normal', toggleStyle: 'label', categoriesExpanded: true,
    windowSize: 'normal', windowScale: 100, accent: '', wingsStyle: 0, cosmeticPet: 'moon_moth',
    windowX: 0, windowY: 0, interfaceSounds: false, ...visual };
  if (new URLSearchParams(location.search).has('glass')) look.theme = 'glass';
  const friends = { signedIn: true, items: [
    { username: 'BirchFox', public_id: 'preview-1', status: 'friend', appearance: { slots: { wings: 'dragon', pet: 'moon_moth' } } },
    { username: 'StoneOwl', public_id: 'preview-2', status: 'friend' },
    { username: 'LeafRunner', public_id: 'preview-3', status: 'incoming' }
  ], worlds: { canHost: true, version: '26.3', port: 25565, invites: [], people: { BirchFox: { status: 'world', world: 'Лесная база', version: '26.3' } } } };
  const configSample = { key: 1, name: 'Выживание', author: 'BirchFox', downloads: 128, modules: ['HUD', 'ZOOM', 'WAYPOINTS'] };
  const configLists = { catalog: [configSample], local: [{ ...configSample, key: 2, name: 'Мои настройки' }], published: [] };
  let sequence = 2;
  const snapshot = { page: position.page === 'module' ? 'all' : position.page || 'all', language: 'ru',
    catalog: uiCatalog, look, enabled: ['HUD', 'ZOOM', 'SKIN_LAYERS'], favorites: ['ZOOM'],
    presets: [], points: [{ key: 1, name: 'База', x: 248, y: 68, z: -132 }], appliedPreset: -1,
    accounts: [{ key: 1, name: 'GleamPreview', uid: 'preview', avatar: 'orion-badge', plan: 'Plus', plusRemaining: '∞' }],
    account: 1, publication: true, friends, chat: { peer: '', items: [], sent: 0 } };

  function renderSnapshot() {
    const language = look.languageChoice;
    snapshot.language = languages.includes(language) ? language : 'ru';
    const dictionary = window.orionTranslations[snapshot.language] ?? {};
    snapshot.catalog = uiCatalog.map(item => ({ ...item, description: dictionary[item.description] ?? item.description }));
    window.paradox.update(structuredClone(snapshot));
  }
  function showConfigs(tab, selected) {
    snapshot.configTab = tab;
    snapshot.configs = configLists[tab];
    snapshot.configSelected = selected;
  }
  function selectChat(name) {
    snapshot.chat = { peer: name, sent: snapshot.chat.sent,
      items: [{ id: 1, own: false, body: 'Привет! Зайдёшь в мой мир?', sent_at: Math.floor(Date.now() / 1000) }] };
  }
  function toggle(list, id) {
    return list.includes(id) ? list.filter(value => value !== id) : [...list, id];
  }
  function showPortrait(message) {
    const stage = document.querySelector(message.friend ? '.friend-model' : '.cosmetic-stage');
    if (!stage || !message.w) return;
    const accessory = cosmeticById(message.items?.at(-1));
    if (!accessory && !message.friend) return;
    if (!accessory) {
      const portrait = document.createElement('span');
      portrait.className = 'friend-initial hero';
      portrait.textContent = message.friend.slice(0, 1);
      stage.replaceChildren(portrait);
      return;
    }
    const item = accessory[0];
    let image = stage.querySelector('img');
    if (image?.dataset.previewItem !== item) {
      image = document.createElement('img');
      image.className = 'cosmetic-art';
      image.alt = '';
      image.dataset.previewItem = item;
      const name = `cosmetic-${item}-${accessory[1] === 'PET' ? 'item' : 'preview'}.png`;
      image.src = sprites.get(name) ?? 'assets/' + name;
      stage.replaceChildren(image);
      appear(image,0,0,150);
    }
    const facing = accessory[1] === 'WINGS' ? 158 : -19;
    const tilt = Math.sin((message.yaw - facing) * Math.PI / 180) * 32;
    image.style.transform = `perspective(430px) rotateY(${tilt}deg) scale(${message.zoom ?? 1})`;
  }

  connectBridge(message => {
    const { action, id, key, index, value, tab } = message;
    const module = byModule.get(id);
    switch (action) {
      case 'cosmetic-preview': showPortrait(message); return;
      case 'section': saveSession('gleam-preview-page', { page: message.page }); return;
      case 'close': openScreen('title'); return;
      case 'look':
        look[key] = key === 'windowScale' ? Number(value) : value;
        saveSession(previewSettings, look);
        break;
      case 'window-position':
        Object.assign(look, { windowX: message.x, windowY: message.y });
        saveSession(previewSettings, look);
        return;
      case 'toggle': snapshot.enabled = toggle(snapshot.enabled, id); break;
      case 'favorite': snapshot.favorites = toggle(snapshot.favorites, id); break;
      case 'module':
        if (!module) return;
        snapshot.module = { id, settings: module.settings, binding: -1 };
        saveSession('gleam-preview-page', { page: 'module', id });
        break;
      case 'setting':
        if (!module?.settings[index]) return;
        module.settings[index].value = value;
        snapshot.module = { id, settings: module.settings, binding: -1 };
        break;
      case 'cosmetic-pet': look.cosmeticPet = look.cosmeticPet === id ? '' : id; break;
      case 'cosmetic-wings':
        if (snapshot.enabled.includes('WINGS') && look.wingsStyle === message.style) {
          snapshot.enabled = snapshot.enabled.filter(item => item !== 'WINGS');
        } else {
          if (!snapshot.enabled.includes('WINGS')) snapshot.enabled.push('WINGS');
          look.wingsStyle = message.style;
        }
        break;
      case 'publication': snapshot.publication = value; break;
      case 'configs': case 'config-select': showConfigs(tab, key); break;
      case 'config-add':
        configLists.local.push({ key: ++sequence, name: message.name, modules: [...snapshot.enabled] });
        showConfigs('local', sequence);
        break;
      case 'config-use': {
        const config = configLists[tab]?.find(item => item.key === key);
        if (config) snapshot.enabled = [...config.modules];
        break;
      }
      case 'friend-profile': friends.profile = { username: message.username, id: 'preview-1', created: '16.11.2026' }; break;
      case 'friend-profile-back': delete friends.profile; break;
      case 'friend-world': {
        const worlds = friends.worlds;
        if (message.command === 'open' || message.command === 'close') {
          worlds.hosting = message.command === 'open';
          worlds.offline = message.offline;
        } else if (message.command === 'invite') {
          worlds.invites = [{ id: 'demo-invite', username: message.target, world: 'Лесная база', version: '26.3', state: 'pending' }];
        } else worlds.invites = [];
        break;
      }
      case 'chat-select': selectChat(message.username); break;
      case 'chat-send':
        snapshot.chat.sent++;
        snapshot.chat.items.push({ id: ++sequence, own: true, body: message.body, sent_at: Math.floor(Date.now() / 1000) });
        break;
      case 'point-add': snapshot.points.push({ key: ++sequence, name: message.name, x: 0, y: 64, z: 0 }); break;
      case 'point-remove': snapshot.points = snapshot.points.filter(point => point.key !== key); break;
      case 'ui-sound': case 'setting-preview': case 'profile': case 'friends-refresh': case 'saved-accounts': return;
      default: toast('Это действие доступно в Minecraft', 'info'); return;
    }
    queueMicrotask(renderSnapshot);
  });
  showConfigs('catalog');
  renderSnapshot();
  if (position.page === 'module' && byModule.has(position.id)) {
    state.module = position.id;
    selectPage('module');
  }
  document.addEventListener('click', () => {
    if (!state.module || state.page !== 'module') saveSession('gleam-preview-page', { page: state.page });
  });
}

function mainMenu() {
  const visual = readSession(previewSettings, {});
  const demo = { light: visual.theme === 'light', accent: visual.accent || '', reveal: 1, loading: false, multiReason: '',
    single: 'Одиночная игра', multi: 'Сетевая игра', modules: 'Модули', settings: 'Настройки Minecraft',
    account: 'GleamPreview', accountType: 'Офлайн-профиль', version: 'Minecraft 26.3', iasAvailable: true,
    minecraftAccounts: 'Аккаунты Minecraft', addMicrosoft: 'Добавить Microsoft', addOffline: 'Добавить офлайн-профиль',
    gleamProfile: 'Профиль GleamClient', iasMissing: 'Нужен In-Game Account Switcher',
    clientSettings: 'Настройки GleamClient', quit: 'Выйти', loadingLabel: 'Загрузка ресурсов' };
  const draw = () => window.paradox.update({ ...demo, width: innerWidth, height: innerHeight,
    themeAction: demo.light ? 'Тёмная тема' : 'Светлая тема' });
  connectBridge(({ action }) => {
    if (action === 'theme') {
      demo.light = !demo.light;
      saveSession(previewSettings, { ...visual, theme: demo.light ? 'light' : 'dark' });
      draw();
    } else if (['modules', 'client-settings', 'account'].includes(action)) {
      saveSession('gleam-preview-page', { page: { modules: 'all', 'client-settings': 'settings', account: 'profile' }[action] });
      openScreen('clickgui');
    }
  });
  addEventListener('resize', draw);
  if (!new URLSearchParams(location.search).has('splash')) { draw(); return; }
  const started = performance.now();
  function animate(now) {
    const seconds = Math.max(0, (now - started) / 1000);
    demo.loading = seconds < 2;
    demo.intro = Math.min(1, seconds / 2);
    demo.progress = Math.floor(demo.intro * 100);
    demo.reveal = Math.min(1, Math.max(0, seconds - 2) * 1.75);
    draw();
    if (demo.reveal < 1) requestAnimationFrame(animate);
  }
  requestAnimationFrame(animate);
}

function gameBackdrop(surface, lumen = false) {
  const world = new URLSearchParams(location.search).get('scene') === 'world';
  const backdrop = world ? '/assets/gameplay-baseline.webp' : '/title/assets/gleam-title-panorama.png';
  const mode = lumen ? (world ? 'world' : 'title') : '';
  const titleCrop = mode === 'title'
    ? 'html[data-theme=glass], html[data-glass] { background-position: left center; background-size: max(138vw, 245.333333vh) auto }' : '';
  const style = document.createElement('style');
  style.textContent = `
    html { background: url(${backdrop}) center / cover fixed }
    ${titleCrop}
    html[data-theme=dark], html[data-theme=light] { background-image: linear-gradient(#10121a55,#10121a55), url(${backdrop}) }
    .preview-glass-layer { position: fixed; inset: 0; width: 100%; height: 100%; pointer-events: none }
    html[data-preview-glass][data-theme=glass] .window,
    html[data-preview-glass][data-glass] .surface { background: transparent }
  `;
  document.head.append(style);
  import('/client-preview-glass.js').then(({ previewGlass }) => previewGlass(surface, backdrop, mode))
    .catch(error => console.warn('Liquid Glass preview:', error));
}

function mediaHud() {
  gameBackdrop('#island .surface', true);
  const visual = readSession(previewSettings, {});
  const tracks = [['Sweden', 'C418'], ['Aria Math', 'C418'], ['Otherside', 'Lena Raine']];
  let index = 0;
  const demo = { hud: true, media: true, active: true, interactive: true, playing: true, glass: true,
    source: 'Spotify', version: '26.3', username: 'GleamPreview', fps: 144, ping: 35,
    light: visual.theme === 'light', accent: visual.accent, duration: 216, position: 48, volume: .65,
    canSeek: true, canVolume: true, canPlay: true, canPrevious: true, canNext: true,
    canShuffle: true, canRepeat: true, shuffle: false, repeat: 0, placement: 'TOP_CENTER' };
  function draw() {
    [demo.title, demo.artist] = tracks[index];
    window.paradox.update({ ...demo });
  }
  connectBridge(({ action, value, x }) => {
    if (action === 'settings') {
      saveSession('gleam-preview-page', { page: 'module', id: 'MEDIA_PLAYER' });
      openScreen('clickgui');
    } else if (action === 'play') {
      demo.position = playbackPosition(performance.now());
      demo.playing = !demo.playing;
    } else if (action === 'next' || action === 'previous') {
      index = (index + tracks.length + (action === 'next' ? 1 : -1)) % tracks.length;
      demo.position = 0;
    } else if (action === 'shuffle') demo.shuffle = !demo.shuffle;
    else if (action === 'repeat') demo.repeat = (demo.repeat + 1) % 3;
    else if (action === 'position') Object.assign(demo, { x, custom: true });
    else if (action === 'seek') demo.position = value;
    else if (action === 'volume') demo.volume = value;
    else return;
    draw();
  });
  draw();
}

function hudWidgets() {
  gameBackdrop('#hud .panel, #hud .key');
  // Same seven keys and design units as OrionStatusHud, enlarged for the browser.
  const size = 17, step = 19;
  const keys = [
    { text: 'W', x: step, y: 0 },
    { text: 'A', x: 0, y: step }, { text: 'S', x: step, y: step }, { text: 'D', x: 2 * step, y: step },
    { text: '', mouse: 'L', x: 0, y: 2 * step },
    { text: '', x: step, y: 2 * step },
    { text: '', mouse: 'R', x: 2 * step, y: 2 * step }
  ];
  document.querySelector('#hud').style.cssText = 'left:10px;top:14px;zoom:2';
  const pressed = new Set();
  const draw = () => window.paradox.update({ glass: true, gleamHud: true,
    parts: keys.map(key => ({ ...key, kind: 'key', radius: 6, w: size, h: size,
      pressed: !key.mouse && pressed.has(key.text || ' ') })) });
  window.addEventListener('keydown', event => { pressed.add(event.key.toUpperCase()); draw(); });
  window.addEventListener('keyup', event => { pressed.delete(event.key.toUpperCase()); draw(); });
  window.addEventListener('blur', () => { pressed.clear(); draw(); });
  draw();
}

if (document.body.hasAttribute('data-preview-hub')) hub();
else if (location.pathname === '/clickgui/') clickGui().catch(error => {
  console.error(error);
  document.querySelector('#content').textContent = 'Не удалось загрузить предпросмотр';
});
else if (location.pathname === '/title/') mainMenu();
else if (location.pathname === '/hud/') mediaHud();
else if (location.pathname === '/widgets/') hudWidgets();
