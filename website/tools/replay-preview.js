(() => {
  const byId = id => document.getElementById(id);
  const query = new URLSearchParams(location.search);
  const titleMenu = byId('title-menu');
  const pause = byId('pause');
  const screen = byId('replay');
  const pauseOpener = byId('open-replay');
  const titleOpener = byId('title-open-replay');
  const enabled = byId('enabled');
  const duration = byId('duration');
  const bitrateMode = byId('bitrate-mode');
  const bitrate = byId('bitrate');
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const library = byId('replay-library');
  const settings = byId('replay-settings');
  const clipList = byId('clip-list');
  const editor = byId('clip-editor');
  const editorPosition = byId('edit-position');
  const startInput = byId('trim-start');
  const endInput = byId('trim-end');
  const startHandle = byId('start-handle');
  const endHandle = byId('end-handle');
  const trimTrack = byId('trim-track');
  const trimSave = byId('trim-save');
  const tabs = [byId('tab-clips'), byId('tab-settings')];
  const filters = [byId('filter-all'), byId('filter-favorites')];
  const date = new Intl.DateTimeFormat('ru-RU', {dateStyle: 'medium', timeStyle: 'short'});
  const number = new Intl.NumberFormat('ru-RU', {maximumFractionDigits: 1});
  const plurals = new Intl.PluralRules('ru');
  const clipImage = '../assets/gameplay-orion.webp';
  const clips = [{title: 'Откат из мира', at: new Date('2026-11-25T18:24:00'), seconds: 24, format: '1080p · 60 FPS', favorite: false}];
  const defaultBindings = {save: {code: 'F10', modifiers: 2}, toggle: {code: 'F9', modifiers: 2}};
  const bindingKey = 'gleam.replay-preview.bindings';
  const bindingButtons = [...document.querySelectorAll('[data-bind]')];
  const recording = ['resolution', 'fps', 'quality', 'bitrate-mode', 'bitrate'];
  const rateBase = {720: 9, 1080: 16, 1440: 25, source: 16};
  const rateFactors = {performance: .75, balanced: 1, quality: 1.25};
  const version = /^(\d+)(?:\.(\d+))?/.exec(query.get('version') || '26.3');
  const major = Number(version?.[1]);
  const minor = major > 26 ? Infinity : Number(version?.[2] ?? 3);
  const multiplayer = query.get('world') === 'multi';

  let view, playingSince = null, available = 0, savePending = false;
  let favoritesOnly = false, clipFocus, edit;
  let previewTimer, replayTimer, closingTimer, replayOpener;
  let bindingAction, bindings = readBindings();
  let activeBitrate = rate();

  if (multiplayer) byId('exit').querySelector('span').textContent = 'Отключиться';
  if (major >= 26) {
    const left = byId('utility-left');
    const right = byId('utility-right');
    if (multiplayer && query.get('links') === '1') addUtility(left, 'links', 'Ссылки сервера');
    if (minor >= 3 || !multiplayer) {
      const label = minor >= 3 ? 'Настройки мира' : minor >= 2 ? 'Настройки сетевой игры' : 'Открыть для сети';
      addUtility(left, 'world', label);
    }
    if (minor >= 2) addUtility(left, 'friends', 'Друзья');
    addUtility(right, 'feedback', 'Обратная связь');
    addUtility(right, 'bug', 'Сообщить об ошибке');
    addUtility(right, minor >= 4 ? 'players' : 'report', minor >= 4 ? 'Другие игроки' : 'Жалоба на игрока', !multiplayer);
  }

  function addUtility(group, icon, label, disabled = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'utility';
    button.title = disabled ? label + ' · доступно в сетевой игре' : label;
    button.setAttribute('aria-label', label);
    button.dataset.preview = label + ' · действие в Minecraft';
    button.disabled = disabled;
    button.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-' + icon + '"/></svg>';
    group.append(button);
  }

  function readBindings() {
    try {
      const stored = JSON.parse(localStorage.getItem(bindingKey));
      const valid = value => value && typeof value.code === 'string' && /^[A-Z][A-Za-z0-9]{0,30}$/.test(value.code) &&
        Number.isInteger(value.modifiers) && value.modifiers >= 0 && value.modifiers <= 15;
      if (valid(stored?.save) && valid(stored?.toggle) && !sameBinding(stored.save, stored.toggle)) return stored;
    } catch {}
    return {...defaultBindings};
  }

  function writeBindings() {
    try { localStorage.setItem(bindingKey, JSON.stringify(bindings)); } catch {}
  }

  function sameBinding(left, right) {
    return left.code === right.code && left.modifiers === right.modifiers;
  }

  function getBinding(event) {
    const modifiers = (event.ctrlKey ? 1 : 0) | (event.altKey ? 2 : 0) |
      (event.shiftKey ? 4 : 0) | (event.metaKey ? 8 : 0);
    return {code: event.code, modifiers};
  }

  function bindingLabel(binding) {
    const keys = ['Ctrl', 'Alt', 'Shift', 'Meta'].filter((_, bit) => binding.modifiers & (1 << bit));
    return [...keys, binding.code.replace(/^(Key|Digit)(?=[A-Z0-9]$)/, '')].join(' + ');
  }

  function syncBindings(message = '') {
    for (const button of bindingButtons) {
      const action = button.dataset.bind;
      const listening = action === bindingAction;
      button.toggleAttribute('data-listening', listening);
      byId(action + '-binding').textContent = listening ? 'Нажми клавишу' : bindingLabel(bindings[action]);
    }
    byId('binding-note').textContent = message;
  }

  function pickBinding(event) {
    event.preventDefault();
    if (event.repeat) return;
    if (event.key === 'Escape') {
      bindingAction = null;
      syncBindings('Назначение отменено');
      return;
    }
    if (!event.code || /^(Alt|Shift|Control|Meta)(Left|Right)$/.test(event.code)) return;
    const candidate = getBinding(event);
    const other = bindingAction === 'save' ? 'toggle' : 'save';
    if (sameBinding(candidate, bindings[other])) {
      syncBindings('Это сочетание уже назначено другому действию');
      return;
    }
    bindings[bindingAction] = candidate;
    bindingAction = null;
    writeBindings();
    syncBindings('Сочетание сохранено');
  }

  function length() { return Number(duration.value); }
  function seconds() {
    if (!enabled.checked) return 0;
    const elapsed = playingSince === null ? 0 : (performance.now() - playingSince) / 1000;
    return Math.min(length(), available + elapsed);
  }
  function time(value) {
    const whole = Math.floor(value + .00001);
    const clock = String(Math.floor(whole / 60)).padStart(2, '0') + ':' + String(whole % 60).padStart(2, '0');
    const tenth = Math.round((value - whole) * 10);
    return tenth > 0 && tenth < 10 ? clock + ',' + tenth : clock;
  }
  function rate() {
    if (bitrateMode.value === 'manual') return Math.max(2, Math.min(60, Number(bitrate.value) || 16));
    const profile = rateFactors[byId('quality').value];
    const frameRate = byId('fps').value === '60' ? 1 : .65;
    return Math.round(rateBase[byId('resolution').value] * profile * frameRate);
  }
  function format() {
    const source = byId('resolution').value;
    return (source === 'source' ? 'Исходное' : source + 'p') + ' · ' + byId('fps').value + ' FPS';
  }
  function syncRecording() {
    bitrate.hidden = bitrateMode.value !== 'manual';
    if (bitrate.hidden) bitrate.value = rate();
    byId('estimate').textContent = format() + ' · ' + rate() + ' Мбит/с';
  }
  function setEnabled(on, initialSeconds = 0) {
    enabled.checked = on;
    available = on ? initialSeconds : 0;
    playingSince = on && view === 'playing' ? performance.now() : null;
  }

  function setView(next) {
    view = next;
    document.body.dataset.view = next;
    titleMenu.hidden = next !== 'title';
    pause.hidden = next !== 'pause';
    byId('shade').hidden = next === 'playing';
    byId('show-pause').hidden = next !== 'playing';
  }
  function resume() {
    const newWorld = view === 'title';
    if (newWorld) available = 0;
    playingSince = enabled.checked ? performance.now() : null;
    setView('playing');
    byId('show-pause').focus();
  }
  function showPause() {
    available = seconds();
    playingSince = null;
    setView('pause');
    byId('resume').focus();
  }
  function showTitle() {
    available = 0;
    playingSince = null;
    setView('title');
    titleOpener.focus();
  }

  function renderClips() {
    const grid = byId('clip-grid');
    const visible = favoritesOnly ? clips.filter(clip => clip.favorite) : clips;
    const label = {one: 'клип', few: 'клипа', many: 'клипов'}[plurals.select(visible.length)];
    byId('clip-count').textContent = visible.length + ' ' + label;
    byId('clip-empty').hidden = visible.length > 0;
    grid.replaceChildren(...visible.map(clip => {
      const card = byId('clip-template').content.firstElementChild.cloneNode(true);
      const open = card.querySelector('.clip-open');
      const star = card.querySelector('.favorite');
      card.querySelector('img').src = clipImage;
      card.querySelector('.clip-length').textContent = time(clip.seconds);
      card.querySelector('.clip-name').textContent = clip.title;
      card.querySelector('.clip-meta').textContent = date.format(clip.at);
      open.setAttribute('aria-label', 'Открыть редактор · ' + clip.title);
      function describeFavorite() {
        star.setAttribute('aria-pressed', String(clip.favorite));
        star.title = clip.favorite ? 'Убрать из избранного' : 'В избранное';
        star.setAttribute('aria-label', star.title + ' · ' + clip.title);
      }
      describeFavorite();
      star.addEventListener('click', () => {
        clip.favorite = !clip.favorite;
        if (favoritesOnly && !clip.favorite) { renderClips(); filters[1].focus(); }
        else describeFavorite();
      });
      open.addEventListener('click', () => openEditor(clip, open));
      return card;
    }));
  }

  function openEditor(clip, opener) {
    edit = {clip, start: 0, end: clip.seconds};
    clipFocus = opener;
    byId('edit-title').textContent = clip.title;
    byId('edit-meta').textContent = date.format(clip.at);
    byId('edit-format').textContent = clip.format;
    byId('edit-duration').textContent = time(clip.seconds);
    startHandle.max = endHandle.max = clip.seconds;
    editorPosition.max = clip.seconds;
    editorPosition.value = '0';
    clipList.hidden = true;
    editor.hidden = false;
    renderTrim();
    byId('replay-body').scrollTop = 0;
    byId('edit-title').focus();
  }

  function returnToClips(restoreFocus = false) {
    editor.hidden = true;
    clipList.hidden = false;
    edit = null;
    if (restoreFocus) (clipFocus?.isConnected ? clipFocus : filters[Number(favoritesOnly)]).focus();
    clipFocus = null;
  }

  function decimal(value) { return Math.round(value * 10) / 10; }

  function renderTrim() {
    startInput.max = decimal(edit.end - .1);
    endInput.min = decimal(edit.start + .1);
    endInput.max = edit.clip.seconds;
    startInput.value = edit.start;
    endInput.value = edit.end;
    startHandle.value = edit.start;
    endHandle.value = edit.end;
    startHandle.setAttribute('aria-valuetext', time(edit.start));
    endHandle.setAttribute('aria-valuetext', time(edit.end));
    editorPosition.setAttribute('aria-valuetext', time(Number(editorPosition.value)));
    trimTrack.style.setProperty('--trim-start', (edit.start / edit.clip.seconds * 100) + '%');
    trimTrack.style.setProperty('--trim-end', ((1 - edit.end / edit.clip.seconds) * 100) + '%');
    byId('edit-clock').textContent = time(Number(editorPosition.value)) + ' / ' + time(edit.clip.seconds);
    byId('trim-result').textContent = 'Длина отрезка · ' + number.format(decimal(edit.end - edit.start)) + ' с';
    trimSave.disabled = edit.start === 0 && edit.end === edit.clip.seconds;
  }

  function setTrim(edge, value) {
    if (Number.isFinite(value)) {
      if (edge === 'start') edit.start = decimal(Math.max(0, Math.min(edit.end - .1, value)));
      else edit.end = decimal(Math.max(edit.start + .1, Math.min(edit.clip.seconds, value)));
    }
    renderTrim();
  }

  function selectTab(index, focus = false) {
    bindingAction = null;
    syncBindings();
    returnToClips();
    tabs.forEach((tab, position) => {
      tab.setAttribute('aria-selected', String(position === index));
      tab.tabIndex = position === index ? 0 : -1;
    });
    library.hidden = index !== 0;
    settings.hidden = index !== 1;
    byId('replay-body').scrollTop = 0;
    if (focus) tabs[index].focus();
  }

  function toast(message) {
    if (screen.open) {
      const status = byId('replay-status');
      clearTimeout(replayTimer);
      status.textContent = message;
      replayTimer = setTimeout(() => { status.textContent = ''; }, 2500);
      return;
    }
    const status = byId('toast');
    clearTimeout(previewTimer);
    status.textContent = message;
    status.hidden = false;
    previewTimer = setTimeout(() => { status.hidden = true; }, 2500);
  }

  function openReplays(opener, tab = 0) {
    clearTimeout(closingTimer);
    replayOpener = opener;
    if (!screen.open) { selectTab(tab); screen.showModal(); }
    opener.setAttribute('aria-expanded', 'true');
    if (motion.matches) screen.dataset.visible = '';
    else requestAnimationFrame(() => requestAnimationFrame(() => {
      if (screen.open && opener.getAttribute('aria-expanded') === 'true') screen.dataset.visible = '';
    }));
  }
  function closeReplays() {
    if (!screen.open || replayOpener.getAttribute('aria-expanded') === 'false') return;
    const opener = replayOpener;
    bindingAction = null;
    delete screen.dataset.visible;
    opener.setAttribute('aria-expanded', 'false');
    const finish = () => {
      screen.close();
      returnToClips();
      byId('replay-status').textContent = '';
      opener.focus();
    };
    if (motion.matches) finish();
    else closingTimer = setTimeout(finish, 175);
  }
  function back() {
    if (edit) returnToClips(true);
    else closeReplays();
  }

  function saveClip() {
    const buffered = Math.floor(seconds());
    if (savePending) return;
    if (buffered < 1) {
      toast(enabled.checked ? 'Буфер пока пуст' : 'Откаты выключены');
      return;
    }
    const clip = {title: 'Откат ' + (clips.length + 1), at: new Date(), seconds: buffered, format: format(), favorite: false};
    savePending = true;
    setTimeout(() => {
      savePending = false;
      clips.unshift(clip);
      renderClips();
      toast('Клип сохранён · ' + buffered + ' с');
    }, 330);
  }

  for (const opener of [pauseOpener, titleOpener]) opener.addEventListener('click', () => openReplays(opener));
  byId('close-replay').addEventListener('click', back);
  byId('editor-back').addEventListener('click', () => returnToClips(true));
  screen.addEventListener('cancel', event => { event.preventDefault(); back(); });
  screen.addEventListener('click', event => {
    if (event.target !== screen) return;
    const bounds = screen.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right ||
        event.clientY < bounds.top || event.clientY > bounds.bottom) closeReplays();
  });
  filters.forEach((filter, index) => filter.addEventListener('click', () => {
    favoritesOnly = index === 1;
    filters.forEach((button, position) => button.setAttribute('aria-pressed', String(position === index)));
    renderClips();
  }));
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(index, true));
    tab.addEventListener('keydown', event => {
      if (bindingAction || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      selectTab(event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index, true);
    });
  });
  editorPosition.addEventListener('input', renderTrim);
  startInput.addEventListener('change', () => setTrim('start', startInput.valueAsNumber));
  endInput.addEventListener('change', () => setTrim('end', endInput.valueAsNumber));
  startHandle.addEventListener('input', () => setTrim('start', startHandle.valueAsNumber));
  endHandle.addEventListener('input', () => setTrim('end', endHandle.valueAsNumber));
  byId('trim-reset').addEventListener('click', () => { edit.start = 0; edit.end = edit.clip.seconds; renderTrim(); });
  trimSave.addEventListener('click', () => {
    const original = edit.clip;
    clips.unshift({...original, title: original.title + ' — обрезка', at: new Date(), seconds: decimal(edit.end - edit.start), favorite: false});
    renderClips();
    toast('Копия сохранена');
  });
  enabled.addEventListener('change', () => setEnabled(enabled.checked));
  byId('resume').addEventListener('click', resume);
  byId('show-pause').addEventListener('click', showPause);
  byId('exit').addEventListener('click', showTitle);
  for (const button of titleMenu.querySelectorAll('[data-start]')) button.addEventListener('click', resume);
  for (const button of document.querySelectorAll('[data-preview]')) button.addEventListener('click', () => toast(button.dataset.preview));
  duration.addEventListener('change', () => { available = Math.min(available, length()); });
  for (const id of recording) byId(id).addEventListener('change', () => {
    const next = rate();
    if (id !== 'bitrate-mode' || next !== activeBitrate) setEnabled(enabled.checked);
    activeBitrate = next;
    if (id === 'bitrate') bitrate.value = next;
    syncRecording();
  });
  for (const button of bindingButtons) button.addEventListener('click', () => {
    bindingAction = button.dataset.bind;
    syncBindings('Esc отменяет назначение');
  });
  byId('reset-bindings').addEventListener('click', () => {
    bindings = {...defaultBindings};
    bindingAction = null;
    writeBindings();
    syncBindings('Восстановлены клавиши по умолчанию');
  });
  document.addEventListener('keydown', event => {
    if (bindingAction) { pickBinding(event); return; }
    const field = event.target.closest?.('input:not([type=checkbox]), select, textarea');
    if (!field) {
      const pressed = getBinding(event);
      if (sameBinding(pressed, bindings.save) || sameBinding(pressed, bindings.toggle)) {
        event.preventDefault();
        if (event.repeat) return;
        if (sameBinding(pressed, bindings.save)) saveClip();
        else {
          setEnabled(!enabled.checked);
          toast(enabled.checked ? 'Откаты включены' : 'Откаты выключены');
        }
        return;
      }
    }
    if (screen.open) return;
    if (event.key === 'Escape' && view !== 'title') {
      event.preventDefault();
      if (view === 'playing') showPause(); else resume();
      return;
    }
    if (view === 'playing' || !['ArrowUp', 'ArrowDown'].includes(event.key)) return;
    event.preventDefault();
    const menu = view === 'title' ? titleMenu : pause;
    const items = [...menu.querySelectorAll('button.item, .item .switch, .utility:enabled')];
    const index = items.indexOf(document.activeElement);
    const step = event.key === 'ArrowDown' ? 1 : -1;
    items[index < 0 ? 0 : (index + step + items.length) % items.length].focus();
  }, true);

  const directReplays = query.get('panel') === 'replays';
  setView(query.get('view') === 'title' ? 'title' : 'pause');
  const state = query.get('state');
  setEnabled(state === 'ready' || state === 'buffering', state === 'buffering' ? 24 : state === 'ready' ? length() : 0);
  syncRecording();
  syncBindings();
  renderClips();
  if (directReplays) openReplays(view === 'title' ? titleOpener : pauseOpener, query.get('tab') === 'settings' ? 1 : 0);
})();
