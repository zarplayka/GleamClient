'use strict';

const paths = {
  grid:'<rect x="4" y="4" width="6" height="6" rx="1"/><rect x="14" y="4" width="6" height="6" rx="1"/><rect x="4" y="14" width="6" height="6" rx="1"/><rect x="14" y="14" width="6" height="6" rx="1"/>',
  search:'<circle cx="10" cy="10" r="5.5"/><path d="m14 14 5 5"/>',
  close:'<path d="m6 6 12 12M18 6 6 18"/>',
  gear:'<path d="m10 3-.7 2.6-2 .9-2.4-.9-2 3.4 1.9 1.8v2.4L2.9 15l2 3.4 2.4-.9 2 .9L10 21h4l.7-2.6 2-.9 2.4.9 2-3.4-1.9-1.8v-2.4l1.9-1.8-2-3.4-2.4.9-2-.9L14 3z"/><circle cx="12" cy="12" r="3"/>',
  star:'<path d="m12 3 2.7 5.4 5.9.9-4.3 4.2 1 5.9-5.3-2.8-5.3 2.8 1-5.9-4.3-4.2 5.9-.9z"/>',
  user:'<circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-4 3-6 7-6s7 2 7 6"/>',
  users:'<circle cx="9" cy="8" r="3.5"/><path d="M2 20a7 6 0 0 1 14 0M16 4.5a3.5 3.5 0 0 1 0 7m2 3a5 5 0 0 1 4 5"/>',
  chat:'<path d="M4 4h16v12H9l-5 4zM8 8h8m-8 4h5"/>',
  smile:'<circle cx="12" cy="12" r="9"/><path d="M8 14c2 3 6 3 8 0M9 9h.01M15 9h.01"/>',
  lock:'<rect x="6" y="10" width="12" height="10" rx="2"/><path d="M9 10V7a3 3 0 0 1 6 0v3m-3 4v2"/>',
  heart:'<path d="M12 20 4.8 12.8a4.6 4.6 0 0 1 6.5-6.5l.7.7.7-.7a4.6 4.6 0 0 1 6.5 6.5Z"/>',
  like:'<path d="M8 20H4V10h4m0 10V10l4-6c2 0 3 2 2 5h4a2 2 0 0 1 2 2l-1 7a2 2 0 0 1-2 2Z"/>',
  laugh:'<circle cx="12" cy="12" r="9"/><path d="m7 10 2-2 2 2m2 0 2-2 2 2M8 14h8c-1 4-7 4-8 0Z"/>',
  surprise:'<circle cx="12" cy="12" r="9"/><path d="M8 9h.01m8 0h.01"/><ellipse cx="12" cy="15" rx="2" ry="2.5"/>',
  sad:'<circle cx="12" cy="12" r="9"/><path d="M9 9h.01m6 0h.01M9 16c1.5-2.5 4.5-2.5 6 0m2-5v2"/>',
  alert:'<path d="M12 3 2 20h20L12 3zm0 6v4m0 3h.01"/>',
  shirt:'<path d="m8 4-5 3 2 5 3-1v9h8v-9l3 1 2-5-5-3a4 4 0 0 1-8 0z"/>',
  pin:'<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0z"/><circle cx="12" cy="10" r="2"/>',
  folder:'<path d="M3 6h7l2 3h9v10H3z"/>',
  layers:'<path d="m3 8 9-5 9 5-9 5-9-5Zm0 4 9 5 9-5M3 16l9 5 9-5"/>',
  monitor:'<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/>',
  target:'<circle cx="12" cy="12" r="7"/><path d="M12 2v5m0 10v5M2 12h5m10 0h5"/>',
  movement:'<path d="m7 17 10-10M8 7h9v9M3 13l5-5m3 13 5-5"/>',
  eye:'<path d="M2 12c5-9 15-9 20 0-5 9-15 9-20 0z"/><circle cx="12" cy="12" r="3"/>',
  cube:'<path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3zM4 7.5l8 4.5 8-4.5M12 12v9"/>',
  copy:'<rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  refresh:'<path d="M20 4v5h-5M4 20v-5h5M5 10a7 7 0 0 1 12-4l3 3M4 15l3 3a7 7 0 0 0 12-4"/>',
  rotate:'<path d="M4 11a8 8 0 1 1 2 7M4 5v6h6"/>',
  send:'<path d="m3 11 17-7-5 16-4-6-8-3zm8 3 5-6"/>',
  back:'<path d="m10 5-7 7 7 7M3 12h18"/>',
  arrow:'<path d="m9 6 6 6-6 6"/>',
  'chevron-down':'<path d="m6 9 6 6 6-6"/>',
  check:'<path d="m5 12 4 4L19 6"/>'
};

let uiLanguage = 'ru';
const t = key => uiLanguage === 'ru' ? key
  : window.orionTranslations?.[uiLanguage]?.[key] ?? window.orionTranslations?.en?.[key] ?? key;

const $ = id => document.getElementById(id);
const text = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const icon = name => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths[name] + '</svg>';
const sections = [
  ['all','Все модули','grid'], ['favorites','Избранное','star'], ['waypoints','Метки','pin'],
  ['presets','Пресеты','monitor'], ['configs','Конфиги','folder'],
  ['cosmetics','Косметика','shirt'], ['friends','Друзья','users'], ['chat','Чат','chat']
];
const categories = [
  ['COMBAT','Бой','target'], ['PLAYER','Игрок','user'], ['MOVEMENT','Движение','movement'],
  ['RENDER','Визуал','eye'], ['WORLD','Мир','cube'], ['MISC','Разное','layers']
];
const names = Object.fromEntries([...sections,...categories,['settings','Настройки клиента'],['profile','Профиль']].map(([id,title]) => [id,title]));
const categoryIds = new Set(categories.map(([id]) => id));
const moduleGroups = {
  BASKETBALL:[[0,'Сетевой матч'],[9,'Как играть'],[13,'Площадка'],[19,'Текущая игра'],[24,'Бросок'],[30,'Физика'],[34,'Вид и звук']],
  FOOTBALL:[[0,'Сетевой матч'],[9,'Как играть'],[14,'Поле'],[20,'Правила игры'],[24,'Удар']],
  HEALTH_TAGS:[[0,'Внешний вид'],[4,'Кого показывать']],
  ARMOR_INDICATOR:[[0,'Внешний вид'],[4,'Кого показывать']],
  ENCHANT_GLINT:[[0,'Редактор переливания'],[6,'Сохранённые переливания']],
  WORLD_CHANGER:[[0,'Небо'],[6,'Туман'],[11,'Облака'],[14,'Пресеты атмосферы']]
};
const catalog = [], ordered = [], byId = new Map();
const quantity = count => count + ' ' + (uiLanguage !== 'ru' ? t(count === 1 ? 'модуль' : 'модулей') : count%10===1 && count%100!==11 ? 'модуль'
  : count%10>=2 && count%10<=4 && (count%100<12 || count%100>14) ? 'модуля' : 'модулей');
