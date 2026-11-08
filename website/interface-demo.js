'use strict';

(async () => {
  const demo = document.querySelector('#interface-demo');
  if (!demo) return;
  const canvas = demo.querySelector('canvas');
  const context = canvas.getContext('2d');
  if (!context) return;

  const nav = demo.querySelector('.demo-nav');
  const controls = demo.querySelector('.demo-cards');
  const pageControls = demo.querySelector('.demo-page-controls');
  const searchLabel = demo.querySelector('.demo-search');
  const search = searchLabel.querySelector('input');
  const scroll = demo.querySelector('.demo-scroll');
  const themes = [...document.querySelectorAll('.hero-preview [data-theme]')];
  const enabled = new Set();
  const favorites = new Set();
  const atlases = new Map();
  const buttons = new Map();
  const pageButtons = new Map();
  const slots = [];
  const sections = [
    ['ALL', 'Все'], ['FAVORITES', 'Избранное'], ['WAYPOINTS', 'Метки'], ['PRESETS', 'Пресеты'],
    ['CONFIGS', 'Конфиги'], ['COMBAT', 'Бой'], ['PLAYER', 'Игрок'], ['MOVEMENT', 'Движение'],
    ['RENDER', 'Визуал'], ['WORLD', 'Мир'], ['MISC', 'Разное'], ['SETTINGS', 'Настройки']
  ];
  const pageLabels = {
    WAYPOINTS: ['Назад к модулям', 'Переключить метки'],
    PRESETS: ['Качество', 'Производительность', 'Баланс', 'Красота'],
    CONFIGS: ['Каталог', 'Мои конфиги', 'Публикации'],
    SETTINGS: ['Предыдущая тема', 'Следующая тема']
  };
  let data, atlas, manifest, assetKey, pageAtlas;
  let moduleIcons, iconCells;
  let section = 'ALL', theme = 'dark', offset = 0, revision = 0;
  let navigationRevision = 0, preset = 0, configTab = 0, waypointModule = -1;

  function place(element, x, y, w, h) {
    const [width, height] = data.size;
    Object.assign(element.style, {
      left: 100 * x / width + '%', top: 100 * y / height + '%',
      width: 100 * w / width + '%', height: 100 * h / height + '%'
    });
  }

  function draw(sx, sy, w, h, dx = sx, dy = sy) {
    const scale = data.scale;
    context.drawImage(atlas, sx * scale, sy * scale, w * scale, h * scale, dx, dy, w, h);
  }

  function selectedModules() {
    const query = search.value.trim().toLowerCase();
    const cards = data.cards.map((module, index) => ({module, index}));
    if (query) return cards.filter(({module}) => module.some((value, index) => index !== 2 && value.toLowerCase().includes(query)));
    return cards.filter(({module, index}) => section === 'ALL' || module[2] === section || (section === 'FAVORITES' && favorites.has(index)))
      .sort((first, second) => {
        const a = first.module[0].toLowerCase(), b = second.module[0].toLowerCase();
        return a < b ? -1 : a > b ? 1 : 0;
      });
  }

  function drawCard(slot, x, y, w, h, cardsY, packedColumns) {
    const state = slot.index * 2 + Number(enabled.has(slot.index));
    const sx = (state % packedColumns) * w, sy = cardsY + Math.floor(state / packedColumns) * h;
    draw(sx, sy, w, h, x, y);
    const cell = iconCells?.[data.cards[slot.index][0]];
    if (moduleIcons && cell !== undefined) {
      const scale = data.scale;
      context.drawImage(atlas, (sx + 32) * scale, (sy + 17) * scale, 1, 1, x + 7, y + 7, 22, 21);
      context.save();
      const cellSize = moduleIcons.naturalWidth / 8;
      context.imageSmoothingEnabled = true;
      context.imageSmoothingQuality = 'high';
      context.drawImage(moduleIcons, cell % 8 * cellSize, Math.floor(cell / 8) * cellSize,
        cellSize, cellSize, x + 7, y + 6, 22, 22);
      context.restore();
    }
    if (favorites.has(slot.index) || slot.element.matches(':hover, :focus-within')) {
      draw(favorites.has(slot.index) ? 12 : 0, data.starsY, 12, 12, x + w - 47, y + 10);
    }
  }

  function drawBrand() {
    const scale = data.scale;
    context.save();
    context.drawImage(atlas, 112 * scale, 6 * scale, 1, 1, 25, 6, 83, 17);
    context.fillStyle = data.ink;
    context.font = '600 10px Onest, sans-serif';
    context.textBaseline = 'middle';
    context.fillText('GleamClient', 26, 15);
    context.drawImage(atlas, 112 * scale, 388 * scale, 1, 1, 25, 391, 78, 19);
    context.font = '400 9px Onest, sans-serif';
    const account = document.documentElement.lang === 'ru' ? 'Аккаунт GleamClient' : 'GleamClient account';
    context.fillText(account, 27, 401, 74);
    context.restore();
  }

  function render() {
    if (!data) return;
    const [width, height] = data.size;
    const pageFrames = data.pages.frames[section];
    controls.hidden = Boolean(pageFrames);
    for (const [key, group] of pageButtons) group.hidden = section !== key;
    search.toggleAttribute('data-filled', Boolean(search.value));
    context.clearRect(0, 0, width, height);
    if (pageFrames) {
      const active = section === 'WAYPOINTS' ? Number(enabled.has(waypointModule))
        : section === 'PRESETS' ? preset : section === 'CONFIGS' ? configTab : 0;
      const [sx, sy] = pageFrames[active];
      const scale = data.scale;
      context.drawImage(pageAtlas, sx * scale, sy * scale, width * scale, height * scale, 0, 0, width, height);
      drawBrand();
      const page = pageButtons.get(section);
      if (section === 'PRESETS' || section === 'CONFIGS') {
        [...page.children].forEach((button, index) => button.setAttribute('aria-pressed', String(index === active)));
      }
      if (section === 'WAYPOINTS') page.children[1].setAttribute('aria-pressed', String(Boolean(active)));
      scroll.hidden = true;
      return;
    }
    const [x, y, w, h, gap, step, columns, visible, cardsY, packedColumns] = data.grid;
    const matches = selectedModules();
    const max = Math.max(0, Math.ceil((matches.length - visible) / columns));
    offset = Math.min(offset, max);
    const [sectionY, navY] = data.views[section];
    const searching = search.value.trim().length > 0;
    const [headingY] = searching ? data.views.SEARCH : data.views[section];
    draw(0, 0, width, height);
    drawBrand();
    draw(0, sectionY, data.sidebar, 18, 0, navY);
    draw(data.sidebar, headingY, width - data.sidebar, y, data.sidebar, 0);
    if (!matches.length) draw(data.sidebar, headingY + y, width - data.sidebar, 27, data.sidebar, y);

    for (const [index, slot] of slots.entries()) {
      const match = matches[offset * columns + index];
      slot.element.hidden = !match;
      if (!match) continue;
      slot.index = match.index;
      slot.toggle.setAttribute('aria-label', match.module[1]);
      slot.toggle.setAttribute('aria-pressed', String(enabled.has(match.index)));
      slot.favorite.setAttribute('aria-label', translate('Избранное') + ' · ' + match.module[1]);
      slot.favorite.setAttribute('aria-pressed', String(favorites.has(match.index)));
      drawCard(slot, x + index % columns * (w + gap), y + Math.floor(index / columns) * step, w, h, cardsY, packedColumns);
    }

    scroll.hidden = max === 0;
    if (max > 0) {
      const track = height - y - 10;
      const thumb = Math.max(16, track * track / (track + max * step));
      context.fillStyle = data.border + '64';
      context.beginPath();
      context.roundRect(data.scrollX, y, 2, track, 1);
      context.fill();
      context.fillStyle = data.accent + 'be';
      context.beginPath();
      context.roundRect(data.scrollX, y + (track - thumb) * offset / max, 2, thumb, 1);
      context.fill();
      scroll.setAttribute('aria-valuemax', max);
      scroll.setAttribute('aria-valuenow', offset);
    }
  }

  async function openSection(key) {
    const current = ++navigationRevision;
    if (data.pages.frames[key]) {
      let keyAtLoad, image;
      do {
        keyAtLoad = assetKey;
        image = await loadImage(keyAtLoad + '-pages');
        if (current !== navigationRevision) return;
      } while (keyAtLoad !== assetKey);
      pageAtlas = image;
    }
    section = key;
    search.value = '';
    offset = 0;
    refreshLabels();
    render();
  }

  function activatePageControl(page, index) {
    if (page === 'WAYPOINTS') {
      if (index === 0) {
        openSection('ALL');
        buttons.get('ALL').focus();
        return;
      }
      if (enabled.has(waypointModule)) enabled.delete(waypointModule);
      else enabled.add(waypointModule);
    } else if (page === 'PRESETS') preset = index;
    else if (page === 'CONFIGS') configTab = index;
    else if (page === 'SETTINGS') {
      loadTheme(theme === 'dark' ? 'light' : 'dark').catch(() => {});
      return;
    }
    render();
  }

  function buildControls() {
    const [width, height] = data.size;
    const [x, y, w, h, gap, step, columns, visible] = data.grid;
    const [searchX, searchY, searchW, searchH] = data.search;
    place(searchLabel, searchX, searchY, searchW, searchH);
    place(scroll, data.scrollX - 4, y, 10, height - y - 10);
    demo.style.aspectRatio = width + ' / ' + height;
    preset = data.pages.defaultPreset;
    waypointModule = data.cards.findIndex(card => card[0] === 'Waypoints');
    for (const [key, label] of sections) {
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.section = key;
      button.addEventListener('click', () => openSection(key).catch(() => {}));
      button.dataset.label = label;
      place(button, 7, data.pages.nav[key] ?? data.views[key][1], data.sidebar - 13, 18);
      nav.append(button);
      buttons.set(key, button);
    }
    for (const [page, rects] of Object.entries(data.pages.actions)) {
      const group = document.createElement('div');
      group.hidden = true;
      rects.forEach((rect, index) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.dataset.label = pageLabels[page][index];
        button.addEventListener('click', () => activatePageControl(page, index));
        place(button, ...rect);
        group.append(button);
      });
      pageButtons.set(page, group);
      pageControls.append(group);
    }
    for (let index = 0; index < visible; index++) {
      const element = document.createElement('div');
      const toggle = document.createElement('button');
      const favorite = document.createElement('button');
      element.className = 'demo-card';
      toggle.type = favorite.type = 'button';
      toggle.className = 'demo-module';
      favorite.className = 'demo-favorite';
      element.append(toggle, favorite);
      place(element, x + index % columns * (w + gap), y + Math.floor(index / columns) * step, w, h);
      Object.assign(favorite.style, {left: (w - 49) / w * 100 + '%', top: 8 / h * 100 + '%', width: 16 / w * 100 + '%', height: 16 / h * 100 + '%'});
      const slot = {element, toggle, favorite, index: 0};
      slots.push(slot);
      toggle.addEventListener('click', () => {
        if (enabled.has(slot.index)) enabled.delete(slot.index);
        else enabled.add(slot.index);
        render();
      });
      favorite.addEventListener('click', () => {
        if (favorites.has(slot.index)) favorites.delete(slot.index);
        else favorites.add(slot.index);
        render();
        if (slot.element.hidden) buttons.get('FAVORITES').focus();
      });
      for (const event of ['pointerenter', 'pointerleave', 'focusin', 'focusout']) {
        element.addEventListener(event, () => requestAnimationFrame(render));
      }
      controls.append(element);
    }
  }

  function refreshLabels() {
    for (const [key, button] of buttons) {
      button.setAttribute('aria-label', translate(button.dataset.label));
      button.setAttribute('aria-pressed', String(section === key));
    }
    pageControls.querySelectorAll('button').forEach(button => button.setAttribute('aria-label', translate(button.dataset.label)));
  }

  function loadImage(key) {
    if (!atlases.has(key)) {
      const image = new Image();
      image.src = 'assets/interface-demo/' + key + '.png';
      atlases.set(key, image.decode().then(() => image).catch(error => {
        atlases.delete(key);
        throw error;
      }));
    }
    return atlases.get(key);
  }

  async function loadTheme(nextTheme = theme) {
    const current = ++revision;
    const lang = document.documentElement.lang === 'ru' ? 'ru' : 'en';
    const key = lang + '-' + nextTheme;
    const image = await loadImage(key);
    const pages = manifest[key].pages.frames[section] ? await loadImage(key + '-pages') : null;
    if (current !== revision) return;
    theme = nextTheme;
    assetKey = key;
    data = manifest[key];
    atlas = image;
    pageAtlas = pages;
    const [width, height] = data.size;
    canvas.width = width * data.scale;
    canvas.height = height * data.scale;
    context.setTransform(data.scale, 0, 0, data.scale, 0, 0);
    demo.style.setProperty('--capture-ink', data.ink);
    demo.style.setProperty('--capture-input', data.input);
    if (!slots.length) buildControls();
    refreshLabels();
    themes.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.theme === theme)));
    render();
  }

  function renderModuleCatalog() {
    const catalog = document.querySelector('.plan-catalog');
    if (!catalog) return;
    const language = document.documentElement.lang === 'ru' ? 'ru' : 'en';
    const cards = manifest[language + '-dark'].cards;
    const groups = document.createDocumentFragment();
    for (const [key, label] of sections) {
      const modules = cards.filter(card => card[2] === key);
      if (!modules.length) continue;
      const group = document.createElement('div');
      const heading = document.createElement('h4');
      const list = document.createElement('ul');
      heading.textContent = translate(label);
      for (const [, name, , , description] of modules) {
        const item = document.createElement('li');
        const title = document.createElement('strong');
        const detail = document.createElement('span');
        title.textContent = name;
        detail.textContent = description;
        item.append(title, detail);
        list.append(item);
      }
      group.append(heading, list);
      groups.append(group);
    }
    catalog.querySelector('.plan-catalog-content').replaceChildren(groups);
    catalog.hidden = false;
  }

  try {
    const response = await fetch('assets/interface-demo/manifest.json?v=game-pages');
    if (!response.ok) return;
    manifest = await response.json();
    const iconImage = new Image();
    iconImage.src = 'assets/interface-demo/module-icons.png?v=64';
    await Promise.all([
      iconImage.decode().then(() => { moduleIcons = iconImage; }),
      fetch('assets/interface-demo/icon-cells.json').then(response => response.json()).then(cells => { iconCells = cells; })
    ]).catch(() => {});
    renderModuleCatalog();
    document.addEventListener('orion:languagechange', renderModuleCatalog);
    await loadTheme();
  } catch {
    return;
  }

  search.addEventListener('input', () => {
    navigationRevision++;
    if (data.pages.frames[section]) {
      section = 'ALL';
      refreshLabels();
    }
    offset = 0;
    render();
  });
  controls.addEventListener('wheel', event => {
    if (!event.deltaY) return;
    const before = offset;
    offset = Math.max(0, offset + Math.sign(event.deltaY));
    render();
    if (offset !== before) event.preventDefault();
  }, {passive: false});
  scroll.addEventListener('pointerdown', event => {
    const bounds = scroll.getBoundingClientRect();
    const max = Number(scroll.getAttribute('aria-valuemax'));
    offset = Math.max(0, Math.min(max, Math.round(max * (event.clientY - bounds.top) / bounds.height)));
    render();
  });
  scroll.addEventListener('keydown', event => {
    const max = Number(scroll.getAttribute('aria-valuemax'));
    const next = {ArrowUp: offset - 1, ArrowDown: offset + 1, Home: 0, End: max}[event.key];
    if (next === undefined) return;
    event.preventDefault();
    offset = Math.max(0, Math.min(max, next));
    render();
  });
  for (const button of themes) button.addEventListener('click', () => loadTheme(button.dataset.theme).catch(() => {}));
  document.addEventListener('orion:languagechange', () => loadTheme().catch(() => {}));
  document.querySelector('#interface-image').hidden = true;
  document.querySelector('#demo-label').hidden = false;
  document.querySelector('.hero-preview .theme-switch').hidden = false;
  demo.hidden = false;
})();