const initialEnabled = [];
const accounts = [{key:0,name:'Аккаунт GleamClient',uid:'',avatar:'orion-badge',plan:''}];
const configs = {catalog:[],local:[],published:[]};
const configLoadStatus = {};
const configPages = {};
const presets = [];
const points = [];
let friends = {signedIn:false,busy:false,status:'',items:[]};
let chat = {peer:'',items:[],busy:false,sent:0};
const chatDrafts = new Map();
const chatReplies = new Map();
let chatContextId = '';
const chatRecent = new Map();
let chatUnread = {};
const toggleStyleKey = 'orion-preview-toggle-style';
const windowSizeKey = 'orion-preview-window-size';
const windowScaleKey = 'orion-preview-window-scale';
const accentKey = 'orion-preview-accent';
const windowSizes = [['mini','Мини'],['compact','Компактный'],['normal','Обычный']];
const accentColors = [
  ['', 'По умолчанию', '#3c58e8'], ['#3b82f6','Голубой'], ['#a78bfa','Сиреневый'], ['#f472b6','Розовый'],
  ['#ef6666','Красный'], ['#fb923c','Оранжевый'], ['#e9bc32','Жёлтый'], ['#34d399','Зелёный'], ['#22b8cf','Бирюзовый'], ['#ffffff','Белый']
];
let toggleStyle = 'label';
let windowSize = 'normal', windowScale = 100, accent = '';
try {
  if (localStorage.getItem(toggleStyleKey) === 'switch') toggleStyle = 'switch';
  const savedSize = localStorage.getItem(windowSizeKey);
  if (windowSizes.some(([id]) => id === savedSize)) windowSize = savedSize;
  const savedScale = Number(localStorage.getItem(windowScaleKey));
  if (Number.isInteger(savedScale) && savedScale >= 70 && savedScale <= 160) windowScale = savedScale;
  const savedAccent = localStorage.getItem(accentKey);
  if (savedAccent && /^#[0-9a-f]{6}$/i.test(savedAccent)) accent = savedAccent.toLowerCase();
} catch {}
const state = {
  page:'all',module:null,returnPage:'all',returnScroll:0,query:'',enabled:new Set(initialEnabled),
  favorites:new Set(), values:new Map(), account:0,categoriesExpanded:true,
  configTab:'catalog',config:null,friendTab:null,friendProfile:null,preset:0,appliedPreset:-1,point:null,
  publication:false,publicationReady:true,chatQuery:'',cosmetic:'angel',cosmeticSlot:'all',
  cosmeticQuery:'',cosmeticOnlyEquipped:false,cosmeticYaw:-19,cosmeticZoom:1,wingsStyle:0,
  seq:100,theme:'dark',density:'normal',toggleStyle,windowSize,windowScale,accent,languageChoice:'auto',windowX:0,windowY:0
};
const currentAccount = () => accounts.find(item => item.key === state.account) || accounts[0];
const avatar = (account, variant='') => '<img class="avatar ' + variant + '" src="assets/' + account.avatar + '.png" alt="">';
const itemArt = module => '<img class="item-art" src="assets/' + module.icon + '" alt="" width="34" height="34">';
const actionButton = (action,label,svg='',variant='',disabled=false) =>
  '<button type="button" class="button ' + variant + '" data-action="' + action + '"' + (disabled ? ' disabled' : '')
  + '>' + (svg ? icon(svg) : '') + text(label) + '</button>';
const toggleMarkup = '<span class="switch" aria-hidden="true"></span>';
const toggleIndicator = (style, enabled) => style === 'switch' ? toggleMarkup
  : '<span class="toggle-text" aria-hidden="true">' + (enabled ? 'ON' : 'OFF') + '</span>';
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const motionEase = 'cubic-bezier(.2,.7,.2,1)';

function appear(element, x, y, duration=180, delay=0) {
  if (!element || reducedMotion.matches) return;
  element.animate([
    {opacity:.45,transform:'translate(' + x + 'px,' + y + 'px)'},
    {opacity:1,transform:'translate(0,0)'}
  ],{duration,delay,easing:motionEase,fill:'backwards'});
}

function animatePage(content) {
  if (reducedMotion.matches) return;
  const grid = content.querySelector('.modules-grid');
  if (!grid) {
    for (const part of content.children) appear(part,0,5);
    return;
  }
  appear(grid.previousElementSibling,0,4);
  const columns = getComputedStyle(grid).gridTemplateColumns.split(' ').length;
  for (const [i,card] of Array.from(grid.children).slice(0,12).entries()) {
    const delay = Math.min(72,(i % columns + Math.floor(i / columns)) * 14);
    appear(card,0,5,175,delay);
  }
}

function navItem([id,title,svg], count) {
  const current = state.page === id && !state.query ? ' aria-current="page"' : '';
  return '<button type="button" class="nav-item" data-page="' + id + '"' + current + '>' + icon(svg)
    + '<span>' + text(t(title)) + '</span>' + (count === undefined ? '' : '<small>' + count + '</small>') + '</button>';
}
function renderNavigation() {
  const unread = Object.values(chatUnread).reduce((total,count) => total + count,0);
  $('primary-nav').innerHTML = sections.map(section => navItem(section,
    section[0] === 'favorites' ? state.favorites.size : section[0] === 'chat' && unread ? Math.min(99,unread) : undefined)).join('');
  $('category-items').innerHTML = categories
    .map(category => navItem(category, catalog.filter(module => module.category === category[0]).length)).join('');
}
function accountStatus(account,separator=false) {
  const status = account.badge || account.plan;
  if (!status) return '';
  return '<span class="account-status" data-status="' + text(status.toLowerCase()) + '">'
    + (separator ? '<span class="account-status-dot" aria-hidden="true"></span>' : '')
    + '<span class="plan">' + text(status) + '</span></span>';
}
function renderAccount() {
  const account = currentAccount();
  $('account-trigger').innerHTML = avatar(account) + '<span class="account-name" title="' + text(account.name) + '">' + text(account.name)
    + '</span>' + accountStatus(account,true);
  if (!account.key) {
    $('account-pop').innerHTML = '<div class="account-actions"><button type="button" data-action="login">'
      + icon('user') + text(t('Войти в GleamClient')) + '</button><button type="button" data-action="accounts">' + icon('users') + text(t('Аккаунты')) + '</button></div>';
    return;
  }
  $('account-pop').innerHTML = '<div class="account-head">' + avatar(account,'large') + '<div><b title="' + text(account.name) + '">'
    + text(account.name) + '</b><button type="button" class="uid" data-action="copy" aria-label="' + text(t('Скопировать ID')) + '">ID '
    + text(account.uid) + icon('copy') + '</button></div></div><div class="account-actions">'
    + '<button type="button" data-page="profile">' + icon('user') + text(t('Профиль')) + '</button>'
    + '<button type="button" data-action="my-configs">' + icon('layers') + text(t('Мои конфиги')) + '</button>'
    + '<button type="button" data-action="accounts">' + icon('users') + text(t('Аккаунты')) + '</button></div>';
}
function accountOpen(open) {
  $('account-trigger').setAttribute('aria-expanded',String(open));
  $('account-pop').setAttribute('aria-hidden',String(!open));
  $('account-pop').inert = !open;
  fitCategories();
}
function fitCategories() {
  const nav = $('categories');
  const popup = $('account-pop');
  if ($('account-trigger').getAttribute('aria-expanded') !== 'true') {
    nav.style.maxHeight = '';
    return;
  }
  if (!state.categoriesExpanded) {
    popup.removeAttribute('data-beside-categories');
    nav.style.maxHeight = '';
    return;
  }
  const gap = 2;
  const scale = state.windowScale / 100;
  const labelHeight = nav.firstElementChild.getBoundingClientRect().height / scale;
  const rowHeight = nav.querySelector('.nav-item').getBoundingClientRect().height / scale;
  const available = ($('account').getBoundingClientRect().top - popup.getBoundingClientRect().height
    - nav.getBoundingClientRect().top) / scale - 3;
  const rows = Math.max(0, Math.floor((available - labelHeight) / (rowHeight + gap)));
  // В низком окне переносим меню аккаунта вправо, если над ним нет места даже для одной строки.
  const beside = rows === 0 && document.querySelector('.workspace').getBoundingClientRect().width
    >= $('account').getBoundingClientRect().width + 16 * scale;
  popup.toggleAttribute('data-beside-categories',beside);
  nav.style.maxHeight = beside ? '' : (available < labelHeight ? 0 : labelHeight + rows * (rowHeight + gap)) + 'px';
}
function setCategoriesExpanded(expanded) {
  state.categoriesExpanded = expanded;
  const list = $('category-list');
  list.toggleAttribute('inert',!expanded);
  list.setAttribute('aria-hidden',String(!expanded));
  $('category-toggle').setAttribute('aria-expanded',String(expanded));
  fitCategories();
}
function pageHead(title, subtitle='', trailing='') {
  return '<div class="page-head"><div><h1>' + text(t(title)) + '</h1>' + (subtitle ? '<p>' + text(subtitle) + '</p>' : '') + '</div>' + trailing + '</div>';
}
function empty(title, subtitle) {
  return '<div class="empty">' + icon('search') + '<b>' + text(title) + '</b><span>' + text(subtitle) + '</span></div>';
}
function modulePage() {
  return state.page === 'all' || state.page === 'favorites' || categoryIds.has(state.page);
}
function visibleModules() {
  const query = state.query.trim().toLocaleLowerCase(uiLanguage);
  const isCosmeticsPage = state.page === 'cosmetics';
  const modules = ordered.filter(module => (module.category === 'cosmetics') === isCosmeticsPage);
  if (query) return modules.filter(module =>
    (module.name + ' ' + module.description).toLocaleLowerCase(uiLanguage).includes(query));
  if (state.page === 'favorites') return modules.filter(module => state.favorites.has(module.id));
  return categoryIds.has(state.page) ? modules.filter(module => module.category === state.page) : modules;
}
function moduleCard(module) {
  const id = module.id, name = text(module.name), enabled = state.enabled.has(id);
  return '<article class="module"><button type="button" class="module-main" data-module-toggle="' + id + '" title="'
    + text(module.description) + '" aria-pressed="' + enabled + '">' + itemArt(module) + '<span class="module-name">' + name
    + '</span></button><div class="module-tools">' + (module.category === 'cosmetics' ? '' : '<button type="button" class="favorite" data-favorite="' + id
    + '" aria-label="' + text(t('Избранное') + ':') + ' ' + name + '" aria-pressed="' + state.favorites.has(id) + '" title="' + text(t('Избранное')) + '">' + icon('star') + '</button>')
    + (module.configurable ? '<button type="button" data-module-settings="' + id + '" aria-label="' + text(t('Настройки') + ':') + ' ' + name
    + '" title="' + text(t('Настройки')) + '">' + icon('gear') + '</button>' : '') + '</div><button type="button" class="module-toggle" role="switch"'
    + ' aria-label="' + name + '" aria-checked="' + enabled + '" data-module-toggle="' + id
    + '" data-toggle-style="' + state.toggleStyle + '">' + toggleIndicator(state.toggleStyle,enabled) + '</button></article>';
}
function renderModules() {
  const modules = visibleModules();
  const title = state.query.trim() ? t('Результаты поиска') : names[state.page];
  const subtitle = state.query.trim() ? t('По запросу') + ' «' + state.query.trim() + '»' : '';
  return pageHead(title, subtitle, '<div class="meta">' + quantity(modules.length) + '</div>')
    + (modules.length ? '<div class="modules-grid">' + modules.map(moduleCard).join('') + '</div>'
      : empty(t('Здесь пока пусто'), state.query.trim() ? t('Попробуй другой запрос') : t('Добавь модуль в избранное с помощью звёздочки')));
}
function rangeProgress(min,max,value) {
  return Math.max(0,Math.min(100,100 * (value - min) / (max - min || 1))) + '%';
}
function rangeControl(attrs,min,max,step,value,suffix='') {
  return '<span class="range-control" data-suffix="' + text(suffix) + '" style="--range-progress:' + rangeProgress(min,max,value) + '">'
    + '<span class="range-track" aria-hidden="true"><span class="range-fill"></span><span class="range-thumb"></span></span>'
    + '<input type="range"' + attrs + ' min="' + min + '" max="' + max + '" step="' + step + '" value="' + value + '"></span>';
}
function updateRange(field,suffix=field.parentElement.dataset.suffix ?? '') {
  field.parentElement.style.setProperty('--range-progress',rangeProgress(
    Number(field.getAttribute('min')),Number(field.getAttribute('max')),Number(field.value)));
  field.closest('.range').querySelector('output').textContent = field.value + suffix;
}
function choiceControl(row,id,index,selected) {
  return '<div class="setting-choice"><button type="button" class="button" id="' + id + '" aria-expanded="false"'
    + ' aria-controls="choice-popup" data-choice-toggle="' + index + '"><span>' + text(row.choices[selected] ?? '')
    + '</span>' + icon('chevron-down') + '</button></div>';
}
let openChoice = null;
const languages = [['auto','Как в Minecraft'],['ru','Русский'],['en','English'],['de','Deutsch'],['es','Español']];
function showChoices(trigger,open) {
  const options = $('choice-popup');
  trigger.setAttribute('aria-expanded',String(open));
  if (!open) {
    options.hidden = true;
    openChoice = null;
    return;
  }
  const language = trigger.id === 'language-trigger';
  let count;
  if (language) {
    count = languages.length;
    options.innerHTML = languages.map(([id,label]) => '<button type="button" data-look="languageChoice" data-value="' + id
      + '" aria-pressed="' + (id === state.languageChoice) + '"><span>' + text(t(label)) + '</span>' + icon('check') + '</button>').join('');
  } else {
    const index = Number(trigger.dataset.choiceToggle), module = byId.get(state.module);
    const row = module.settings[index], selected = state.values.get(module.id + ':' + index) ?? row.value;
    count = row.choices.length;
    options.innerHTML = row.choices.map((choice,i) => '<button type="button" data-choice-option="' + i + '" aria-pressed="'
      + (i === Number(selected)) + '"><span>' + text(choice) + '</span>' + icon('check') + '</button>').join('');
  }
  const anchor = trigger.getBoundingClientRect(), bounds = $('content').getBoundingClientRect();
  const scale = state.windowScale / 100, gap = 4 * scale;
  const below = bounds.bottom - anchor.bottom - gap * 2;
  const above = anchor.top - bounds.top - gap * 2;
  const upward = below < Math.min(184,8 + count * 31) * scale && above > below;
  options.style.setProperty('zoom',String(scale));
  options.style.left = anchor.left / scale + 'px';
  options.style.width = anchor.width / scale + 'px';
  options.style.maxHeight = Math.max(0,Math.min(184,(upward ? above : below) / scale)) + 'px';
  options.hidden = false;
  const height = options.getBoundingClientRect().height;
  options.style.top = (upward ? anchor.top - gap - height : anchor.bottom + gap) / scale + 'px';
  openChoice = trigger;
}
function updateChoice(trigger,value) {
  const row = byId.get(state.module).settings[Number(trigger.dataset.choiceToggle)];
  trigger.querySelector('span').textContent = row.choices[value] ?? '';
  if (trigger === openChoice) for (const option of $('choice-popup').children)
    option.setAttribute('aria-pressed',String(Number(option.dataset.choiceOption) === Number(value)));
}
const crosshairGridSize = 15;
function crosshairPath(pixels) {
  let path = '';
  for (let i = 0; i < pixels.length; i++) {
    if (pixels[i] === '1') path += 'M' + (i % crosshairGridSize) + ' ' + Math.floor(i / crosshairGridSize) + 'h1v1h-1z';
  }
  return path;
}
function crosshairIcon(pixels) {
  return '<svg viewBox="0 0 15 15" aria-hidden="true" shape-rendering="crispEdges"><path fill="currentColor" d="'
    + crosshairPath(pixels) + '"/></svg>';
}
function crosshairEditor(row, module, index) {
  const [,mode,color,size,rotation] = module.settings;
  const cells = [...row.value].map(pixel => '<span' + (pixel === '1' ? ' class="painted"' : '') + '></span>').join('');
  const resetIcon = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M13 7a5 5 0 1 0-1 4M13 3v4H9"/></svg>';
  const saveIcon = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2v8m-3-3 3 3 3-3M3 13h10"/></svg>';
  return '<section id="field-' + index + '" class="crosshair-editor" data-crosshair="' + index + '" data-pixels="' + row.value
    + '" data-mode="' + mode.value + '" style="--crosshair-ink:' + text(color.value) + ';--crosshair-size:' + size.value / 100
    + ';--crosshair-turn:' + rotation.value + 'deg">'
    + '<div class="crosshair-mode"><span>' + text(mode.label) + '</span><div id="field-1" role="group" aria-label="' + text(mode.label) + '">'
    + mode.choices.map((label,i) => '<button type="button" data-crosshair-operation="mode" data-crosshair-index="' + i
      + '" aria-pressed="' + (i === mode.value) + '">' + text(label) + '</button>').join('')
    + '</div></div><div class="crosshair-workbench" data-crosshair-custom><div class="crosshair-pane">'
    + '<div class="crosshair-heading"><h3>' + text(row.label) + '</h3><div class="crosshair-tools">'
    + '<button type="button" data-crosshair-operation="clear" title="' + text(t('Очистить')) + '">' + resetIcon + '</button>'
    + '<button type="button" data-crosshair-operation="save-mode" title="' + text(t('Сохранить')) + '" aria-pressed="false">' + saveIcon + '</button>'
    + '</div></div><div class="crosshair-grid" tabindex="0" aria-label="' + text(row.label) + '">' + cells + '</div></div>'
    + '<div class="crosshair-custom-side"><div><h3>' + text(t('Управление')) + '</h3>'
    + '<p><kbd>LMB</kbd> ' + text(t('Нарисовать пиксель')) + '</p><p><kbd>RMB</kbd> ' + text(t('Удалить пиксель'))
    + '</p><p><kbd>Ctrl + Z</kbd> ' + text(t('Отменить штрих')) + '</p><p><kbd>Shift + LMB</kbd> '
    + text(t('Соединить две точки')) + '</p></div>'
    + '<div class="crosshair-shelf"><div class="crosshair-heading"><h3>' + text(t('Сохранённые прицелы'))
    + ' <span>' + row.slots.filter(slot => slot.pixels).length + ' / ' + row.slots.length + '</span></h3>'
    + '<small class="crosshair-save-hint">' + text(t('Выбери слот для сохранения')) + '</small></div><div class="crosshair-options">'
    + row.slots.map(({pixels,color},i) => '<button type="button" class="crosshair-option" data-crosshair-operation="slot" data-crosshair-index="'
      + i + '" data-filled="' + Boolean(pixels) + '" title="' + text(t(pixels ? 'Загрузить' : 'Сохранить')) + ' ' + (i + 1)
      + '" style="color:' + text(color ?? '') + '">' + (pixels ? crosshairIcon(pixels) : '<span class="crosshair-empty">+</span>') + '</button>').join('')
    + '</div></div></div></div><div class="crosshair-shelf" data-crosshair-presets><h3>' + text(t('Готовые формы')) + '</h3><div class="crosshair-options">'
    + row.presets.map(({label,pixels},i) => '<button type="button" class="crosshair-option" data-crosshair-operation="preset" data-crosshair-index="'
      + i + '" title="' + text(label) + '" aria-pressed="' + (i === row.selected) + '">' + crosshairIcon(pixels) + '</button>').join('')
    + '</div></div><div class="crosshair-classic">' + text(t('Используется стандартный прицел Minecraft')) + '</div>'
    + '<div class="crosshair-details"><div><h3>' + text(t('Цвет прицела')) + '</h3>'
    + controlRow({...color,label:t('Основной цвет')},module,2,true) + '</div>'
    + '<div><h3>' + text(t('Размер и поворот')) + '</h3>'
    + controlRow(size,module,3,true) + controlRow(rotation,module,4,true) + '</div>'
    + '<div class="crosshair-preview"><span>' + text(t('Предпросмотр')) + '</span><div>' + crosshairIcon(mode.value === 1 ? row.presets[row.selected].pixels : row.value)
    + '</div></div></div></section>';
}
function controlRow(row, module, index, embedded = false) {
  if (row.kind === 'crosshair') return crosshairEditor(row,module,index);
  if (!embedded && module.id === 'CROSSHAIR' && module.settings[0]?.kind === 'crosshair') return '';
  const id = 'field-' + index, saved = state.values.get(module.id + ':' + index) ?? row.value;
  if (row.kind === 'toggle') return settingToggle(id,row.label,saved,'data-setting-toggle="' + index + '"');
  let control;
  const attrs = ' id="' + id + '" data-setting="' + index + '"';
  switch (row.kind) {
    case 'slider': control = '<span class="range">' + rangeControl(attrs,row.min,row.max,row.step || '.01',saved,row.suffix)
      + '<output for="' + id + '">' + saved + text(row.suffix ?? '') + '</output></span>'; break;
    case 'choice': control = choiceControl(row,id,index,saved); break;
    case 'color': return colorRow(row,id,attrs,saved);
    case 'text': control = '<input type="text"' + attrs + (row.maxLength ? ' maxlength="' + row.maxLength + '"' : '')
      + ' value="' + text(saved) + '" autocomplete="off">'; break;
    case 'keybind': control = '<button type="button" class="button" id="' + id + '" data-keybind="' + index + '">' + text(saved === '—' ? t('Назначить') : saved) + '</button>'; break;
    case 'button': control = '<button class="button" type="button" data-setting-button="' + index + '">' + text(row.caption) + '</button>'; break;
    default: control = '<small id="' + id + '">' + text(saved) + '</small>';
  }
  return '<div class="setting-row' + (row.kind === 'choice' ? ' choice-row' : '')
    + '"><label for="' + id + '">' + text(row.label) + '</label>' + control + '</div>';
}
function settingToggle(id,label,checked,attrs) {
  return '<button type="button" id="' + id + '" class="setting-row setting-toggle-row" role="switch" aria-checked="'
    + Boolean(checked) + '" ' + attrs + '><span>' + text(label) + '</span>' + toggleMarkup + '</button>';
}
function colorRow(row,id,attrs,value) {
  return '<div class="setting-row setting-color-row" style="--picker-color:' + text(value) + '">'
    + '<span>' + text(row.label) + '</span><input type="text" hidden' + attrs + ' value="' + text(value) + '">'
    + '<button type="button" class="color-swatch" data-color-toggle data-color-palette="' + text((row.palette ?? []).join(','))
    + '" aria-controls="color-popup" aria-expanded="false" aria-label="' + text(t('Палитра') + ' — ' + row.label)
    + '"><span class="color-code">' + text(value) + '</span><i></i></button></div>';
}
function colorPopup(palette,value) {
  return '<h3 id="color-popup-title">' + text(t('Палитра')) + '</h3><div class="color-presets">'
    + palette.map(color => '<button type="button" data-color-value="' + text(color)
      + '" aria-label="' + text(color) + '" title="' + text(color) + '" style="--color:' + text(color) + '"></button>').join('')
    + '</div><div class="color-saturation" data-color-axis="sv"><i></i></div>'
    + '<div class="color-hue" data-color-axis="hue"><i></i></div>'
    + '<label class="color-preview"><i></i><input id="color-popup-hex" aria-label="HEX" type="text" maxlength="7" spellcheck="false"'
    + ' value="' + text(value) + '"></label>';
}
function glintPreview(module) {
  if (module.settings.length < 6) return '';
  return '<div class="glint-preview"><div class="glint-preview-title"><strong>' + text(t('Предпросмотр'))
    + '</strong><span class="glint-preview-colors"><i></i><i></i></span></div>'
    + '<div id="glint-swatch"><div class="glint-light"><i></i></div><div class="glint-light"><i></i></div></div></div>';
}
function worldPreview(module) {
  if (module.settings.length < 14) return '';
  return '<div class="world-preview"><div class="world-preview-label"><strong>' + text(t('Предпросмотр атмосферы'))
    + '</strong><small>' + text(t('Небо, горизонт и туман')) + '</small></div>'
    + '<div id="world-preview-art"><svg viewBox="0 0 640 126" preserveAspectRatio="none" aria-hidden="true">'
    + '<path class="world-preview-clouds" fill="currentColor" d="M64 25h98v5h28v5H83v-5H64zm318 13h99v-5h78v5h32v6H382z"/>'
    + '<path class="world-preview-far" fill="currentColor" d="M0 90 56 78 91 82 160 61 203 74 241 75 279 66 351 83 415 68 470 81 516 64 581 81 640 71V126H0Z"/>'
    + '<path class="world-preview-near" fill="currentColor" d="M0 105 70 93 126 102 195 91 259 102 328 93 390 103 466 86 533 99 589 89 640 95V126H0Z"/>'
    + '</svg></div></div>';
}
function renderModule() {
  const module = byId.get(state.module);
  const enabled = state.enabled.has(module.id);
  const groups = (moduleGroups[module.id] || [[0,t('Настройки модуля')]]).map(([start,title],i,all) => ({
    start, title, rows:module.settings.slice(start,all[i+1]?.[0] ?? module.settings.length)
  })).filter(group => group.rows.length);
  const contents = groups.length > 1 ? '<nav class="settings-toc" aria-label="' + text(t('На этой странице')) + '"><span>' + text(t('На этой странице')) + '</span>'
    + groups.map(({title,rows},i) => '<button type="button" data-settings-group="' + i + '"'
      + (i === 0 ? ' aria-current="location"' : '') + '><span>' + text(t(title)) + '</span><small>' + rows.length + '</small></button>').join('')
    + '</nav>' : '';
  return '<div class="page-head module-settings-head"><div class="module-head"><span class="module-head-art">' + itemArt(module)
    + '</span><div><div class="module-category">' + text(t(names[module.category])) + '</div><h1>' + text(module.name)
    + '</h1><p>' + text(module.description) + '</p></div></div><button type="button" class="button module-enable" role="switch" aria-label="' + text(t('Модуль')) + ' '
    + text(module.name) + '" aria-checked="' + enabled + '" data-module-toggle="' + module.id + '">'
    + '<span class="module-state">' + (enabled ? t('Включён') : t('Выключен')) + '</span>' + toggleMarkup + '</button></div>'
    + '<div class="module-settings' + (contents ? ' with-toc' : '') + '">' + contents + '<div class="settings-groups">'
    + groups.map(({start,title,rows},i) => '<section class="settings-group" id="settings-group-' + i + '">'
      + (module.id === 'CROSSHAIR' ? '' : '<div class="settings-group-title"><h2>' + text(t(title)) + '</h2><span>' + rows.length + '</span></div>')
      + '<div class="sheet">'
      + (module.id === 'ENCHANT_GLINT' && start === 0 ? glintPreview(module) : '')
      + (module.id === 'WORLD_CHANGER' && start === 0 ? worldPreview(module) : '')
      + rows.map((row,j) => controlRow(row,module,start+j)).join('') + '</div></section>').join('')
    + '</div></div>';
}
function renderProfile() {
  const account = currentAccount();
  if (!account.key) return pageHead(t('Профиль')) + actionButton('login',t('Войти в GleamClient'),'user','primary');
  const status = account.badge === 'Owner' || account.badge === 'Admin'
    ? accountStatus(account) : text(t('Подписка')) + ' ' + text(account.plan);
  return pageHead(t('Профиль')) + '<div class="sheet"><div class="profile-top">' + avatar(account,'hero') + '<div><h2>'
    + text(account.name) + '</h2><p>' + status + '</p></div></div>'
    + (account.plusRemaining ? '<div class="setting-row"><span>' + text(t('Осталось Plus')) + '</span>'
      + '<strong class="profile-plus"' + (account.plusRemaining === '∞' ? ' aria-label="' + text(t('Навсегда')) + '"' : '')
      + '>' + text(account.plusRemaining) + '</strong></div>' : '')
    + '<div class="setting-row"><span>' + text(t('ID аккаунта')) + '</span>' + actionButton('copy',account.uid,'copy') + '</div>'
    + '<button type="button" class="setting-row publication-row" data-action="publication" role="checkbox" aria-checked="'
    + state.publication + '"' + (state.publicationReady ? '' : ' disabled') + '><span>' + text(t('Профиль виден в каталоге'))
    + '</span><span class="publication-check" aria-hidden="true">' + icon('check') + '</span></button>'
    + '<div class="setting-row"><span>' + text(t('Опубликованные конфиги')) + '</span>'
    + actionButton('my-configs',t('Мои публикации'),'layers') + '</div></div>';
}
function renderConfigs() {
  const tabs = [['catalog',t('Каталог')],['local',t('Локальные')],['published',t('Мои публикации')]];
  const tab = state.configTab, list = configs[tab], chosen = currentConfig();
  const modules = chosen?.modules.filter(id => byId.has(id)).map(id => text(byId.get(id).name)).join(' · ');
  const status = configLoadStatus[tab], page = configPages[tab] ?? {};
  const disabled = page.busy ? ' disabled' : '';
  const button = (action,label,svg='',variant='') => actionButton(action,t(label),svg,variant,page.busy);
  const pagination = page.offset > 0 || page.hasNext ? '<div class="config-pagination">'
    + actionButton('config-prev',t('Назад'),'back','',page.busy || status === 'loading' || !page.offset)
    + actionButton('config-next',t('Дальше'),'arrow','',page.busy || status === 'loading' || !page.hasNext) + '</div>' : '';
  const canEdit = chosen && (tab === 'published' || tab === 'local' && chosen.key !== 0);
  return pageHead(t('Конфиги'),t('Сохраняй и применяй наборы модулей'),'<div class="native-config-actions">'
      + button('new-config','Создать','plus') + button('publish-config','Опубликовать')
      + button('refresh-configs','Обновить') + '</div>')
    + '<div class="tabs" role="tablist">' + tabs.map(([id,label]) => '<button type="button" role="tab" aria-selected="'
      + (tab === id) + '" data-config-tab="' + id + '"' + disabled + '>' + text(label) + '</button>').join('') + '</div>'
    + (list.length ? '<div class="split"><div class="list-panel">' + list.map(item => '<button type="button" class="list-row" data-config="'
      + item.key + '" aria-pressed="' + (item === chosen) + '"' + disabled + '>' + icon('layers') + '<span><span class="row-name">' + text(item.name)
      + '</span><span class="row-note">' + text(item.note) + '</span></span></button>').join('')
      + pagination + '</div><article class="preset-info"><h2>' + text(chosen.name) + '</h2><p>'
      + text([chosen.note,chosen.author].filter(Boolean).join(' · ')) + '</p>'
      + (modules ? '<p>' + modules + '</p>' : '') + '<div class="config-entry-actions">'
      + (tab === 'local' ? button('apply-config','Применить','check','primary')
        : button('save-config',chosen.downloaded ? 'Обновить копию' : 'Скачать','folder','primary'))
      + (canEdit ? button('rename-config','Переименовать') + button('overwrite-config','Обновить текущими') : '')
      + (tab === 'local' ? button('publish-config-copy','Опубликовать копию') : '')
      + (canEdit ? button('remove-config','Удалить') : '')
      + '</div></article></div>' : !status || status === 'loading' ? empty(t('Загрузка…'),'')
      : status !== 'ready' ? empty(status,'')
      : empty(t('Здесь пока пусто'),t('Создай конфиг из текущего набора модулей')));
}
function friendAvatar(item,large=false) {
  const source = friends.avatars?.[item.public_id];
  const size = large ? ' hero' : '';
  return source ? '<img class="avatar friend-avatar' + size + '" src="assets/' + text(source) + '.png" alt="">'
    : '<span class="friend-initial' + size + '" aria-hidden="true">' + text(item.username[0].toUpperCase()) + '</span>';
}
function renderFriendProfile() {
  const item = friends.items.find(entry => entry.username === state.friendProfile);
  const profile = friends.profile?.username === state.friendProfile ? friends.profile : null;
  const back = actionButton('friend-profile-back',t('Назад к друзьям'),'back');
  const head = pageHead(t('Профиль'),'',back);
  if (!item) return head + empty(t('Профиль недоступен'),'');
  const badge = (profile?.role ?? item.role) === 'owner' ? 'Owner' : (profile?.role ?? item.role) === 'admin' ? 'Admin' : '';
  return head + '<div class="sheet"><div class="profile-top">' + friendAvatar(item,true) + '<div><h2>'
    + text(item.username) + '</h2>' + (badge ? '<p>' + accountStatus({badge}) + '</p>' : '') + '</div></div>'
    + (profile?.id ? '<div class="setting-row"><span>' + text(t('ID аккаунта')) + '</span>'
      + actionButton('friend-profile-copy',profile.id,'copy') + '</div><div class="setting-row"><span>'
      + text(t('Дата регистрации')) + '</span><small>' + text(profile.created) + '</small></div>'
      : '<div class="setting-row"><span>' + text(profile?.status ?? t('Загрузка…')) + '</span></div>') + '</div>';
}
function renderFriends() {
  if (state.friendProfile && friends.signedIn) return renderFriendProfile();
  const head = pageHead(t('Друзья'),'',friends.signedIn
    ? '<div class="friends-tools">' + actionButton('friends-refresh',t('Обновить'),'refresh','',friends.busy)
      + actionButton('friends-add',t('Добавить'),'plus','primary',friends.busy) + '</div>' : '');
  if (!friends.signedIn) return head + '<div class="empty">' + icon('users') + '<b>' + text(t('Войди в аккаунт GleamClient'))
    + '</b>' + actionButton('friends-login',t('Войти'),'user','primary') + '</div>';
  const tabs = [
    ['friend','Друзья',[['remove','Удалить из друзей']]],
    ['incoming','Входящие',[['accept','Принять'],['decline','Отклонить']]],
    ['outgoing','Исходящие',[['cancel','Отозвать']]]
  ].map(([id,title,commands]) => ({id,title,commands,rows:friends.items.filter(item => item.status === id)}));
  const active = tabs.find(tab => tab.id === state.friendTab) ?? tabs.find(tab => tab.rows.length) ?? tabs[0];
  const summary = friends.busy ? 'Загрузка…' : active.id === 'friend' ? 'Друзей пока нет' : 'Заявок пока нет';
  return head + '<div class="tabs friends-tabs" role="tablist" aria-label="' + text(t('Друзья')) + '">'
    + tabs.map(({id,title,rows}) => '<button type="button" role="tab" aria-selected="' + (id === active.id)
      + '" data-friend-tab="' + id + '"><span>' + text(t(title)) + '</span><span class="friend-count">'
      + rows.length + '</span></button>').join('') + '</div>'
    + (friends.status ? '<p class="friends-status" role="status">' + text(friends.status) + '</p>' : '')
    + '<div class="friends-list" role="tabpanel" aria-label="' + text(t(active.title)) + '">'
    + (active.rows.length ? active.rows.map(item => '<div class="friends-row"><button type="button" class="friend-identity" data-friend-profile="'
      + text(item.username) + '">' + friendAvatar(item) + '<span class="friend-name">' + text(item.username)
      + '</span></button><div class="friends-tools"><button type="button" class="button" data-friend-profile="' + text(item.username)
      + '">' + text(t('Профиль')) + '</button>'
      + active.commands.map(([change,label]) => '<button type="button" class="button' + (change === 'accept' ? ' primary' : '')
        + '" data-friend-change="' + change + '" data-username="' + text(item.username) + '"'
        + (friends.busy ? ' disabled' : '') + '>' + text(t(label)) + '</button>').join('') + '</div></div>').join('')
      : '<div class="friends-empty">' + icon(active.id === 'friend' ? 'users' : 'user') + '<span>' + text(t(summary)) + '</span></div>') + '</div>';
}
const chatContacts = () => friends.items.filter(item => item.status === 'friend');
function chatTime(sentAt) {
  const date = new Date(sentAt * 1000);
  return String(date.getHours()).padStart(2,'0') + ':' + String(date.getMinutes()).padStart(2,'0');
}
function chatReplyQuote(own, body) {
  return '<b>' + text(own ? currentAccount().name : chat.peer) + '</b><small>'
    + text(body || t('Ответ на сообщение')) + '</small>';
}
function renderReplyDraft() {
  const reply = chatReplies.get(chat.peer);
  return reply ? '<div class="chat-quote">' + chatReplyQuote(reply.own, reply.body) + '</div>'
    + '<button type="button" class="icon-button" data-action="chat-cancel-reply" aria-label="'
    + text(t('Отменить ответ')) + '">' + icon('close') + '</button>' : '';
}
function syncReplyDraft() {
  const draft = $('chat-reply-draft');
  if (!draft) return;
  draft.innerHTML = renderReplyDraft();
  draft.hidden = !chatReplies.has(chat.peer);
}
function closeChatContext() {
  chatContextId = '';
  $('chat-context-menu').hidden = true;
}
function openChatContext(x,y) {
  closeChatContext();
  if (state.page !== 'chat' || $('dialog').hasAttribute('open')) return;
  const messages = $('chat-messages');
  const contains = ({left,top,right,bottom}) => x >= left && x < right && y >= top && y < bottom;
  if (!messages || !contains(messages.getBoundingClientRect())) return;
  const bubble = Array.from(messages.querySelectorAll('[data-chat-message]'))
    .find(node => contains(node.getBoundingClientRect()));
  const item = chat.items.find(item => String(item.id) === bubble?.dataset.chatMessage);
  if (!item || item.unreadable) return;
  chatContextId = String(item.id);
  const popup = $('chat-context-menu');
  popup.innerHTML = actionButton('chat-copy-message',t('Копировать текст'),'copy')
    + actionButton('chat-reply-message',t('Ответить'),'back');
  popup.hidden = false;
  const box = popup.getBoundingClientRect();
  const viewport = $('stage').getBoundingClientRect();
  popup.style.left = Math.max(4,Math.min(x,viewport.right - box.width - 4)) + 'px';
  popup.style.top = Math.max(4,Math.min(y,viewport.bottom - box.height - 4)) + 'px';
}
function renderChat() {
  if (!friends.signedIn) return pageHead(t('Чат')) + '<div class="empty">' + icon('chat') + '<b>' + text(t('Войди в аккаунт GleamClient'))
    + '</b>' + actionButton('friends-login',t('Войти'),'user','primary') + '</div>';
  return '<div class="chat-layout"><aside class="chat-sidebar"><h1>' + text(t('Чат')) + '</h1>'
    + '<label class="chat-search">' + icon('search') + '<input id="chat-search" autocomplete="off" placeholder="'
    + text(t('Поиск по чатам')) + '" aria-label="' + text(t('Поиск по чатам')) + '" value="' + text(state.chatQuery) + '"></label>'
    + '<div id="chat-contacts" class="chat-contacts">' + renderChatContacts(chatContacts(),state.chatQuery.toLowerCase())
    + '</div></aside><section id="chat-thread" class="chat-thread">' + renderChatThread() + '</section></div>';
}
function renderChatMessages() {
  const retry = () => actionButton('chat-retry',t('Обновить'),'');
  if (!chat.peer) return '<div class="chat-empty"><b>'
    + text(friends.busy ? t('Загрузка…') : friends.status || t('Чатов пока нет')) + '</b>'
    + (friends.busy ? '' : friends.status ? retry() : '<small>' + text(t('Чат появится после принятия заявки в друзья')) + '</small>'
      + actionButton('chat-friends',t('Перейти к друзьям'),'users')) + '</div>';
  if (!chat.items.length) return '<div class="chat-empty"><b>'
    + text(chat.busy ? t('Загрузка…') : chat.status || t(chat.loaded ? 'Сообщений пока нет' : 'Загрузка…')) + '</b>'
    + (chat.busy ? '' : chat.status ? retry() : chat.loaded ? actionButton('chat-wave',t('Поздороваться'),'chat') : '') + '</div>';
  const person = chatContacts().find(item => item.username === chat.peer);
  const rows = chat.items.map((item,i) => {
    const date = new Date(item.sent_at * 1000);
    const sameGroup = other => other && other.own === item.own
      && Math.abs(other.sent_at - item.sent_at) < 120
      && new Date(other.sent_at * 1000).toDateString() === date.toDateString();
    const previous = chat.items[i-1], next = chat.items[i+1];
    const newDay = !previous || new Date(previous.sent_at * 1000).toDateString() !== date.toDateString();
    const day = String(date.getDate()).padStart(2,'0') + '.' + String(date.getMonth()+1).padStart(2,'0');
    const body = item.unreadable ? t('Не удалось расшифровать сообщение') : item.body;
    const portrait = person && !item.own ? '<div class="chat-message-avatar">'
      + (sameGroup(next) ? '' : friendAvatar(person)) + '</div>' : '';
    return (newDay ? '<div class="chat-date"><span>' + day + '</span></div>' : '')
      + '<div class="chat-line' + (item.own ? ' own' : '') + (sameGroup(previous) ? ' continuation' : '')
      + '">' + portrait + '<div class="chat-message" data-chat-message="' + item.id + '">'
      + (item.reply_to ? '<button type="button" class="chat-quote" data-reply-jump="' + item.reply_to + '">'
        + chatReplyQuote(item.reply_own,item.reply_preview) + '</button>' : '')
      + '<div class="chat-bubble"><span>' + text(body)
      + (item.legacy ? '<small class="chat-legacy">' + text(t('Без сквозного шифрования')) + '</small>' : '') + '</span>'
      + (sameGroup(next) ? '' : '<time>' + chatTime(item.sent_at) + '</time>') + '</div></div></div>';
  }).join('');
  return (chat.older ? actionButton('chat-older',t('Ранние сообщения'),'','',chat.busy) : '') + '<div class="chat-log">' + rows + '</div>';
}
function renderChatThread() {
  const person = chatContacts().find(item => item.username === chat.peer);
  const title = person ? '<div class="chat-heading"><div id="chat-heading-avatar">' + friendAvatar(person)
    + '</div><strong>' + text(person.username) + '</strong>'
    + '<button id="chat-verify" type="button" class="button" data-action="chat-verify"'
    + (chat.verification ? '' : ' hidden') + '>' + icon('lock') + text(t('Код безопасности')) + '</button></div>' : '';
  const controls = person ? '<form id="chat-compose" class="chat-compose"><input name="body" autocomplete="off" maxlength="500"'
    + ' aria-label="' + text(t('Сообщение')) + '" placeholder="' + text(t('Напиши сообщение')) + '" value="'
    + text(chatDrafts.get(chat.peer) ?? '') + '"><button class="button primary chat-send" type="submit" aria-label="'
    + text(t('Отправить')) + '" title="' + text(t('Отправить')) + '"' + (chat.busy ? ' disabled' : '')
    + '>' + icon('send') + '</button></form>' : '';
  return title + '<div id="chat-messages" class="chat-messages">' + renderChatMessages() + '</div>'
    + (person ? '<button id="chat-bottom" class="button" type="button" data-action="chat-bottom" aria-label="'
      + text(t('К новым сообщениям')) + '" hidden>' + icon('chevron-down') + '</button>' : '')
    + '<p id="chat-status" class="chat-status" role="status"'
    + (!chat.items.length || !chat.status || chat.status === t('Сообщение отправлено') ? ' hidden' : '') + '>' + text(chat.status ?? '') + '</p>'
    + (person ? '<div id="chat-reply-draft" class="chat-reply-draft"' + (chatReplies.has(chat.peer) ? '' : ' hidden') + '>'
      + renderReplyDraft() + '</div>' : '') + controls;
}
function renderChatContacts(contacts, query) {
  return contacts.filter(item => item.username.toLowerCase().includes(query)).sort((a,b) =>
    (chatRecent.get(b.username)?.sent_at ?? 0) - (chatRecent.get(a.username)?.sent_at ?? 0)).map(item => {
    const latest = chatRecent.get(item.username);
    const unread = chatUnread[item.username];
    const draft = item.username === chat.peer ? '' : chatDrafts.get(item.username);
    return '<button type="button" class="chat-contact" data-chat-peer="' + text(item.username) + '" aria-pressed="'
      + (chat.peer === item.username) + '">' + friendAvatar(item) + '<span class="chat-contact-body"><span class="chat-contact-line"><b>'
      + text(item.username) + '</b>' + (unread ? '<small class="chat-unread">' + Math.min(99,unread) + '</small>'
        : latest ? '<time>' + chatTime(latest.sent_at) + '</time>' : '')
      + '</span>' + (draft || latest ? '<small>' + (draft ? '<em>' + text(t('Черновик')) + '</em> ' : '')
        + text(draft || latest.body) + '</small>' : '') + '</span></button>';
  }).join('')
    || '<p class="chat-hint">' + text(t(query ? 'Ничего не найдено' : 'Чатов пока нет')) + '</p>';
}
function syncChatBottom() {
  const box = $('chat-messages'), button = $('chat-bottom');
  if (box && button) button.hidden = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
}
function mountChatThread() {
  const box = $('chat-messages');
  if (!box) return;
  box.scrollTop = box.scrollHeight;
  box.addEventListener('scroll',syncChatBottom);
  box.addEventListener('scroll',closeChatContext);
  syncChatBottom();
}
function patchChat(previous, friendsChanged) {
  const thread = $('chat-thread');
  if (!thread) return;
  const contacts = $('chat-contacts');
  const list = renderChatContacts(chatContacts(),state.chatQuery.toLowerCase());
  if (list !== contacts.lastMarkup) {
    const scroll = contacts.scrollTop;
    contacts.innerHTML = list;
    contacts.lastMarkup = list;
    contacts.scrollTop = scroll;
  }
  const person = chatContacts().find(item => item.username === chat.peer);
  if (previous.peer !== chat.peer || Boolean($('chat-heading-avatar')) !== Boolean(person)) {
    closeChatContext();
    thread.innerHTML = renderChatThread();
    mountChatThread();
    return;
  }
  const portrait = $('chat-heading-avatar');
  if (friendsChanged && portrait && person) {
    const markup = friendAvatar(person);
    if (portrait.innerHTML !== markup) portrait.innerHTML = markup;
  }
  const box = $('chat-messages');
  if (JSON.stringify(previous.items) !== JSON.stringify(chat.items) || previous.busy !== chat.busy || friendsChanged
      || previous.loaded !== chat.loaded || previous.status !== chat.status) {
    const top = box.scrollTop, height = box.scrollHeight;
    const atEnd = height - top - box.clientHeight < 40;
    const older = previous.items[0] && chat.items[0] && chat.items[0].id < previous.items[0].id;
    box.innerHTML = renderChatMessages();
    box.scrollTop = older ? top + box.scrollHeight - height : atEnd || previous.sent !== chat.sent ? box.scrollHeight : top;
  }
  const input = thread.querySelector('input[name="body"]'), send = thread.querySelector('button[type="submit"]');
  if (send) send.disabled = chat.busy;
  if (input && previous.sent !== chat.sent) input.value = chatDrafts.get(chat.peer) ?? '';
  const status = $('chat-status');
  const verification = $('chat-verify');
  if (verification) verification.hidden = !chat.verification;
  status.textContent = chat.status ?? '';
  status.hidden = !chat.items.length || !chat.status || chat.status === t('Сообщение отправлено');
  syncReplyDraft();
  syncChatBottom();
}
const cosmetics = [
  ['angel','WINGS','Ангельские крылья','Спина',0],
  ['dragon','WINGS','Драконьи крылья','Спина',1],
  ['halo','HALO','Золотой нимб','Голова'],
  ['hat','CHINA_HEAD','Соломенная шляпа','Голова'],
  ['dance','EMOTES','Эмоции','Анимация']
];
const cosmeticById = id => cosmetics.find(item => item[0] === id);
const cosmeticSlots = [['all','Все','grid'],['Спина','Спина','layers'],['Голова','Голова','user'],['Анимация','Анимация','movement']];
const isCosmeticEquipped = ([,module,,,style]) => state.enabled.has(module) && (style === undefined || style === state.wingsStyle);
function cosmeticDrawing(id, model) {
  return '<img class="cosmetic-art" src="assets/cosmetic-' + id + (model ? '-preview' : '-item') + '.png" alt="">';
}
function renderCosmetics() {
  const selected = cosmeticById(state.cosmetic) ?? cosmetics[0];
  const [id,module,label,type] = selected, active = isCosmeticEquipped(selected);
  const search = '<label class="chat-search cosmetic-search">' + icon('search') + '<input id="cosmetic-search" autocomplete="off"'
    + ' placeholder="' + text(t('Поиск вещей')) + '" aria-label="' + text(t('Поиск вещей')) + '" value="' + text(state.cosmeticQuery) + '"></label>';
  return pageHead(t('Косметика'),t('Надето') + ' ' + cosmetics.filter(isCosmeticEquipped).length + ' / ' + cosmetics.length,search)
    + '<div class="cosmetics-layout"><nav class="cosmetic-slots" aria-label="' + text(t('Тип вещи')) + '">'
    + cosmeticSlots.map(([key,label,svg]) => '<button type="button" class="button" data-cosmetic-slot="' + key + '" aria-label="'
      + text(t(label)) + '" title="' + text(t(label)) + '" aria-pressed="' + (key === state.cosmeticSlot) + '">' + icon(svg) + '</button>').join('')
    + '</nav><section class="cosmetic-shelf"><header class="cosmetic-shelf-head"><strong>' + text(t(state.cosmeticSlot === 'all' ? 'Предметы' : state.cosmeticSlot))
    + '</strong><span id="cosmetic-count" class="cosmetic-type"></span><div class="cosmetic-filters">'
    + actionButton('cosmetic-show-all',t('Все'),'',state.cosmeticOnlyEquipped ? '' : 'selected')
    + actionButton('cosmetic-show-equipped',t('Надето'),'',state.cosmeticOnlyEquipped ? 'selected' : '') + '</div></header>'
    + '<div id="cosmetic-items" class="cosmetic-items"></div></section>'
    + '<section class="cosmetic-preview"><header class="cosmetic-preview-head">'
    + '<span>' + text(t('Примерка')) + '</span>' + actionButton('cosmetic-turn',t(Math.cos(state.cosmeticYaw * Math.PI / 180) < 0 ? 'Спереди' : 'Со спины'),'rotate') + '</header>'
    + '<div class="cosmetic-stage" data-preview="' + id + '">' + (typeof sendGame === 'function' ? '' : cosmeticDrawing(id,true)) + '</div>'
    + '<div class="cosmetic-info"><div class="cosmetic-info-title">' + cosmeticDrawing(id,false) + '<div><strong>'
    + text(t(label)) + '</strong><div class="cosmetic-type">' + text(t(type)) + '</div></div></div>'
    + '<div class="cosmetic-actions"><button type="button" class="button ' + (active ? '' : 'primary') + '" data-equip-cosmetic="'
    + id + '">' + text(t(id === 'dance' ? active ? 'Выключить' : 'Включить' : active ? 'Снять' : 'Надеть')) + '</button>'
    + (byId.get(module)?.configurable ? '<button type="button" class="button" data-module-settings="' + module + '">'
      + icon('gear') + text(t('Настройки')) + '</button>' : '') + '</div></div></section></div>';
}
function patchCosmeticShelf() {
  const query = state.cosmeticQuery.trim().toLocaleLowerCase(uiLanguage);
  const items = cosmetics.filter(item => (state.cosmeticSlot === 'all' || item[3] === state.cosmeticSlot)
    && (!state.cosmeticOnlyEquipped || isCosmeticEquipped(item))
    && (!query || t(item[2]).toLocaleLowerCase(uiLanguage).includes(query)));
  $('cosmetic-count').textContent = String(items.length);
  $('cosmetic-items').innerHTML = items.length ? items.map(item => {
    const [id,,label,type] = item;
    return '<button type="button" class="cosmetic-tile" data-cosmetic="' + id + '" aria-pressed="' + (id === state.cosmetic)
      + '">' + cosmeticDrawing(id,false) + '<strong>' + text(t(label)) + '</strong><small>' + text(t(type)) + '</small>'
      + (isCosmeticEquipped(item) ? '<span class="cosmetic-equipped" aria-label="' + text(t('Надето')) + '">' + icon('check') + '</span>' : '') + '</button>';
  }).join('') : '<p class="chat-hint">' + text(t('Ничего не найдено')) + '</p>';
}
let cosmeticFrame = 0, cosmeticLayout = '';
let cosmeticHover = '', cosmeticSuppressed = '', cosmeticSpin = null;
function cosmeticLoadout() {
  const peek = cosmeticById(cosmeticHover);
  const worn = cosmetics.filter(item => item[0] !== 'dance' && isCosmeticEquipped(item) && (!peek || peek[3] !== item[3]));
  if (peek) worn.push(peek);
  return worn.map(item => item[0]);
}
function scheduleCosmeticPreview() {
  if (typeof sendGame !== 'function' || cosmeticFrame) return;
  cosmeticFrame = requestAnimationFrame(() => {
    cosmeticFrame = 0;
    const stage = state.page === 'cosmetics' && !$('dialog').hasAttribute('open')
      && !$('window').hidden && $('content').querySelector('.cosmetic-stage');
    const data = {};
    if (stage) {
      const box = stage.getBoundingClientRect(), clip = $('content').getBoundingClientRect();
      data.items = cosmeticLoadout();
      data.yaw = state.cosmeticYaw;
      data.zoom = state.cosmeticZoom;
      data.x = box.left;
      data.y = box.top;
      data.w = box.width;
      data.h = box.height;
      data.clipTop = clip.top;
      data.clipBottom = clip.bottom;
    }
    const key = JSON.stringify(data);
    if (key === cosmeticLayout) return;
    cosmeticLayout = key;
    sendGame('cosmetic-preview',data);
  });
}
document.addEventListener('mouseover',event => {
  if (state.page !== 'cosmetics') return;
  const hovered = event.target.closest('[data-cosmetic]')?.dataset.cosmetic ?? '';
  if (hovered && cosmeticSuppressed === hovered) return;
  cosmeticSuppressed = '';
  if (cosmeticHover === hovered) return;
  cosmeticHover = hovered;
  scheduleCosmeticPreview();
});
document.addEventListener('mouseout',event => {
  if (!cosmeticHover || event.relatedTarget) return;
  cosmeticHover = '';
  scheduleCosmeticPreview();
});
document.addEventListener('pointerdown',event => {
  if (event.button !== 0 || !event.target.closest('.cosmetic-stage')) return;
  cosmeticSpin = event.clientX;
});
document.addEventListener('pointermove',event => {
  if (cosmeticSpin === null) return;
  state.cosmeticYaw = (state.cosmeticYaw + (event.clientX - cosmeticSpin) * 1.1) % 360;
  cosmeticSpin = event.clientX;
  scheduleCosmeticPreview();
});
document.addEventListener('pointerup',() => { cosmeticSpin = null; });
document.addEventListener('pointercancel',() => { cosmeticSpin = null; });
document.addEventListener('wheel',event => {
  if (!event.target.closest('.cosmetic-stage')) return;
  event.preventDefault();
  state.cosmeticZoom = Math.max(.65,Math.min(1.35,state.cosmeticZoom + (event.deltaY > 0 ? -.08 : .08)));
  scheduleCosmeticPreview();
},{passive:false});
function renderPresets() {
  if (!presets.length) return pageHead(t('Визуальные пресеты'));
  const [name,note,renderDistance,simulation,scale,details] = presets[state.preset];
  return pageHead(t('Визуальные пресеты'),t('Готовые наборы графики и эффектов')) + '<div class="split"><div class="list-panel">'
    + presets.map(([title,description],i) => '<button type="button" class="list-row" data-preset="' + i + '" aria-pressed="'
      + (state.preset === i) + '">' + icon('monitor') + '<span><span class="row-name">' + title + '</span><span class="row-note">' + description
      + '</span></span>' + (state.appliedPreset === i ? '<span class="right">' + icon('check') + '</span>' : '') + '</button>').join('')
    + '</div><article class="preset-info"><h2>' + name + '</h2><p>' + note + '</p><div class="readout">'
    + '<div>' + text(t('Прорисовка')) + '<b>' + renderDistance + ' ' + text(t('чанков')) + '</b></div><div>' + text(t('Симуляция')) + '<b>' + simulation
    + ' ' + text(t('чанков')) + '</b></div><div>' + text(t('Дальность сущностей')) + '<b>' + scale + '×</b></div></div><p>' + details + '</p>'
    + actionButton('apply-preset',t('Применить'),'check','primary') + (state.canRestorePreset ? ' ' + actionButton('restore-preset',t('Вернуть настройки')) : '') + '</article></div>';
}
function renderWaypoints() {
  const chosen = points.find(item => item.key === state.point);
  return pageHead(t('Метки мира'),t('Сохранённые координаты'),actionButton('new-point',t('Добавить'),'plus'))
    + (chosen ? '<div class="split"><div class="list-panel">' + points.map(item => '<button type="button" class="list-row" data-point="'
      + item.key + '" aria-pressed="' + (item === chosen) + '">' + icon('pin') + '<span><span class="row-name">' + text(item.name)
      + '</span><span class="row-note">' + item.xyz.join(' / ') + '</span></span></button>').join('')
      + '</div><article class="preset-info"><div class="map-preview"><span class="map-marker">' + icon('pin') + text(chosen.name)
      + '</span></div><div class="readout"><div>' + text(t('Координаты')) + '<b>' + chosen.xyz.join(' / ') + '</b></div></div>'
      + actionButton('remove-point',t('Удалить метку')) + '</article></div>' : empty(t('Нет сохранённых меток'),t('Добавь метку, чтобы посмотреть её координаты')));
}
function lookOption(key,value,label,art) {
  return '<button type="button" class="look-tile" data-look="' + key + '" data-value="' + value + '" aria-pressed="'
    + (state[key] === value) + '">' + art
    + '<span class="look-tile-label">' + text(t(label)) + '<span class="look-tick">' + icon('check') + '</span></span></button>';
}
function lookTile(key,value,label,theme,density) {
  const art = '<span class="look-art" data-mini-theme="' + theme + '" data-mini-density="' + density + '" aria-hidden="true">'
    + '<span class="look-mini-nav"><img src="assets/orion-badge.png" width="11" height="11" alt=""><i></i><i></i><i></i></span>'
    + '<span class="look-mini-page"><i></i><span>' + '<i></i>'.repeat(density === 'compact' ? 12 : 8) + '</span></span></span>';
  return lookOption(key,value,label,art);
}
function toggleTile(style,label) {
  const art = '<span class="look-toggle-art" aria-hidden="true">' + [true,false].map(enabled =>
    '<span class="look-toggle-card"><i></i><span class="module-toggle" data-toggle-style="' + style
    + '" data-state="' + (enabled ? 'on' : 'off') + '">' + toggleIndicator(style,enabled) + '</span></span>').join('') + '</span>';
  return lookOption('toggleStyle',style,label,art);
}
function sizeTile(size,label) {
  return lookOption('windowSize',size,label,'<span class="look-size-art" aria-hidden="true">'
    + '<span class="look-size-frame" data-size="' + size + '"><i></i><i></i></span></span>');
}
function accentPicker() {
  return '<div class="look-accent"><h3>' + text(t('Акцент')) + '</h3><p>' + text(t('Цвет включённых модулей, ползунков и выделения')) + '</p>'
    + '<div class="accent-options" role="group" aria-label="' + text(t('Цвет акцента')) + '">'
    + accentColors.map(([value,name,color=value]) => '<button type="button" class="accent-swatch" data-accent="' + value
      + '" aria-pressed="' + (state.accent === value) + '" aria-label="' + text(t(name)) + '" title="' + text(t(name)) + '" style="--swatch:'
      + color + '"><svg viewBox="0 0 32 32" aria-hidden="true"><circle class="accent-ring" cx="16" cy="16" r="14"/>'
      + '<circle class="accent-dot" cx="16" cy="16" r="10"/></svg></button>').join('')
    + '</div>' + colorRow({label:t('Свой цвет акцента'),palette:accentColors.map(([value]) => value).filter(Boolean)},
      'custom-accent',' id="custom-accent"',state.accent || '#3c58e8') + '</div>';
}
function renderSettings() {
  const language = languages.find(([id]) => id === state.languageChoice) || languages[0];
  return pageHead(t('Настройки клиента')) + '<div class="look-settings"><section><h2>' + text(t('Язык интерфейса')) + '</h2>'
    + '<div class="sheet look-block"><button type="button" class="button language-trigger" id="language-trigger"'
      + ' data-choice-toggle="language" aria-controls="choice-popup" aria-expanded="false"><span>'
      + text(t(language[1])) + '</span>' + icon('chevron-down') + '</button></div></section>'
    + '<section><h2>' + text(t('Уведомления чата')) + '</h2><div class="sheet">'
    + chatPreference('chat-notification-toggle','chatNotifications','Всплывающие уведомления')
    + chatPreference('chat-sound-toggle','chatNotificationSound','Звук сообщений')
    + chatPreference('fake-punishment-toggle','showFakePunishments','Показывать чужие розыгрыши',false)
    + '</div></section><section><h2>' + text(t('Оформление')) + '</h2>'
    + '<div class="sheet look-block"><h3>' + text(t('Тема ClickGUI')) + '</h3><p>' + text(t('Выбери оформление меню')) + '</p><div class="look-options" role="group" aria-label="' + text(t('Тема ClickGUI')) + '">'
    + lookTile('theme','dark',t('Тёмная'),'dark',state.density) + lookTile('theme','light',t('Светлая'),'light',state.density)
    + '</div>' + accentPicker() + '</div></section><section><h2>' + text(t('Окно')) + '</h2><div class="sheet look-block">'
    + '<h3>' + text(t('Размер окна')) + '</h3><p>' + text(t('Сколько места меню занимает на экране')) + '</p>'
    + '<div class="look-options window-options" role="group" aria-label="' + text(t('Размер окна')) + '">'
    + windowSizes.map(([id,label]) => sizeTile(id,label)).join('')
    + '</div><div class="window-scale"><div><label for="window-scale">' + text(t('Масштаб')) + '</label><p>' + text(t('Размер текста и элементов меню')) + '</p></div>'
    + '<div class="range">' + rangeControl(' id="window-scale" aria-valuetext="' + state.windowScale + '%"',70,160,1,state.windowScale)
    + '<output for="window-scale">' + state.windowScale
    + '%</output></div></div>' + actionButton('center-window',t('Вернуть окно в центр')) + '</div></section><section><h2>' + text(t('Размер карточек')) + '</h2>'
    + '<div class="sheet look-block"><h3>' + text(t('Плотность')) + '</h3><p>' + text(t('В компактном режиме в сетку помещается больше модулей')) + '</p>'
    + '<div class="look-options" role="group" aria-label="' + text(t('Плотность карточек')) + '">' + lookTile('density','normal',t('Обычная'),state.theme,'normal')
    + lookTile('density','compact',t('Компактная'),state.theme,'compact') + '</div></div></section>'
    + '<section><h2>' + text(t('Переключатели модулей')) + '</h2><div class="sheet look-block"><h3>' + text(t('Стиль на карточках')) + '</h3>'
    + '<p>' + text(t('Выбери, как показывать состояние модуля')) + '</p><div class="look-options" role="group" aria-label="' + text(t('Стиль переключателя')) + '">'
    + toggleTile('label','ON/OFF') + toggleTile('switch',t('Тумблер')) + '</div></div></section></div>';
}

function chatPreference(id,key,label,fallback=true) {
  return settingToggle(id,t(label),state[key] ?? fallback,'data-chat-preference="' + key + '"');
}
const renderers = {module:renderModule,profile:renderProfile,configs:renderConfigs,presets:renderPresets,
  waypoints:renderWaypoints,settings:renderSettings,friends:renderFriends,chat:renderChat,cosmetics:renderCosmetics};
let renderedPage;
function render(scrollTop=true, animate=true) {
  if (openChoice) showChoices(openChoice,false);
  renderNavigation();
  const isModule = state.page === 'module';
  const isFriendProfile = state.page === 'friends' && state.friendProfile && friends.signedIn;
  const crumb = isModule ? byId.get(state.module).name : isFriendProfile ? state.friendProfile : t(state.query.trim() ? 'Поиск' : names[state.page]);
  $('breadcrumb').innerHTML = (isModule ? '<button type="button" data-action="back">' + text(t('Модули')) + '</button>' : '<button type="button" data-page="all">GleamClient</button>')
    + '<span>' + icon('arrow') + '</span><b>' + text(crumb) + '</b>';
  $('breadcrumb').querySelector('svg').style.width = '11px';
  $('breadcrumb').querySelector('svg').style.height = '11px';
  $('content').toggleAttribute('data-grid',modulePage());
  $('content').toggleAttribute('data-chat',state.page === 'chat');
  $('content').innerHTML = modulePage() ? renderModules() : renderers[state.page]();
  if (state.page === 'chat') mountChatThread();
  if (state.page === 'cosmetics') patchCosmeticShelf();
  fitGrid();
  fitCategories();
  if (scrollTop) $('content').scrollTop = 0;
  const page = isModule ? state.module : state.page;
  if (animate && renderedPage !== page) animatePage($('content'));
  renderedPage = page;
  window.paradox?.refreshSettingsArtwork?.();
  scheduleCosmeticPreview();
}
function fitGrid() {
  const content = $('content');
  const availableWidth = content.getBoundingClientRect().width / (state.windowScale / 100);
  content.toggleAttribute('data-stacked',availableWidth <= 540);
  const grid = content.querySelector('.modules-grid');
  if (!grid) return;
  const columns = availableWidth < 360 ? 1 : availableWidth < 740 ? 2 : availableWidth < 875 ? 3 : 4;
  grid.style.gridTemplateColumns = 'repeat(' + columns + ',minmax(0,1fr))';
}
function fitWindow() {
  const stage = $('stage'), menu = $('window'), scale = state.windowScale / 100;
  const availableWidth = Math.max(1,(stage.clientWidth - 24) / scale);
  menu.style.setProperty('--window-scale',scale);
  menu.style.setProperty('--fit-width',availableWidth + 'px');
  menu.style.setProperty('--fit-height',Math.max(1,stage.clientHeight - 24) / scale + 'px');
  menu.toggleAttribute('data-compact-layout',availableWidth <= 916);
  menu.toggleAttribute('data-small-layout',availableWidth <= 636);
  positionWindow();
}
function windowTravel() {
  const stage = $('stage').getBoundingClientRect(), menu = $('window').getBoundingClientRect();
  return [Math.max(0,(stage.width - menu.width) / 2 - 5),Math.max(0,(stage.height - menu.height) / 2 - 5)];
}
function positionWindow() {
  const [width,height] = windowTravel(), scale = state.windowScale / 100;
  $('window').style.setProperty('left',width * state.windowX / (1000 * scale) + 'px');
  $('window').style.setProperty('top',height * state.windowY / (1000 * scale) + 'px');
  scheduleCosmeticPreview();
}
let windowDrag;
document.addEventListener('pointerdown',event => {
  if (event.button !== 0 || !event.target.closest('.workspace-header') || event.target.closest('button')) return;
  const [width,height] = windowTravel();
  windowDrag = {x:event.clientX,y:event.clientY,width,height,startX:state.windowX,startY:state.windowY};
  $('window').setAttribute('data-dragging','');
});
document.addEventListener('pointermove',event => {
  if (!windowDrag) return;
  const {x,y,width,height,startX,startY} = windowDrag;
  const limit = value => Math.round(Math.min(1000,Math.max(-1000,value)));
  if (width > 0) state.windowX = limit(startX + 1000 * (event.clientX - x) / width);
  if (height > 0) state.windowY = limit(startY + 1000 * (event.clientY - y) / height);
  positionWindow();
});
function finishWindowDrag() {
  if (!windowDrag) return;
  windowDrag = null;
  $('window').removeAttribute('data-dragging');
  if (typeof sendGame === 'function') sendGame('window-position',{x:state.windowX,y:state.windowY});
}
document.addEventListener('pointerup',finishWindowDrag);
document.addEventListener('pointercancel',finishWindowDrag);
let windowResizeTimer;
function animateWindowSize() {
  clearTimeout(windowResizeTimer);
  $('window').toggleAttribute('data-size-transition',!reducedMotion.matches);
  windowResizeTimer = setTimeout(() => {
    $('window').removeAttribute('data-size-transition');
    const choice = document.activeElement;
    if (choice?.matches('[data-look="windowSize"]')) choice.scrollIntoView({block:'nearest',inline:'nearest'});
  },reducedMotion.matches ? 0 : 260);
}
function selectPage(page) {
  closeChatContext();
  if (page !== 'cosmetics') cosmeticHover = '';
  state.friendProfile = null;
  if (page === 'friends' && state.page !== page) state.friendTab = null;
  state.page = page;
  state.query = '';
  $('search').value = '';
  accountOpen(false);
  render();
}
function colorChannels(hex) {
  return [1,3,5].map(index => parseInt(hex.slice(index,index+2),16));
}
function luminance(rgb) {
  return rgb.map(value => {
    const normalized = value / 255;
    return normalized <= .04045 ? normalized / 12.92 : ((normalized + .055) / 1.055) ** 2.4;
  }).reduce((sum,value,index) => sum + value * [.2126,.7152,.0722][index],0);
}
function applyAccent() {
  const root = document.documentElement, color = state.accent;
  const light = state.theme === 'light';
  const card = colorChannels(light ? '#fafbfe' : '#25262a');
  const rule = colorChannels(light ? '#c4ccda' : '#3b3c41');
  const mix = (from,to,weight) => 'rgb(' + from.map((value,index) =>
    Math.round(value * (1 - weight) + to[index] * weight)).join(',') + ')';
  let accent = colorChannels(light ? '#4257b2' : '#939fe9');
  const vars = ['--accent','--accent-ink','--accent-surface','--on-fill','--on-ink','--selected-accent'];
  if (color) {
    const base = colorChannels(color);
    const luma = luminance(base), surface = luminance(colorChannels(light ? '#f3f5f9' : '#1d1e22'));
    const ratio = (Math.max(luma,surface)+.05)/(Math.min(luma,surface)+.05);
    accent = ratio < 3 ? base.map(value => Math.round(value * .43 + (light ? 18 : 255) * .57)) : base;
    const ink = value => value > .179 ? '#101219' : '#fff';
    const values = ['rgb(' + accent.join(',') + ')',ink(luminance(accent)),
      mix(accent,card,.8),color,ink(luma),color];
    vars.forEach((key,index) => root.style.setProperty(key,values[index]));
  } else vars.forEach(key => root.style.removeProperty(key));
  // Цвета, которые браузер вычисляет через color-mix().
  const on = colorChannels(color || '#3c58e8');
  root.style.setProperty('--module-hover-border',mix(rule,accent,.34));
  root.style.setProperty('--list-hover-border',mix(rule,accent,.55));
  root.style.setProperty('--toggle-on-line',mix(on,[255,255,255],.12));
  root.style.setProperty('--toggle-on-hover',mix(on,[0,0,0],.09));
  for (const button of document.querySelectorAll('[data-accent]'))
    button.setAttribute('aria-pressed',String(button.dataset.accent === color));
  const picker = $('custom-accent');
  if (picker) {
    picker.value = color || '#3c58e8';
    const row = picker.closest('.setting-color-row');
    row.style.setProperty('--picker-color',picker.value);
    row.querySelector('.color-code').textContent = picker.value.toUpperCase();
  }
}
function applyLook() {
  document.documentElement.dataset.theme = state.theme;
  document.documentElement.dataset.density = state.density;
  document.documentElement.dataset.windowSize = state.windowSize;
  fitWindow();
  $('theme').setAttribute('aria-pressed',String(state.theme === 'light'));
  $('density').setAttribute('aria-pressed',String(state.density === 'compact'));
  $('theme').textContent = state.theme === 'light' ? t('Тёмная тема') : t('Светлая тема');
  $('density').textContent = state.density === 'compact' ? t('Обычный') : t('Компактный');
  if (state.page === 'settings') render(false);
  applyAccent();
  fitGrid();
  fitCategories();
}
let themeRequest;
function changeTheme(theme, trigger) {
  if ((themeRequest?.theme ?? state.theme) === theme) return;
  themeRequest?.transition?.skipTransition();
  const request = {theme}, root = document.documentElement, menu = $('window');
  themeRequest = request;
  const game = typeof sendGame === 'function';
  const box = menu.getBoundingClientRect(), origin = trigger.getBoundingClientRect();
  const animate = !reducedMotion.matches && !menu.hidden && box.width > 0 && box.height > 0;
  const x = Math.max(0,Math.min(box.width,origin.x + origin.width / 2 - box.x));
  const y = Math.max(0,Math.min(box.height,origin.y + origin.height / 2 - box.y));
  const radius = Math.hypot(Math.max(x,box.width-x),Math.max(y,box.height-y)) + 2;
  const apply = () => {
    if (themeRequest !== request) return;
    state.theme = theme;
    applyLook();
    if (game) {
      const change = {key:'theme',value:theme};
      if (animate) change.reveal = {x:box.x + x,y:box.y + y,radius};
      sendGame('look',change);
    }
    if (trigger.dataset.look === 'theme')
      $('content').querySelector('[data-look="theme"][data-value="' + theme + '"]')?.focus({preventScroll:true});
  };
  const finish = () => {
    if (themeRequest !== request) return;
    themeRequest = null;
    delete root.dataset.themeMotion;
  };
  if (game || !animate || !document.startViewTransition) {
    apply();
    finish();
    return;
  }
  root.dataset.themeMotion = '';
  request.transition = document.startViewTransition(apply);
  request.transition.ready.then(() => {
    if (themeRequest !== request) return;
    root.animate([
      {clipPath:'circle(0px at ' + x + 'px ' + y + 'px)'},
      {clipPath:'circle(' + radius + 'px at ' + x + 'px ' + y + 'px)'}
    ],{duration:410,easing:motionEase,pseudoElement:'::view-transition-new(orion-menu)'});
  }).catch(() => {});
  request.transition.finished.then(finish,finish);
}
const toastQueue = [];
let toastRunning = false;
function toast(message,kind='success') {
  if (!message) return;
  toastQueue.push([message,kind]);
  if (toastQueue.length > 4) toastQueue.shift();
  if (!toastRunning) nextToast();
}
function nextToast() {
  if (!toastQueue.length) { toastRunning = false; return; }
  toastRunning = true;
  const [message,kind] = toastQueue.shift(), element = $('toast');
  element.dataset.kind = kind;
  element.innerHTML = icon(kind === 'error' ? 'alert' : kind === 'chat' ? 'chat' : 'check') + '<span>' + text(message) + '</span>';
  element.classList.add('visible');
  setTimeout(() => {
    element.classList.remove('visible');
    setTimeout(nextToast,210);
  },2500);
}
function showDialog(title,html) {
  const dialog = $('dialog');
  dialog.innerHTML = '<div class="dialog-head"><h2 id="dialog-title">' + text(title) + '</h2><button type="button" class="icon-button" data-action="close-dialog" aria-label="' + text(t('Закрыть')) + '">'
    + icon('close') + '</button></div><div class="dialog-body">' + html + '</div>';
  $('dialog-overlay').hidden = false;
  if (typeof dialog.showModal === 'function') {
    if (!dialog.hasAttribute('open')) dialog.showModal();
  } else {
    dialog.setAttribute('open','');
    $('window').setAttribute('inert','');
    (dialog.querySelector('input') || dialog.querySelector('[type="submit"]'))?.focus({preventScroll:true});
  }
  scheduleCosmeticPreview();
}
function closeDialog() {
  const dialog = $('dialog');
  if (typeof dialog.close === 'function') dialog.close();
  else {
    dialog.removeAttribute('open');
    $('window').removeAttribute('inert');
    $('content').focus({preventScroll:true});
  }
  $('dialog-overlay').hidden = true;
  scheduleCosmeticPreview();
}
$('dialog').addEventListener('close', () => { $('dialog-overlay').hidden = true; scheduleCosmeticPreview(); });
function showAccounts() {
  const pop = $('account-pop');
  pop.innerHTML = '<button type="button" class="account-back" data-action="account-back">' + icon('back') + text(t('Аккаунты')) + '</button>'
    + '<div class="account-list">' + accounts.map(account => '<button type="button" class="account-option" data-account="'
    + account.key + '" aria-pressed="' + (account.key === state.account) + '">' + avatar(account)
    + '<span class="account-option-text"><span class="account-option-title"><b title="' + text(account.name) + '">'
    + text(account.name) + '</b>' + accountStatus(account,true) + '</span>'
    + (account.uid ? '<small>ID ' + text(account.uid) + '</small>' : '') + '</span>'
    + (account.key === state.account ? icon('check') : '') + '</button>').join('')
    + '</div><form class="account-add" id="add-account"><input name="name" required maxlength="32" placeholder="' + text(t('Ник аккаунта')) + '" aria-label="' + text(t('Ник')) + '">'
    + '<button type="button" class="icon-button" data-action="login" aria-label="' + text(t('Добавить аккаунт')) + '" title="' + text(t('Добавить аккаунт')) + '">' + icon('plus') + '</button></form>' + (state.account ? actionButton('logout',t('Выйти')) : '');
  fitCategories();
  for (const part of pop.children) appear(part,5,0,160);
  pop.querySelector('.account-back').focus();
}
function currentConfig() { return configs[state.configTab].find(item => item.key === state.config) || configs[state.configTab][0]; }
function configTab(tab) { state.configTab = tab; state.config = configs[tab][0]?.key ?? null; }
const actions = {
  'chat-copy-message'() {
    const item = chat.items.find(item => String(item.id) === chatContextId);
    closeChatContext();
    if (item && !item.unreadable && typeof sendGame === 'function') sendGame('clipboard-copy',{text:item.body});
  },
  'chat-reply-message'() {
    const item = chat.items.find(item => String(item.id) === chatContextId);
    closeChatContext();
    if (!item || item.unreadable) return;
    chatReplies.set(chat.peer,item);
    syncReplyDraft();
    $('chat-compose')?.querySelector('input').focus();
  },
  'chat-cancel-reply'() {
    chatReplies.delete(chat.peer);
    syncReplyDraft();
    $('chat-compose')?.querySelector('input').focus();
  },
  'chat-verify'() {
    if (!chat.verification) return;
    const code = chat.verification.match(/.{1,4}/g).join(' ');
    showDialog(t('Код безопасности'),'<p>' + text(t('Сравни этот код с другом через другой канал')) + '</p>'
      + '<code class="chat-verification">' + text(code) + '</code>'
      + actionButton('chat-copy-code',t('Скопировать'),'copy'));
  },
  'chat-copy-code'() {
    if (!chat.verification || typeof sendGame !== 'function') return;
    sendGame('clipboard-copy',{text:chat.verification.match(/.{1,4}/g).join(' ')});
    toast(t('Скопировано'));
  },
  'cosmetic-turn'() {
    state.cosmeticYaw = Math.cos(state.cosmeticYaw * Math.PI / 180) < 0 ? -19 : 158;
    render(false,false);
  },
  'cosmetic-show-all'() { state.cosmeticOnlyEquipped = false; cosmeticHover = ''; render(false,false); },
  'cosmetic-show-equipped'() { state.cosmeticOnlyEquipped = true; cosmeticHover = ''; render(false,false); },
  'chat-bottom'() { const box = $('chat-messages'); box.scrollTop = box.scrollHeight; syncChatBottom(); },
  'center-window'() {
    state.windowX = state.windowY = 0;
    positionWindow();
    if (typeof sendGame === 'function') sendGame('window-position',{x:0,y:0});
  },
  'chat-friends'() { selectPage('friends'); },
  publication() { state.publication = !state.publication; render(false,false); },
  'friend-profile-back'() { state.friendProfile = null; render(); },
  async copy() {
    try { await navigator.clipboard.writeText(currentAccount().uid); toast(t('ID скопирован')); }
    catch { toast('ID: ' + currentAccount().uid); }
  },
  accounts:showAccounts,
  'account-back'() {
    renderAccount();
    fitCategories();
    for (const part of $('account-pop').children) appear(part,-5,0,160);
    $('account-pop').querySelector('[data-action="accounts"]')?.focus();
  },
  'my-configs'() { configTab('published'); selectPage('configs'); },
  back() {
    state.page = state.returnPage;
    render();
    const grid = $('content').querySelector('.modules-grid');
    if (grid) grid.scrollTop = state.returnScroll;
  },
  'close-dialog'() { closeDialog(); },
  'new-config'() {
    showDialog(t('Новый конфиг'),'<form id="add-config"><input name="name" required maxlength="32" placeholder="' + text(t('Название конфига')) + '" aria-label="' + text(t('Название')) + '">'
      + '<button type="submit" class="button primary">' + text(t('Сохранить')) + '</button></form>');
  },
  'apply-config'() { state.enabled = new Set(currentConfig().modules); toast(t('Конфиг применён в макете')); },
  'save-config'() {
    const item = currentConfig();
    if (configs.local.some(entry => entry.name === item.name)) return toast(t('Конфиг уже в библиотеке'));
    configs.local.push({...item,key:++state.seq,note:t('Сохранён из каталога')});
    toast(t('Конфиг добавлен в библиотеку'));
  },
  'remove-config'() {
    configs[state.configTab] = configs[state.configTab].filter(item => item.key !== state.config);
    configTab(state.configTab); render();
  },
  'apply-preset'() { state.appliedPreset = state.preset; render(false); toast(t('Пресет применён в макете')); },
  'new-point'() {
    showDialog(t('Новая метка'),'<form id="add-point"><input name="name" required maxlength="32" placeholder="' + text(t('Название')) + '" aria-label="' + text(t('Название')) + '">'
      + '<button type="submit" class="button primary">' + text(t('Добавить')) + '</button></form><p>' + text(t('Для макета используются координаты 0 / 64 / 0.')) + '</p>');
  },
  'remove-point'() {
    points.splice(points.findIndex(item => item.key === state.point),1); state.point = points[0]?.key ?? null; render();
  },
  'module-demo'() { toast(t('Эта команда доступна в игре')); }
};
document.addEventListener('click', event => {
  const button = event.target.closest('button');
  if (!event.target.closest('#chat-context-menu')) closeChatContext();
  if (!event.composedPath().includes($('account'))) accountOpen(false);
  if (openChoice && event.target.closest('[data-choice-toggle]') !== openChoice
      && !event.target.closest('#choice-popup')) showChoices(openChoice,false);
  if (!button) return;
  const data = button.dataset;
  if (data.replyJump !== undefined) {
    const target = $('chat-messages')?.querySelector('[data-chat-message="' + data.replyJump + '"]');
    if (target) {
      target.scrollIntoView({block:'nearest'});
      appear(target,0,0,270);
    }
  } else if (data.choiceToggle !== undefined) {
    showChoices(button,button.getAttribute('aria-expanded') !== 'true');
  } else if (data.choiceOption !== undefined && openChoice) {
    const trigger = openChoice;
    const index = Number(trigger.dataset.choiceToggle), value = Number(data.choiceOption);
    state.values.set(state.module + ':' + index,value);
    updateChoice(trigger,value);
    showChoices(trigger,false);
    trigger.focus({preventScroll:true});
    sendGame('setting',{id:state.module,index,value});
  } else if (data.page) {
    selectPage(data.page);
  } else if (data.friendTab) {
    state.friendTab = data.friendTab;
    render(true,false);
    if (!document.documentElement.hasAttribute('data-pointer-focus'))
      $('content').querySelector('[data-friend-tab][aria-selected="true"]').focus({preventScroll:true});
  } else if (data.friendProfile) {
    state.friendProfile = data.friendProfile;
    render();
  } else if (data.cosmetic) {
    state.cosmetic = data.cosmetic;
    cosmeticSuppressed = data.cosmetic;
    cosmeticHover = '';
    render(false,false);
  } else if (data.cosmeticSlot) {
    state.cosmeticSlot = data.cosmeticSlot;
    state.cosmeticQuery = cosmeticHover = '';
    render(false,false);
  }
  else if (data.action) actions[data.action]?.();
  else if (data.accent !== undefined) {
    state.accent = data.accent;
    applyAccent();
    try { localStorage.setItem(accentKey,state.accent); } catch {}
  }
  else if (data.look) {
    if (data.look === 'theme') {
      changeTheme(data.value,button);
      return;
    }
    state[data.look] = data.value;
    if (data.look === 'toggleStyle') {
      try { localStorage.setItem(toggleStyleKey,state.toggleStyle); } catch {}
    }
    if (data.look === 'windowSize') {
      try { localStorage.setItem(windowSizeKey,state.windowSize); } catch {}
      animateWindowSize();
    }
    applyLook();
    const selected = data.look === 'languageChoice' ? $('language-trigger')
      : $('content').querySelector('[data-look="' + data.look + '"][data-value="' + data.value + '"]');
    selected?.focus({preventScroll:true});
  } else if (data.settingsGroup !== undefined) {
    const content = $('content'), group = $('settings-group-' + data.settingsGroup);
    for (const link of content.querySelectorAll('[data-settings-group]')) {
      if (link === button) link.setAttribute('aria-current','location');
      else link.removeAttribute('aria-current');
    }
    content.scrollTo({top:content.scrollTop + group.getBoundingClientRect().top - content.getBoundingClientRect().top - 12,
      behavior:reducedMotion.matches ? 'instant' : 'smooth'});
    if (!reducedMotion.matches) {
      group.querySelector('.sheet').animate([
        {borderColor:'var(--rule)'},
        {borderColor:'var(--accent)',offset:.45},
        {borderColor:'var(--rule)'}
      ],{duration:630,easing:'ease-in-out'});
    }
  } else if (data.moduleSettings) {
    state.returnPage = state.page;
    state.returnScroll = $('content').querySelector('.modules-grid')?.scrollTop ?? 0;
    state.module = data.moduleSettings;
    state.page = 'module';
    render();
  } else if (data.moduleToggle) {
    const id = data.moduleToggle, enabled = !state.enabled.has(id);
    if (enabled) state.enabled.add(id); else state.enabled.delete(id);
    for (const control of document.querySelectorAll('[data-module-toggle="' + id + '"]')) {
      control.setAttribute(control.getAttribute('role') === 'switch' ? 'aria-checked' : 'aria-pressed',String(enabled));
      const label = control.querySelector('.toggle-text');
      if (label) {
        label.textContent = enabled ? 'ON' : 'OFF';
        appear(label,0,enabled ? 3 : -3,130);
      }
      if (control.classList.contains('module-enable')) control.querySelector('.module-state').textContent = enabled ? t('Включён') : t('Выключен');
    }
  } else if (data.favorite) {
    const id = data.favorite, favorite = !state.favorites.has(id);
    if (favorite) state.favorites.add(id); else state.favorites.delete(id);
    button.setAttribute('aria-pressed',String(favorite));
    if (state.page === 'favorites' && !state.query) render(false); else renderNavigation();
  } else if (data.account) {
    state.account = Number(data.account);
    renderAccount();
    render(false);
    $('account-pop').querySelector('[data-action="accounts"]')?.focus();
  } else if (data.configTab) { configTab(data.configTab); render(false); }
  else if (data.config) { state.config = Number(data.config); render(false); }
  else if (data.preset !== undefined) { state.preset = Number(data.preset); render(false); }
  else if (data.point) { state.point = Number(data.point); render(false); }
  else if (data.keybind !== undefined) { button.textContent = t('Нажми клавишу'); button.dataset.listening = 'true'; }
});
$('account-trigger').addEventListener('click', () => {
  const open = $('account-trigger').getAttribute('aria-expanded') === 'false';
  if (open) renderAccount();
  accountOpen(open);
});
$('category-toggle').addEventListener('click', () => {
  $('category-list').setAttribute('data-animate','');
  setCategoriesExpanded(!state.categoriesExpanded);
  if (typeof sendGame === 'function') sendGame('look',{key:'categoriesExpanded',value:state.categoriesExpanded});
});
$('search').addEventListener('input', event => {
  if (!modulePage()) {
    state.page = 'all';
    if (typeof sendGame === 'function') sendGame('section',{page:'all'});
  }
  state.query = event.target.value;
  render(true,false);
});
$('theme').addEventListener('click', event => changeTheme((themeRequest?.theme ?? state.theme) === 'dark' ? 'light' : 'dark',event.currentTarget));
$('density').addEventListener('click', () => { state.density = state.density === 'normal' ? 'compact' : 'normal'; applyLook(); });
$('close-window').addEventListener('click', () => sendGame('close'));
$('reopen').addEventListener('click', () => {
  $('window').hidden = false;
  $('reopen').hidden = true;
  fitGrid();
  appear($('window'),0,8,205);
  $('search').focus();
});
window.addEventListener('resize', () => {
  if (openChoice) showChoices(openChoice,false);
  $('window').removeAttribute('data-size-transition');
  fitWindow();
});
$('content').addEventListener('scroll', () => {
  if (openChoice) showChoices(openChoice,false);
  if (state.page === 'cosmetics') scheduleCosmeticPreview();
});
new ResizeObserver(() => { fitGrid(); fitCategories(); positionWindow(); }).observe($('content'));
document.addEventListener('input', event => {
  const target = event.target;

  if (target.id === 'window-scale') {
    const value = target.value + '%';
    target.setAttribute('aria-valuetext',value);
    updateRange(target,'%');
  }
  if (target.dataset.setting !== undefined) {
    state.values.set(state.module + ':' + target.dataset.setting,target.type === 'checkbox' ? target.checked : target.value);
    if (target.type === 'range') updateRange(target);
  }
});
$('content').addEventListener('change', event => {

  if (event.target.id !== 'window-scale') return;
  state.windowScale = Number(event.target.value);
  try { localStorage.setItem(windowScaleKey,state.windowScale); } catch {}
  $('window').removeAttribute('data-size-transition');
  fitWindow();
  requestAnimationFrame(() => event.target.scrollIntoView({block:'nearest',inline:'nearest'}));
});
document.addEventListener('submit', event => {
  const form = event.target;
  event.preventDefault();
  const name = new FormData(form).get('name')?.trim();
  if (!name) return;
  if (form.id === 'add-account') {
    accounts.push({key:++state.seq,name,uid:'DEMO-' + state.seq,avatar:state.seq % 2 ? 'alex' : 'steve',plan:'Free'});
    showAccounts();
  } else if (form.id === 'add-config') {
    const key = ++state.seq;
    configs.local.push({key,name,note:t('Сохранён на этом устройстве'),author:currentAccount().name,modules:[...state.enabled]});
    configTab('local'); state.config = key; closeDialog(); render(false);
  } else if (form.id === 'add-point') {
    state.point = ++state.seq;
    points.push({key:state.point,name,xyz:[0,64,0]}); closeDialog(); render(false);
  }
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && chatContextId) {
    closeChatContext();
    event.preventDefault();
    return;
  }
  if ((event.key === 'ArrowDown' || event.key === 'ArrowUp')
      && (event.target.id === 'language-trigger' || openChoice?.id === 'language-trigger' && event.target.closest('#choice-popup'))) {
    const trigger = $('language-trigger');
    if (openChoice !== trigger) showChoices(trigger,true);
    const options = Array.from($('choice-popup').children);
    const index = options.indexOf(event.target);
    const direction = event.key === 'ArrowDown' ? 1 : -1;
    const next = index < 0 ? options.find(option => option.getAttribute('aria-pressed') === 'true')
      : options[(index + direction + options.length) % options.length];
    (next || options[0])?.focus({preventScroll:true});
    event.preventDefault();
    return;
  }
  if (event.key === 'Escape' && openChoice) {
    const trigger = openChoice;
    showChoices(trigger,false);
    trigger.focus({preventScroll:true});
    event.preventDefault();
    return;
  }
  const listening = event.target.closest('[data-listening="true"]');
  if (listening) {
    event.preventDefault();
    delete listening.dataset.listening;
    if (event.key === 'Escape') listening.textContent = state.values.get(state.module + ':' + listening.dataset.keybind) || t('Назначить');
    else {
      const value = event.key === ' ' ? 'Space' : event.key;
      state.values.set(state.module + ':' + listening.dataset.keybind,value); listening.textContent = value;
    }
    return;
  }
  if (event.key === 'Escape' && !$('dialog').hasAttribute('open') && $('account-trigger').getAttribute('aria-expanded') === 'true') {
    event.preventDefault(); accountOpen(false); $('account-trigger').focus();
  }
  if (event.key === '/' && !$('dialog').hasAttribute('open') && !$('window').hidden && !event.target.closest('input, select, textarea')) {
    event.preventDefault(); $('search').focus();
  }
});
for (const element of document.querySelectorAll('[data-icon]')) element.innerHTML = icon(element.dataset.icon);
renderAccount();
applyLook();
render();


