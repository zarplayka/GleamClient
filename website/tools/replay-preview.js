(() => {
  const byId = id => document.getElementById(id);
  const game = !!window.gleamReplayInGame;
  if (game) {
    const root = document.documentElement;
    root.setAttribute('data-pointer-focus', '');
    document.addEventListener('pointerdown', () => root.setAttribute('data-pointer-focus', ''), true);
    document.addEventListener('keydown', event => {
      if (event.key === 'Tab') root.removeAttribute('data-pointer-focus');
    }, true);
  }
  function iconMarkup(id) {
    const geometry = window.gleamReplayIcons?.[id];
    return geometry
      ? '<svg class="icon" aria-hidden="true" viewBox="0 0 24 24">' + geometry + '</svg>'
      : '<svg class="icon" aria-hidden="true"><use href="#' + id + '"/></svg>';
  }
  function setIcon(container, id) {
    const svg = container.querySelector('svg');
    if (svg.getAttribute('data-icon') === id) return;
    if (game) {
      svg.insertAdjacentHTML('beforebegin', iconMarkup(id));
      svg.remove();
    } else {
      svg.querySelector('use').setAttribute('href', '#' + id);
    }
    container.querySelector('svg').setAttribute('data-icon', id);
  }
  const query = game ? {get: key => key === 'panel' ? 'replays' : null} : new URLSearchParams(location.search);
  const live = game || query.get('client') === '1' && typeof window.ipc?.postMessage === 'function';
  const titleMenu = byId('title-menu');
  const pause = byId('pause');
  const screen = byId('replay');
  const pauseOpener = byId('open-replay');
  const titleOpener = byId('title-open-replay');
  const enabled = byId('enabled');
  const duration = byId('duration');
  const customDurationField = byId('custom-duration-field');
  const customDuration = byId('custom-duration');
  let customSeconds = Number(customDuration.value);
  const bitrateMode = byId('bitrate-mode');
  const bitrate = byId('bitrate');
  const motion = typeof matchMedia === 'function' ? matchMedia('(prefers-reduced-motion: reduce)') : {matches: false};
  const library = byId('replay-library');
  const settings = byId('replay-settings');
  const youtubeTemplate = byId('youtube-template');
  if (game) for (const element of settings.querySelectorAll('[data-game-only]')) element.hidden = false;
  const optionsMenu = byId('replay-options');
  let activePicker, highlightedOption, optionsMotion, optionTerm = '', optionTypedAt = 0;
  const preferenceTabs = [...settings.querySelectorAll('.settings-nav [role=tab]')].filter(tab => !tab.hidden);
  const preferencePanels = preferenceTabs.map(tab => byId(tab.getAttribute('aria-controls')));
  const panelAnimations = new WeakMap();
  let activePreference = 0;
  const clipList = byId('clip-list');
  const editor = byId('clip-editor');
  const editVideo = byId('edit-video');
  const videoSurface = byId('edit-video-surface');
  let player = {};
  let playing = false;
  let seekTimer, seekTarget;
  function flushGameSeek() {
    clearTimeout(seekTimer);
    seekTimer = undefined;
    post('seek', {position: seekTarget});
  }
  if (game) {
    let speed = 1, volume = 1, muted = false;
    Object.defineProperties(editVideo, {
      duration: {get: () => edit?.clip.seconds ?? NaN},
      currentTime: {
        get: () => seekTimer === undefined ? player.position || 0 : seekTarget,
        set(value) {
          seekTarget = value;
          if (seekTimer === undefined) seekTimer = setTimeout(flushGameSeek, 100);
        }
      },
      paused: {get: () => !playing},
      ended: {get: () => !!player.ended},
      seeking: {get: () => seekTimer !== undefined || !!player.seeking},
      volume: {get: () => volume, set(value) { volume = value; }},
      muted: {
        get: () => muted,
        set(value) { muted = !!value; post('audio-preview', {volume, muted}); }
      },
      playbackRate: {
        get: () => speed,
        set(value) { speed = value; post('speed', {value}); }
      },
      pause: {value() {
        if (!playing) return;
        playing = false;
        post('pause-preview');
        syncPlayer();
      }},
      play: {value() {
        playing = true;
        player.ended = false;
        post('play-preview');
        syncPlayer();
        return Promise.resolve();
      }},
      load: {value() {
        clearTimeout(seekTimer);
        seekTimer = undefined;
        post('unload-preview');
      }}
    });
  }
  const editFrame = byId('edit-frame');
  const editViewport = editFrame.parentElement;
  const previewPlay = byId('preview-play');
  const editPlay = byId('edit-play');
  const editPlayIcon = byId('edit-play-icon');
  const editPauseIcon = byId('edit-pause-icon');
  const editFavorite = byId('edit-favorite');
  const removeAction = byId('edit-remove');
  const removeConfirm = byId('remove-confirm');
  let previewLoad;
  const editorPosition = byId('edit-position');
  const editClock = byId('edit-clock');
  const startInput = byId('trim-start');
  const endInput = byId('trim-end');
  const startHandle = byId('start-handle');
  const endHandle = byId('end-handle');
  const trimTrack = byId('trim-track');
  const trimSelection = trimTrack.querySelector('.timeline-selection');
  const trimProgress = trimTrack.querySelector('.timeline-progress');
  const timeline = byId('timeline-controls');
  const trimSave = byId('trim-save');
  const trimApply = byId('trim-apply');
  const trimReset = byId('trim-reset');
  if (game) for (const field of [customDuration, bitrate, startInput, endInput]) {
    field.setAttribute('type', 'text');
    field.setAttribute('inputmode', field === startInput || field === endInput ? 'decimal' : 'numeric');
    field.addEventListener('keydown', event => {
      if ((event.ctrlKey || event.metaKey) && event.code === 'KeyA') {
        event.preventDefault();
        field.setSelectionRange(0, field.value.length);
      } else if (event.key === 'Enter') {
        event.preventDefault();
        field.blur();
      }
    });
  }
  const tabs = [byId('tab-clips'), byId('tab-settings')];
  const filters = [...clipList.querySelectorAll('[data-filter]')];
  const recordAction = byId('record-action');
  const recordTime = byId('record-time');
  const recordHud = byId('record-hud');
  const recordHudTime = byId('record-hud-time');
  const recordShortcut = byId('record-shortcut');
  const emptyFilters = {
    all: ['i-video', 'Пока нет клипов', 'Начни запись или сохрани откат, чтобы открыть его здесь.'],
    favorites: ['i-star', 'Пока нет избранных клипов', 'Нажми на звезду рядом с клипом, чтобы он появился здесь.'],
    replay: ['i-replay', 'Пока нет откатов', 'Включи буфер и сохрани откат горячей клавишей из настроек.'],
    recording: ['i-video', 'Пока нет записей', 'Начни запись в мире. После остановки она появится здесь.']
  };
  const date = game ? {format: value => value} : new Intl.DateTimeFormat('ru-RU', {dateStyle: 'medium', timeStyle: 'short'});
  const number = game ? {format: value => String(value).replace('.', ',')} : new Intl.NumberFormat('ru-RU', {maximumFractionDigits: 1});
  const plurals = game ? {select: value => value % 100 >= 11 && value % 100 <= 14 ? 'many'
    : value % 10 === 1 ? 'one' : value % 10 >= 2 && value % 10 <= 4 ? 'few' : 'many'} : new Intl.PluralRules('ru');
  const clipImage = '../assets/gameplay-orion.webp';
  const clips = live ? [] : [{kind: 'replay', title: 'Откат из мира', at: new Date('2026-11-25T18:24:00'), seconds: 24, favorite: false}];
  const clipNumbers = {replay: 1, recording: 0};
  const clipTypes = {replay: 'Откат', recording: 'Запись'};
  const defaultBindings = {
    save: {code: 'F10', modifiers: 2},
    toggle: {code: 'F9', modifiers: 2},
    record: {code: 'F8', modifiers: 2},
    ...(game ? {'save-publish': {code: 'F11', modifiers: 2}} : {})
  };
  const bindingIds = Object.keys(defaultBindings);
  const bindingKey = 'gleam.replay-preview.bindings';
  const bindingButtons = [...document.querySelectorAll('[data-bind]')].filter(button => bindingIds.includes(button.dataset.bind));
  const videoSettings = ['resolution', 'fps', 'quality', 'bitrate-mode', 'bitrate'];
  const rateBase = {720: 9, 1080: 16, 1440: 25, source: 16};
  const rateFactors = {performance: .75, balanced: 1, quality: 1.25};
  const version = /^(\d+)(?:\.(\d+))?/.exec(query.get('version') || '26.3');
  const major = Number(version?.[1]);
  const minor = major > 26 ? Infinity : Number(version?.[2] ?? 3);
  const multiplayer = query.get('world') === 'multi';

  let view, playingSince = null, available = 0, savePending = false;
  let activeFilter = 'all', clipFocus, edit;
  let manualRecording, recordingTimer;
  let previewTimer, replayTimer, closingTimer, replayOpener;
  let bindingAction, bindings = readBindings();
  let activeBitrate = rate();
  let liveState, liveMessage = 0, liveClips = '', postingSettings = false;
  let liveCommands = Promise.resolve();

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
    button.innerHTML = iconMarkup('i-' + icon);
    group.append(button);
  }

  function readBindings() {
    if (live) return {...defaultBindings};
    try {
      const stored = JSON.parse(localStorage.getItem(bindingKey));
      const valid = value => value && typeof value.code === 'string' && /^[A-Z][A-Za-z0-9]{0,30}$/.test(value.code) &&
        Number.isInteger(value.modifiers) && value.modifiers >= 0 && value.modifiers <= 15;
      if (valid(stored?.save) && valid(stored?.toggle) && !sameBinding(stored.save, stored.toggle)) {
        const record = valid(stored.record) ? stored.record : stored.record === null ? null : defaultBindings.record;
        const conflict = record && [stored.save, stored.toggle].some(binding => sameBinding(binding, record));
        return {save: stored.save, toggle: stored.toggle, record: conflict ? null : record};
      }
    } catch {}
    return {...defaultBindings};
  }

  function writeBindings() {
    if (live) { post('bindings', bindings); return; }
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
    if (!binding) return 'Не назначено';
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
    recordShortcut.textContent = bindings.record ? bindingLabel(bindings.record) : '';
    recordShortcut.hidden = !bindings.record;
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
    if (live && !/^(?:Key[A-Z]|Digit[0-9]|F(?:[1-9]|1[0-9]|2[0-4])|Insert|Delete|Home|End|PageUp|PageDown|Arrow(?:Up|Down|Left|Right)|Space|Enter|NumpadEnter)$/.test(event.code)) {
      syncBindings('Эта клавиша недоступна в Minecraft');
      return;
    }
    const candidate = getBinding(event);
    if (bindingIds.some(action => action !== bindingAction && bindings[action] && sameBinding(candidate, bindings[action]))) {
      syncBindings('Это сочетание уже назначено другому действию');
      return;
    }
    bindings[bindingAction] = candidate;
    bindingAction = null;
    writeBindings();
    syncBindings('Сочетание сохранено');
  }

  function length() { return duration.value === 'custom' ? customSeconds : Number(duration.value); }
  function updateDuration() {
    available = Math.min(available, length());
    syncRecording();
  }
  function seconds() {
    if (!enabled.checked) return 0;
    const elapsed = playingSince === null ? 0 : (performance.now() - playingSince) / 1000;
    return Math.min(length(), available + elapsed);
  }
  function time(value) {
    const whole = Math.floor(value + .00001);
    const hours = Math.floor(whole / 3600);
    const parts = [Math.floor(whole / 60) % 60, whole % 60];
    if (hours) parts.unshift(hours);
    const clock = parts.map(part => String(part).padStart(2, '0')).join(':');
    const tenth = Math.round((value - whole) * 10);
    return tenth > 0 && tenth < 10 ? clock + ',' + tenth : clock;
  }
  function rate() {
    if (bitrateMode.value === 'manual') return Math.max(2, Math.min(60, Number(bitrate.value) || 16));
    const profile = rateFactors[byId('quality').value];
    const frameRate = byId('fps').value === '60' ? 1 : .65;
    return Math.round(rateBase[byId('resolution').value] * profile * frameRate);
  }
  function syncRecording() {
    bitrate.hidden = bitrateMode.value !== 'manual';
    if (bitrate.hidden) bitrate.value = rate();
    const size = Math.round(length() * rate() / 8);
    const audio = byId('audio').value !== 'none' || byId('mic').checked;
    const bufferSeconds = Math.floor(256 * 1024 * 1024 * 8 / (rate() * 1_000_000 + (audio ? 1_536_000 : 0)));
    byId('estimate').textContent = live && bufferSeconds < length()
      ? 'Лимит буфера 256 МиБ. При этих настройках поместится около ' + bufferSeconds + ' с'
      : 'Около ' + size + ' МБ за ' + length() + ' с записи при ' + rate() + ' Мбит/с';
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
    syncManualRecording();
  }

  function post(action, values = {}) {
    if (game) {
      window.webkit.messageHandlers.paradox.postMessage(JSON.stringify({action, values}));
      return;
    }
    const body = JSON.stringify({action, values});
    liveCommands = liveCommands.then(async () => {
      try {
        const request = await fetch('api/action', {method: 'POST', headers: {'Content-Type': 'application/json'}, body});
        if (!request.ok) throw Error(request.statusText);
      } catch { toast('Нет связи с Minecraft'); }
    });
  }

  function publishSettings() {
    if (!live || !liveState || postingSettings) return;
    post('settings', {
      duration: duration.value, customSeconds, resolution: byId('resolution').value,
      fps: Number(byId('fps').value), quality: byId('quality').value, bitrateMode: bitrateMode.value,
      bitrate: Number(bitrate.value), audio: byId('audio').value, mic: byId('mic').checked
    });
  }

  function loadLiveSettings(values) {
    postingSettings = true;
    customSeconds = values.customSeconds;
    customDuration.value = customSeconds;
    bitrate.value = values.bitrate;
    byId('mic').checked = values.mic;
    for (const [id, value] of Object.entries({
      duration: values.duration, resolution: values.resolution, fps: values.fps, quality: values.quality,
      'bitrate-mode': values.bitrateMode, audio: values.audio
    })) {
      const control = byId(id);
      control.value = value;
      control.dispatchEvent(new Event('change'));
    }
    if (game) {
      byId('youtube-auto').checked = !!values.youtubeAuto;
      byId('youtube-privacy').value = values.youtubePrivacy || 'unlisted';
      byId('youtube-privacy').dispatchEvent(new Event('change'));
      if (document.activeElement !== youtubeTemplate) youtubeTemplate.value = values.youtubeTemplate || '{link}';
      if (!values.keys['save-publish']) delete bindings['save-publish'];
    }
    for (const [action, stored] of Object.entries(values.keys)) {
      const [mask, code] = stored.split(':');
      bindings[action] = {code, modifiers: Number(mask)};
    }
    syncBindings();
    postingSettings = false;
  }

  async function refreshLive() {
    try {
      const response = await fetch('api/state');
      if (!response.ok) throw Error(response.statusText);
      applyLive(await response.json());
    } catch {
      if (!liveState) toast('Нет связи с Minecraft');
    } finally { setTimeout(refreshLive, document.hidden ? 1600 : 450); }
  }

  function applyLive(snapshot) {
      if (game && (snapshot.width !== liveState?.width || snapshot.height !== liveState?.height)) {
        const w = Math.min(1480, Math.max(910, snapshot.width * .74), snapshot.width - (snapshot.width <= 620 ? 30 : 72));
        const h = Math.min(Math.max(550, snapshot.height * .75), snapshot.height - (snapshot.width <= 620 ? 32 : 60));
        screen.style.inset = 'auto';
        screen.style.margin = '0';
        screen.style.left = (snapshot.width - w) / 2 + 'px';
        screen.style.top = (snapshot.height - h) / 2 + 'px';
        screen.style.width = w + 'px';
        screen.style.height = h + 'px';
        requestAnimationFrame(layoutClipGrid);
      }
      if (snapshot.settings) {
        loadLiveSettings(snapshot.settings);
      }
      liveState = snapshot;
      enabled.checked = snapshot.buffer;
      if (game) {
        byId('youtube-status').textContent = snapshot.youtubeConnected ? 'Google подключён' : 'Google не подключён';
        byId('youtube-connect').textContent = snapshot.youtubeConnected ? 'Сменить аккаунт' : 'Подключить Google';
        byId('youtube-disconnect').hidden = !snapshot.youtubeConnected;
      }
      recordAction.hidden = !snapshot.inWorld;
      recordAction.disabled = snapshot.pending;
      recordAction.toggleAttribute('data-recording', !!snapshot.recording);
      setIcon(recordAction, snapshot.recording ? 'i-stop' : 'i-record');
      byId('record-label').textContent = snapshot.pending ? 'Подождите…' : snapshot.recording ? 'Остановить запись' : 'Начать запись';
      recordTime.hidden = !snapshot.recording || snapshot.pending;
      if (snapshot.recording) recordTime.textContent = time(game ? snapshot.recordSeconds : Math.max(0, Math.floor((Date.now() - snapshot.recordedAt) / 1000)));
      const identity = snapshot.clips && JSON.stringify(snapshot.clips);
      if (identity && liveClips !== identity) {
        liveClips = identity;
        clips.splice(0, clips.length, ...snapshot.clips);
        const selected = edit && clips.find(clip => clip.id === edit.clip.id);
        if (edit && !selected) returnToClips(true);
        else if (selected) {
          if (selected.revision !== edit.clip.revision) openEditor(selected, clipFocus);
          else {
            edit.clip = selected;
            editFavorite.setAttribute('aria-pressed', String(selected.favorite));
          }
        }
        renderClips();
      }
      if (game) syncPublication();
      if (snapshot.version > liveMessage) {
        liveMessage = snapshot.version;
        toast(snapshot.message);
      }
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
    if (manualRecording) stopRecording();
    available = 0;
    playingSince = null;
    setView('title');
    titleOpener.focus();
  }

  function renderClips() {
    const grid = byId('clip-grid');
    const visible = clips.filter(clip => activeFilter === 'all' ||
      (activeFilter === 'favorites' ? clip.favorite : clip.kind === activeFilter));
    const labels = activeFilter === 'recording' ? ['запись', 'записи', 'записей'] :
      activeFilter === 'replay' ? ['откат', 'отката', 'откатов'] : ['клип', 'клипа', 'клипов'];
    const label = labels[{one: 0, few: 1, many: 2}[plurals.select(visible.length)]];
    byId('clip-count').textContent = visible.length + ' ' + label;
    const empty = byId('clip-empty');
    empty.hidden = visible.length > 0;
    const [icon, heading, help] = emptyFilters[activeFilter];
    empty.querySelector('strong').textContent = heading;
    empty.querySelector('p').textContent = help;
    setIcon(empty, icon);
    grid.replaceChildren(...visible.map(clip => {
      const template = byId('clip-template');
      const card = (game ? template.firstElementChild : template.content.firstElementChild).cloneNode(true);
      const open = card.querySelector('.clip-open');
      const star = card.querySelector('.favorite');
      const image = card.querySelector('img');
      if (game) {
        card.querySelector('.clip-placeholder').hidden = !!clip.thumbnail;
        image.hidden = !clip.thumbnail;
        if (clip.thumbnail) image.src = clip.thumbnail;
        card.setAttribute('data-clip-id', clip.id);
      } else if (live) {
        card.querySelector('.clip-placeholder').hidden = false;
        image.addEventListener('error', () => { image.hidden = true; }, {once: true});
        image.loading = 'lazy';
        image.src = 'media/' + clip.id + '.jpg?v=' + clip.revision;
      } else image.src = clipImage;
      card.querySelector('.clip-length').textContent = time(clip.seconds);
      card.querySelector('.clip-name').textContent = clip.title;
      card.querySelector('.clip-meta').textContent = clipTypes[clip.kind] + ' · ' + date.format(clip.at);
      open.setAttribute('aria-label', 'Открыть редактор · ' + clip.title);
      if (clip === edit?.clip) clipFocus = open;
      function describeFavorite() {
        star.setAttribute('aria-pressed', String(clip.favorite));
        star.title = clip.favorite ? 'Убрать из избранного' : 'В избранное';
        star.setAttribute('aria-label', star.title + ' · ' + clip.title);
      }
      describeFavorite();
      star.addEventListener('click', () => {
        if (live) { post('favorite', {id: clip.id, favorite: !clip.favorite}); return; }
        clip.favorite = !clip.favorite;
        if (activeFilter === 'favorites' && !clip.favorite) {
          renderClips();
          clipFilterButton().focus();
        }
        else describeFavorite();
      });
      open.addEventListener('click', () => openEditor(clip, open));
      return card;
    }));
    if (game) requestAnimationFrame(() => { layoutClipGrid(); requestThumbnails(); });
  }

  function layoutClipGrid() {
    const grid = byId('clip-grid');
    const width = grid.clientWidth;
    if (width <= 0) return;
    const columns = Math.max(1, Math.floor((width + 14) / 226));
    const column = (width - (columns - 1) * 14) / columns;
    for (const card of grid.children) card.style.width = column + 'px';
  }

  function requestThumbnails() {
    if (clipList.hidden || library.hidden) return;
    const viewport = byId('replay-body').getBoundingClientRect();
    for (const card of byId('clip-grid').children) {
      const bounds = card.getBoundingClientRect();
      if (bounds.bottom < viewport.top || bounds.top > viewport.bottom + 170 || !bounds.width) continue;
      if (card.hasAttribute('data-thumbnail-requested')) continue;
      card.setAttribute('data-thumbnail-requested', '');
      if (card.querySelector('img').hidden) post('thumbnail', {id: card.getAttribute('data-clip-id')});
    }
  }

  function clipFilterButton() {
    return filters.find(button => button.dataset.filter === activeFilter);
  }

  function selectClipFilter(filter) {
    activeFilter = filter;
    for (const button of filters) button.setAttribute('aria-pressed', String(button.dataset.filter === filter));
    renderClips();
  }

  function clock() {
    const position = Number(editorPosition.value);
    const formatted = time(position);
    const clockText = formatted + ' / ' + time(edit.clip.seconds);
    if (editClock.textContent !== clockText) {
      editorPosition.setAttribute('aria-valuetext', formatted);
      editClock.textContent = clockText;
    }
    const progress = (position / edit.clip.seconds * 100) + '%';
    trimProgress.style.width = progress;
    timeline.children[2].style.left = progress;
  }

  function seekPreview(position, previewOnly = false) {
    if (!edit) return;
    editorPosition.value = Math.max(0, Math.min(edit.clip.seconds, position));
    if (live && Number.isFinite(editVideo.duration)) {
      editVideo.currentTime = Math.min(editVideo.duration, Number(editorPosition.value));
      if (game && !previewOnly) flushGameSeek();
    } else if (Number.isFinite(editVideo.duration) && editVideo.duration > 0) {
      const position = Number(editorPosition.value);
      const target = (edit.clip.sampleStart ?? 0) + position;
      edit.sampleCycle = Math.floor(target / editVideo.duration);
      let sampleTime = target % editVideo.duration;
      if (sampleTime === 0 && position === edit.clip.seconds && edit.sampleCycle > 0) {
        edit.sampleCycle--;
        sampleTime = editVideo.duration;
      }
      editVideo.currentTime = sampleTime;
    }
    clock();
  }

  function loadPreview() {
    if (live) return Promise.resolve();
    if (!previewLoad) {
      previewLoad = fetch('replay-demo.json').then(response => {
        if (!response.ok) throw Error('Replay example is unavailable');
        return response.json();
      }).then(({type, data}) => {
        const bytes = Uint8Array.from(atob(data), byte => byte.charCodeAt(0));
        editVideo.preload = 'auto';
        const url = URL.createObjectURL(new Blob([bytes], {type}));
        editVideo.src = url;
        window.addEventListener('pagehide', event => { if (!event.persisted) URL.revokeObjectURL(url); }, {once: true});
      }).catch(error => {
        previewLoad = null;
        throw error;
      });
    }
    return previewLoad;
  }

  function expandGamePreview(expanded) {
    if (expanded === editFrame.hasAttribute('data-expanded')) return;
    editFrame.toggleAttribute('data-expanded', expanded);
    document.body.toggleAttribute('data-video-expanded', expanded);
    if (expanded) document.body.appendChild(editFrame);
    else editViewport.insertBefore(editFrame, editViewport.firstChild);
  }

  function syncPlayer() {
    const playing = !editVideo.paused && !editVideo.ended;
    const label = playing ? 'Пауза' : 'Воспроизвести';
    if (editPlay.getAttribute('aria-label') !== label) editPlay.setAttribute('aria-label', label);
    if (previewPlay.hidden !== playing) previewPlay.hidden = playing;
    editPlayIcon.toggleAttribute('hidden', playing);
    editPauseIcon.toggleAttribute('hidden', !playing);
  }

  async function togglePreview() {
    if (!edit) return;
    if (!editVideo.paused) { editVideo.pause(); return; }
    const current = edit;
    try {
      await loadPreview();
      if (current !== edit) return;
      if (!game && Number(editorPosition.value) >= edit.clip.seconds) seekPreview(0);
      await editVideo.play();
    } catch (error) {
      if (current === edit && error.name !== 'AbortError') toast(live ? 'Не удалось открыть клип' : 'Демонстрационное видео не загрузилось');
    }
  }

  function syncVideoClock() {
    if (!edit || !Number.isFinite(editVideo.duration)) return;
    const sourceTime = live ? editVideo.currentTime : edit.sampleCycle * editVideo.duration + editVideo.currentTime;
    const position = Math.max(0, Math.min(edit.clip.seconds, sourceTime - (edit.clip.sampleStart ?? 0)));
    if (position >= edit.clip.seconds && !editVideo.paused) editVideo.pause();
    editorPosition.value = position;
    clock();
  }

  function saveStill() {
    if (game) { post('still'); return; }
    if (!edit || !editVideo.videoWidth || editVideo.readyState < 2) {
      toast('Кадр ещё загружается');
      return;
    }
    const title = edit.clip.title;
    const canvas = document.createElement('canvas');
    canvas.width = editVideo.videoWidth;
    canvas.height = editVideo.videoHeight;
    canvas.getContext('2d').drawImage(editVideo, 0, 0);
    canvas.toBlob(blob => {
      if (!blob) return;
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = title.replace(/[\\/:*?"<>|]/g, '_') + '.png';
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 5000);
    });
  }
  function syncPublication() {
    if (!edit) return;
    const button = byId('edit-publish');
    const uploading = liveState?.publishing?.includes(edit.clip.id);
    button.disabled = !!uploading;
    byId('edit-publish-label').textContent = uploading ? 'Загрузка на YouTube…'
      : edit.clip.published ? 'Копировать ссылку YouTube' : 'Опубликовать';
  }

  function openEditor(clip, opener) {
    editVideo.pause();
    showRemoveConfirm(false);
    edit = {clip, start: 0, end: clip.seconds, sampleCycle: 0};
    if (game) syncPublication();
    clipFocus = opener;
    byId('edit-title').textContent = clip.title;
    byId('edit-type').textContent = clipTypes[clip.kind];
    byId('edit-meta').textContent = date.format(clip.at);
    byId('edit-source').textContent = live ? 'Minecraft' : clip.source ?? 'Демонстрация';
    byId('edit-format').textContent = live ? 'H.264 · MP4' : 'WebM';
    byId('edit-duration').textContent = time(clip.seconds);
    startHandle.max = endHandle.max = clip.seconds;
    editorPosition.max = clip.seconds;
    editorPosition.value = '0';
    if (game) {
      player = {};
      videoSurface.src = clip.thumbnail || 'assets/gleam-replay-video.png';
      clearTimeout(seekTimer);
      seekTimer = undefined;
    } else if (live) {
      editVideo.preload = 'metadata';
      editVideo.src = 'media/' + clip.id + '.mp4?v=' + clip.revision;
    }
    clipList.hidden = true;
    editor.hidden = false;
    screen.dataset.editor = '';
    if (game) post('load-preview', {
      id: clip.id, rate: editVideo.playbackRate, volume: editVideo.volume, muted: editVideo.muted
    });
    screen.setAttribute('aria-labelledby', 'edit-title');
    editFavorite.setAttribute('aria-pressed', String(clip.favorite));
    syncPlayer();
    const current = edit;
    if (!game) loadPreview().then(() => { if (current === edit) seekPreview(Number(editorPosition.value)); })
      .catch(() => { if (current === edit) toast('Демонстрационное видео не загрузилось'); });
    renderTrim();
    byId('replay-body').scrollTop = 0;
    byId('edit-title').focus({preventScroll: true});
    animatePanel(editor, 9);
  }

  function returnToClips(restoreFocus = false) {
    showRemoveConfirm(false);
    if (edit) {
      editVideo.pause();
      if (live) {
        editVideo.removeAttribute('src');
        editVideo.load();
      }
      if (document.fullscreenElement === editFrame) document.exitFullscreen().catch(() => {});
    }
    delete screen.dataset.editor;
    if (game) expandGamePreview(false);
    screen.setAttribute('aria-labelledby', 'replay-title');
    editor.hidden = true;
    clipList.hidden = false;
    edit = null;
    if (game) requestAnimationFrame(() => { layoutClipGrid(); requestThumbnails(); });
    if (restoreFocus) {
      (clipFocus?.isConnected ? clipFocus : clipFilterButton()).focus({preventScroll: true});
      animatePanel(clipList, -7);
    }
    clipFocus = null;
  }

  function decimal(value) { return Math.round(value * 10) / 10; }
  function numberField(field) {
    if (!game) return field.valueAsNumber;
    return field.value.trim() ? Number(field.value.replace(',', '.')) : NaN;
  }

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
    trimSelection.style.left = (edit.start / edit.clip.seconds * 100) + '%';
    trimSelection.style.right = ((1 - edit.end / edit.clip.seconds) * 100) + '%';
    const handles = timeline.children;
    handles[0].style.left = (edit.start / edit.clip.seconds * 100) + '%';
    handles[1].style.left = 'calc(' + (edit.end / edit.clip.seconds * 100) + '% - 3px)';
    clock();
    byId('trim-result').textContent = number.format(decimal(edit.end - edit.start)) + ' с';
    trimSave.disabled = trimApply.disabled = trimReset.disabled = edit.start === 0 && edit.end === edit.clip.seconds;
  }

  function setTrim(edge, value) {
    if (Number.isFinite(value)) {
      if (edge === 'start') edit.start = decimal(Math.max(0, Math.min(edit.end - .1, value)));
      else edit.end = decimal(Math.max(edit.start + .1, Math.min(edit.clip.seconds, value)));
    }
    renderTrim();
  }

  function trimmedRange() {
    return {
      seconds: decimal(edit.end - edit.start),
      sampleStart: (edit.clip.sampleStart ?? 0) + edit.start
    };
  }

  function showRemoveConfirm(open) {
    removeConfirm.hidden = !open;
    removeAction.setAttribute('aria-expanded', String(open));
    if (open) byId('remove-cancel').focus({preventScroll: true});
  }

  function animatePanel(panel, offset) {
    panelAnimations.get(panel)?.cancel();
    if (motion.matches || !isOpen() || typeof panel.animate !== 'function') return;
    const transition = panel.animate([
      {opacity: .3, transform: 'translateX(' + offset + 'px)'},
      {opacity: 1, transform: 'none'}
    ], {duration: 210, easing: 'cubic-bezier(.22,.75,.19,1)'});
    panelAnimations.set(panel, transition);
  }


  function positionOptions(button) {
    const rect = button.getBoundingClientRect();
    const frame = screen.getBoundingClientRect();
    const scaleX = frame.width / screen.offsetWidth;
    const scaleY = frame.height / screen.offsetHeight;
    const localRight = (rect.right - frame.left) / scaleX - screen.clientLeft;
    const localTop = (rect.top - frame.top) / scaleY - screen.clientTop;
    const localBottom = (rect.bottom - frame.top) / scaleY - screen.clientTop;
    optionsMenu.style.width = 'max-content';
    const menuWidth = Math.min(screen.clientWidth - 16, Math.max(rect.width / scaleX, optionsMenu.offsetWidth, 155));
    const above = localTop - 14;
    const below = screen.clientHeight - localBottom - 14;
    const ideal = Math.min(196, 10 + optionsMenu.childElementCount * 32);
    const upward = below < ideal && above > below;
    const room = Math.max(26, Math.min(ideal, upward ? above : below));
    optionsMenu.style.width = menuWidth + 'px';
    optionsMenu.style.left = Math.max(8, Math.min(localRight - menuWidth, screen.clientWidth - menuWidth - 8)) + 'px';
    optionsMenu.style.maxHeight = room + 'px';
    optionsMenu.style.top = upward ? 'auto' : localBottom + 5 + 'px';
    optionsMenu.style.bottom = upward ? screen.clientHeight - localTop + 5 + 'px' : 'auto';
    optionsMenu.style.transformOrigin = upward ? 'bottom right' : 'top right';
    return upward ? 5 : -5;
  }

  function highlightOption(row, scroll = false) {
    if (!row || row.getAttribute('aria-disabled') === 'true') return;
    highlightedOption?.removeAttribute('data-highlighted');
    highlightedOption = row;
    row.dataset.highlighted = '';
    activePicker.button.setAttribute('aria-activedescendant', row.id);
    if (scroll) {
      if (row.offsetTop < optionsMenu.scrollTop) optionsMenu.scrollTop = row.offsetTop;
      const bottom = row.offsetTop + row.offsetHeight;
      if (bottom > optionsMenu.scrollTop + optionsMenu.clientHeight) optionsMenu.scrollTop = bottom - optionsMenu.clientHeight;
    }
  }

  function closeOptions(restoreFocus = false, animate = true) {
    if (!activePicker) return;
    const {button} = activePicker;
    activePicker = null;
    highlightedOption = null;
    button.setAttribute('aria-expanded', 'false');
    button.removeAttribute('aria-activedescendant');
    if (restoreFocus) button.focus({preventScroll: true});
    optionsMenu.setAttribute('aria-hidden', 'true');
    optionsMenu.dataset.closing = '';
    const style = getComputedStyle(optionsMenu);
    const from = {opacity: style.opacity, transform: style.transform};
    optionsMotion?.cancel();
    if (!animate || motion.matches || typeof optionsMenu.animate !== 'function') {
      optionsMenu.hidden = true;
      return;
    }
    const transition = optionsMenu.animate([from, {opacity: 0, transform: 'scale(.98)'}], {duration: 110, fill: 'forwards'});
    optionsMotion = transition;
    transition.finished.then(() => {
      if (optionsMotion === transition) optionsMenu.hidden = true;
    }).catch(() => {});
  }

  function openOptions(picker) {
    if (activePicker === picker) return;
    closeOptions(false, false);
    const {select, button, label} = picker;
    let from;
    if (optionsMenu.hasAttribute('data-closing') && !optionsMenu.hidden && optionsMenu.getAttribute('aria-labelledby') === label.id) {
      const style = getComputedStyle(optionsMenu);
      from = {opacity: style.opacity, transform: style.transform};
    }
    optionsMotion?.cancel();
    activePicker = picker;
    optionTerm = '';
    const rows = [...select.options].map((option, index) => {
      const row = document.createElement('div');
      row.className = 'replay-option';
      row.id = 'replay-option-' + index;
      row.dataset.index = index;
      row.setAttribute('role', 'option');
      row.setAttribute('aria-selected', String(option.selected));
      row.setAttribute('aria-disabled', String(option.disabled));
      const text = document.createElement('span');
      text.textContent = option.textContent;
      row.append(text);
      row.insertAdjacentHTML('beforeend', iconMarkup('i-check'));
      return row;
    });
    optionsMenu.setAttribute('aria-labelledby', label.id);
    optionsMenu.replaceChildren(...rows);
    optionsMenu.hidden = false;
    optionsMenu.removeAttribute('aria-hidden');
    optionsMenu.removeAttribute('data-closing');
    const offset = positionOptions(button);
    optionsMenu.scrollTop = 0;
    button.setAttribute('aria-expanded', 'true');
    highlightOption(rows[select.selectedIndex], true);
    if (motion.matches || typeof optionsMenu.animate !== 'function') return;
    optionsMotion = optionsMenu.animate([
      from ?? {opacity: 0, transform: 'translateY(' + offset + 'px) scale(.98)'},
      {opacity: 1, transform: 'none'}
    ], {duration: 175, easing: 'cubic-bezier(.22,.75,.19,1)'});
  }

  function chooseOption(row) {
    if (!activePicker || !row || row.getAttribute('aria-disabled') === 'true') return;
    const {select} = activePicker;
    const next = Number(row.dataset.index);
    if (select.selectedIndex !== next) {
      select.selectedIndex = next;
      select.dispatchEvent(new Event('input', {bubbles: true}));
      select.dispatchEvent(new Event('change', {bubbles: true}));
    }
    closeOptions(true);
  }

  function settingSelect(select) {
    const label = select.labels?.[0] || settings.querySelector('label[for="' + select.id + '"]');
    const button = document.createElement('button');
    button.type = 'button';
    button.id = select.id + '-picker';
    button.className = 'replay-select';
    button.setAttribute('role', 'combobox');
    button.setAttribute('aria-controls', optionsMenu.id);
    button.setAttribute('aria-expanded', 'false');
    label.id ||= select.id + '-label';
    label.htmlFor = button.id;
    const caption = document.createElement('span');
    caption.id = select.id + '-selected';
    button.setAttribute('aria-labelledby', label.id + ' ' + caption.id);
    button.append(caption, select.parentElement.querySelector('.icon'));
    select.after(button);
    select.hidden = true;
    const picker = {select, button, label};
    const sync = () => { caption.textContent = select.options[select.selectedIndex]?.textContent || ''; };
    select.addEventListener('change', sync);
    sync();
    button.addEventListener('click', () => {
      if (activePicker === picker) closeOptions();
      else openOptions(picker);
    });
    button.addEventListener('keydown', event => {
      if (activePicker === picker && event.key === 'Escape') {
        event.preventDefault();
        closeOptions(true);
        return;
      }
      if (event.key === 'Tab') {
        if (activePicker === picker) closeOptions();
        return;
      }
      const moves = ['ArrowUp', 'ArrowDown', 'Home', 'End'];
      if (['Enter', ' '].includes(event.key) && activePicker === picker) {
        event.preventDefault();
        chooseOption(highlightedOption);
        return;
      }
      const letter = event.key.length === 1 && !event.ctrlKey && !event.altKey && !event.metaKey;
      if (!moves.includes(event.key) && (!letter || event.key === ' ')) return;
      event.preventDefault();
      const wasOpen = activePicker === picker;
      if (!wasOpen) openOptions(picker);
      const rows = [...optionsMenu.children].filter(row => row.getAttribute('aria-disabled') !== 'true');
      const index = rows.indexOf(highlightedOption);
      const last = rows.length - 1;
      if (event.key === 'Home') highlightOption(rows[0], true);
      else if (event.key === 'End') highlightOption(rows[last], true);
      else if (moves.includes(event.key)) {
        if (wasOpen) {
          const step = event.key === 'ArrowUp' ? -1 : 1;
          highlightOption(rows[Math.min(last, Math.max(0, index + step))], true);
        }
      } else {
        const now = performance.now();
        optionTerm = (now - optionTypedAt > 650 ? '' : optionTerm) + event.key.toLocaleLowerCase();
        optionTypedAt = now;
        highlightOption(rows.find(row => row.textContent.toLocaleLowerCase().startsWith(optionTerm)), true);
      }
    });
  }
  function selectPreference(index) {
    if (activePreference === index) return;
    closeOptions(false, false);
    bindingAction = null;
    syncBindings();
    const offset = index > activePreference ? 8 : -8;
    activePreference = index;
    preferenceTabs.forEach((tab, position) => {
      const active = position === index;
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
      preferencePanels[position].hidden = !active;
    });
    byId('replay-body').scrollTop = 0;
    animatePanel(preferencePanels[index], offset);
  }

  function selectTab(index, focus = false) {
    const changed = library.hidden !== (index !== 0);
    closeOptions(false, false);
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
    if (focus) tabs[index].focus({preventScroll: true});
    if (changed) animatePanel(index === 0 ? library : settings, index === 0 ? -9 : 9);
  }

  function isOpen() { return game ? screen.hasAttribute('open') : screen.open; }

  function toast(message) {
    if (isOpen()) {
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
    if (!isOpen()) {
      selectTab(tab);
      if (game) screen.setAttribute('open', ''); else screen.showModal();
    }
    opener.setAttribute('aria-expanded', 'true');
    if (motion.matches) screen.dataset.visible = '';
    else requestAnimationFrame(() => requestAnimationFrame(() => {
      if (isOpen() && opener.getAttribute('aria-expanded') === 'true') screen.dataset.visible = '';
    }));
  }
  function closeReplays() {
    if (!isOpen() || replayOpener.getAttribute('aria-expanded') === 'false') return;
    if (game) {
      editVideo.pause();
      replayOpener.setAttribute('aria-expanded', 'false');
      delete screen.dataset.visible;
      closingTimer = setTimeout(() => post('close'), 180);
      return;
    } else if (live) {
      editVideo.pause();
      replayOpener.setAttribute('aria-expanded', 'false');
      delete screen.dataset.visible;
      if (motion.matches) window.ipc.postMessage('close');
      else setTimeout(() => window.ipc.postMessage('close'), 180);
      return;
    }
    const opener = replayOpener;
    closeOptions(false, false);
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
    else closingTimer = setTimeout(finish, 240);
  }
  function back() {
    if (game && editFrame.hasAttribute('data-expanded')) expandGamePreview(false);
    else if (activePicker) closeOptions(true);
    else if (!removeConfirm.hidden) {
      showRemoveConfirm(false);
      removeAction.focus();
    } else if (edit) returnToClips(true);
    else closeReplays();
  }

  function makeClip(kind, seconds, at = new Date()) {
    return {
      kind, seconds, at, title: clipTypes[kind] + ' ' + ++clipNumbers[kind],
      source: multiplayer ? 'Сетевая игра' : 'Одиночная игра', favorite: false
    };
  }

  function updateRecordClock() {
    const elapsed = time(Math.floor((performance.now() - manualRecording.started) / 1000));
    recordTime.textContent = recordHudTime.textContent = elapsed;
  }

  function syncManualRecording() {
    const active = Boolean(manualRecording);
    recordAction.hidden = view === 'title';
    recordAction.toggleAttribute('data-recording', active);
    setIcon(recordAction, active ? 'i-stop' : 'i-record');
    byId('record-label').textContent = active ? 'Остановить запись' : 'Начать запись';
    recordTime.hidden = !active;
    recordHud.hidden = !active || view !== 'playing';
    if (active) updateRecordClock();
  }

  function stopRecording() {
    const recorded = Math.max(.1, decimal((performance.now() - manualRecording.started) / 1000));
    const clip = makeClip('recording', recorded, manualRecording.at);
    clearInterval(recordingTimer);
    manualRecording = null;
    syncManualRecording();
    clips.unshift(clip);
    renderClips();
    toast('Запись сохранена · ' + time(recorded));
  }

  function toggleRecording() {
    if (live) { post('record'); return; }
    if (manualRecording) {
      stopRecording();
      return;
    }
    if (view === 'title') {
      toast('Открой мир, чтобы начать запись');
      return;
    }
    manualRecording = {started: performance.now(), at: new Date()};
    syncManualRecording();
    recordingTimer = setInterval(updateRecordClock, 1000);
    toast('Запись началась');
  }

  function saveClip() {
    if (live) { post('save'); return; }
    const buffered = Math.floor(seconds());
    if (savePending) return;
    if (buffered < 1) {
      toast(enabled.checked ? 'Буфер пока пуст' : 'Откаты выключены');
      return;
    }
    const clip = makeClip('replay', buffered);
    savePending = true;
    setTimeout(() => {
      savePending = false;
      clips.unshift(clip);
      renderClips();
      toast('Клип сохранён · ' + buffered + ' с');
    }, 330);
  }

  for (const opener of [pauseOpener, titleOpener]) opener.addEventListener('click', () => openReplays(opener));
  recordAction.addEventListener('click', () => {
    const stopping = live ? liveState?.recording : Boolean(manualRecording);
    toggleRecording();
    if (stopping && activeFilter !== 'all' && activeFilter !== 'recording') selectClipFilter('recording');
  });
  byId('close-replay').addEventListener('click', back);
  byId('editor-back').addEventListener('click', () => returnToClips(true));
  screen.addEventListener('cancel', event => { event.preventDefault(); back(); });
  screen.addEventListener('click', event => {
    if (event.target !== screen) return;
    const bounds = screen.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right ||
        event.clientY < bounds.top || event.clientY > bounds.bottom) closeReplays();
  });
  for (const filter of filters) filter.addEventListener('click', () => selectClipFilter(filter.dataset.filter));
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(index, true));
    tab.addEventListener('keydown', event => {
      if (bindingAction || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      selectTab(event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index, true);
    });
  });
  preferenceTabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectPreference(index));
    tab.addEventListener('keydown', event => {
      if (bindingAction || !['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const direction = ['ArrowUp', 'ArrowLeft'].includes(event.key) ? -1 : 1;
      const last = preferenceTabs.length - 1;
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? last : (index + direction + last + 1) % (last + 1);
      selectPreference(next);
      preferenceTabs[next].focus({preventScroll: true});
    });
  });
  for (const button of [previewPlay, editPlay]) button.addEventListener('click', togglePreview);
  (game ? videoSurface : editVideo).addEventListener('click', togglePreview);
  for (const event of ['play', 'pause']) editVideo.addEventListener(event, syncPlayer);
  editVideo.addEventListener('ended', () => {
    if (live) { syncVideoClock(); syncPlayer(); return; }
    syncVideoClock();
    if (edit && Number(editorPosition.value) < edit.clip.seconds) {
      seekPreview((edit.sampleCycle + 1) * editVideo.duration - (edit.clip.sampleStart ?? 0));
      togglePreview();
    } else syncPlayer();
  });
  editVideo.addEventListener('loadedmetadata', () => seekPreview(Number(editorPosition.value)));
  editVideo.addEventListener('timeupdate', syncVideoClock);
  for (const button of editor.querySelectorAll('[data-seek]')) {
    button.addEventListener('click', () => seekPreview(Number(editorPosition.value) + Number(button.dataset.seek)));
  }
  const speedButtons = [...editor.querySelectorAll('[data-speed]')];
  for (const button of speedButtons) button.addEventListener('click', () => {
    editVideo.playbackRate = Number(button.dataset.speed);
    speedButtons.forEach(item => item.setAttribute('aria-pressed', String(item === button)));
  });
  const volume = byId('edit-volume');
  const volumeControl = byId('volume-slider');
  const volumeRail = volumeControl.firstElementChild;
  const mute = byId('edit-mute');
  const syncVolume = () => {
    const position = (editVideo.muted ? 0 : editVideo.volume) * 100 + '%';
    volumeRail.children[0].style.width = position;
    volumeRail.children[1].style.left = position;
    mute.setAttribute('aria-pressed', String(editVideo.muted));
    mute.setAttribute('aria-label', editVideo.muted ? 'Включить звук' : 'Выключить звук');
  };
  volume.addEventListener('input', () => {
    editVideo.volume = Number(volume.value);
    editVideo.muted = editVideo.volume === 0;
    syncVolume();
  });
  syncVolume();
  trackPointer(volumeControl, (event, rect) => {
    const ratio = Math.max(0, Math.min(1, (event.clientX - rect.left - 7) / (rect.width - 14)));
    const next = Math.round(ratio * 20) / 20;
    if (next === Number(volume.value) && !editVideo.muted) return;
    volume.value = next;
    volume.dispatchEvent(new Event('input'));
  });
  mute.addEventListener('click', () => {
    if (editVideo.muted && editVideo.volume === 0) { editVideo.volume = 1; volume.value = '1'; }
    editVideo.muted = !editVideo.muted;
    syncVolume();
  });
  byId('edit-fullscreen').addEventListener('click', async () => {
    if (game) {
      expandGamePreview(!editFrame.hasAttribute('data-expanded'));
      return;
    }
    try {
      if (document.fullscreenElement === editFrame) await document.exitFullscreen();
      else await editFrame.requestFullscreen();
    } catch { toast('Полноэкранный режим недоступен'); }
  });
  editFavorite.addEventListener('click', () => {
    if (!edit) return;
    if (live) { post('favorite', {id: edit.clip.id, favorite: !edit.clip.favorite}); return; }
    edit.clip.favorite = !edit.clip.favorite;
    editFavorite.setAttribute('aria-pressed', String(edit.clip.favorite));
    renderClips();
  });
  if (game) {
    byId('edit-publish').hidden = false;
    byId('edit-publish').addEventListener('click', () => {
      if (edit) post('publish', {id: edit.clip.id});
    });
    byId('youtube-connect').addEventListener('click', () => post('youtube-connect'));
    byId('youtube-disconnect').addEventListener('click', () => post('youtube-disconnect'));
    byId('youtube-config').addEventListener('click', () => post('youtube-config'));
    const saveYouTubeSettings = () => {
      if (postingSettings) return;
      const template = youtubeTemplate.value.trim() || '{link}';
      if (!/\{(?:link|url|short|id)\}/.test(template)) {
        toast('Добавь в шаблон {link}, {short} или {id}');
        return;
      }
      youtubeTemplate.value = template;
      post('youtube-settings', {youtubeAuto: byId('youtube-auto').checked,
        youtubePrivacy: byId('youtube-privacy').value, youtubeTemplate: template});
    };
    byId('youtube-auto').addEventListener('change', saveYouTubeSettings);
    byId('youtube-privacy').addEventListener('change', saveYouTubeSettings);
    byId('youtube-save-template').addEventListener('click', saveYouTubeSettings);
    youtubeTemplate.addEventListener('keydown', event => {
      if (event.key === 'Enter') {
        event.preventDefault();
        saveYouTubeSettings();
        youtubeTemplate.blur();
      }
    });
  }
  byId('edit-still').addEventListener('click', saveStill);
  byId('edit-copy').addEventListener('click', async () => {
    if (game) { post('copy-title'); return; }
    try {
      await navigator.clipboard.writeText(edit.clip.title);
      if (edit) toast('Имя скопировано');
    } catch { if (edit) toast('Не удалось скопировать имя'); }
  });
  removeAction.addEventListener('click', () => showRemoveConfirm(removeConfirm.hidden));
  byId('remove-cancel').addEventListener('click', back);
  byId('remove-clip').addEventListener('click', () => {
    if (live) {
      const {id} = edit.clip;
      returnToClips(true);
      post('remove', {id});
      return;
    }
    clips.splice(clips.indexOf(edit.clip), 1);
    renderClips();
    returnToClips(true);
    toast('Клип удалён');
  });
  editorPosition.addEventListener('input', () => seekPreview(Number(editorPosition.value)));
  function trackPointer(control, update) {
    let active = false;
    control.addEventListener('pointerdown', event => {
      active = true;
      update(event, control.getBoundingClientRect());
    });
    document.addEventListener('pointermove', event => { if (active) update(event, control.getBoundingClientRect()); });
    document.addEventListener('pointerup', event => {
      if (active) update(event, control.getBoundingClientRect());
      active = false;
    });
    document.addEventListener('pointercancel', () => { active = false; });
  }
  let dragEdge;
  let scrubbing = false;
  let scrubResume = false;
  trackPointer(timeline, (event, rect) => {
    if (!edit) { scrubbing = false; return; }
    const x = event.clientX - rect.left;
    if (event.type === 'pointerdown') {
      dragEdge = Math.abs(x - rect.width * edit.start / edit.clip.seconds) < 6 ? 'start'
        : Math.abs(x - rect.width * edit.end / edit.clip.seconds) < 6 ? 'end' : null;
      scrubbing = !dragEdge;
      if (game && scrubbing) {
        scrubResume = !editVideo.paused;
        if (scrubResume) editVideo.pause();
      }
    }
    const point = Math.max(0, Math.min(1, x / rect.width)) * edit.clip.seconds;
    if (dragEdge) setTrim(dragEdge, point);
    else if (event.type === 'pointerup') {
      scrubbing = false;
      seekPreview(point);
      if (game && scrubResume && point < edit.clip.seconds) editVideo.play();
      scrubResume = false;
    }
    else if (game) seekPreview(point, true);
    else { editorPosition.value = point; clock(); }
  });
  document.addEventListener('pointercancel', () => {
    if (scrubbing && game && scrubResume) editVideo.play();
    scrubbing = scrubResume = false;
  });
  startInput.addEventListener(game ? 'blur' : 'change', () => { if (edit) setTrim('start', numberField(startInput)); });
  endInput.addEventListener(game ? 'blur' : 'change', () => { if (edit) setTrim('end', numberField(endInput)); });
  startHandle.addEventListener('input', () => setTrim('start', startHandle.valueAsNumber));
  endHandle.addEventListener('input', () => setTrim('end', endHandle.valueAsNumber));
  trimReset.addEventListener('click', () => { edit.start = 0; edit.end = edit.clip.seconds; renderTrim(); });
  trimApply.addEventListener('click', () => {
    if (live) { requestTrim(true); return; }
    const clip = edit.clip;
    Object.assign(clip, trimmedRange());
    renderClips();
    openEditor(clip, clipFocus);
    toast('Клип обрезан');
  });
  trimSave.addEventListener('click', () => {
    if (live) { requestTrim(false); return; }
    const original = edit.clip;
    clips.unshift({...original, ...trimmedRange(), title: original.title + ' — обрезка', at: new Date(), favorite: false});
    renderClips();
    toast('Копия сохранена');
  });
  function requestTrim(replace) {
    editVideo.pause();
    post('trim', {id: edit.clip.id, start: Math.round(edit.start * 1000), end: Math.round(edit.end * 1000), replace});
  }
  enabled.addEventListener('change', () => {
    if (live) post('buffer', {enabled: enabled.checked});
    else setEnabled(enabled.checked);
  });
  byId('clip-folder').addEventListener('click', () => post('folder'));
  byId('resume').addEventListener('click', resume);
  byId('show-pause').addEventListener('click', showPause);
  byId('exit').addEventListener('click', showTitle);
  for (const button of titleMenu.querySelectorAll('[data-start]')) button.addEventListener('click', resume);
  for (const button of document.querySelectorAll('[data-preview]')) button.addEventListener('click', () => toast(button.dataset.preview));
  duration.addEventListener('change', () => {
    const custom = duration.value === 'custom';
    customDurationField.hidden = !custom;
    updateDuration();
    if (custom && !postingSettings) queueMicrotask(() => {
      if (!customDurationField.hidden && isOpen() && !settings.hidden) {
        customDuration.focus({preventScroll: true});
        customDuration.select();
      }
    });
  });
  function commitDuration() {
    const value = numberField(customDuration);
    if (Number.isFinite(value)) {
      customSeconds = Math.max(Number(customDuration.getAttribute('min')), Math.min(Number(customDuration.getAttribute('max')), Math.round(value)));
    }
    customDuration.value = String(customSeconds);
    updateDuration();
    publishSettings();
  }
  customDuration.addEventListener(game ? 'blur' : 'change', commitDuration);
  if (!game) customDuration.addEventListener('keydown', event => {
    if (event.key === 'Enter') { event.preventDefault(); customDuration.blur(); }
  });
  const settingEvent = id => game && id === 'bitrate' ? 'blur' : 'change';
  for (const id of videoSettings) byId(id).addEventListener(settingEvent(id), () => {
    const next = rate();
    if (!live && (id !== 'bitrate-mode' || next !== activeBitrate)) setEnabled(enabled.checked);
    activeBitrate = next;
    if (id === 'bitrate') bitrate.value = next;
    syncRecording();
  });
  for (const id of [...videoSettings, 'duration', 'audio', 'mic'])
    byId(id).addEventListener(settingEvent(id), publishSettings);
  for (const id of ['audio','mic']) byId(id).addEventListener('change', syncRecording);
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
    const field = event.target.closest?.('input:not([type=checkbox]), select, textarea, [role=combobox]');
    if (!field) {
      const pressed = getBinding(event);
      const action = bindingIds.find(id => bindings[id] && sameBinding(pressed, bindings[id]));
      if (action) {
        event.preventDefault();
        if (event.repeat) return;
        if (action === 'save') saveClip();
        else if (action === 'save-publish' && game) post('save-publish');
        else if (action === 'record') toggleRecording();
        else if (live) post('buffer', {enabled: !enabled.checked});
        else {
          setEnabled(!enabled.checked);
          toast(enabled.checked ? 'Откаты включены' : 'Откаты выключены');
        }
        return;
      }
    }
    if (edit && removeConfirm.hidden && (!field || field.type === 'range') && !event.repeat &&
        !event.ctrlKey && !event.altKey && !event.shiftKey && !event.metaKey &&
        (event.code === 'KeyI' || event.code === 'KeyO')) {
      event.preventDefault();
      setTrim(event.code === 'KeyI' ? 'start' : 'end', Number(editorPosition.value));
      return;
    }
    if (game && event.key === 'Escape' && !event.defaultPrevented) {
      event.preventDefault();
      back();
      return;
    }
    if (isOpen()) return;
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

  for (const select of settings.querySelectorAll('select')) settingSelect(select);
  optionsMenu.addEventListener('pointerdown', event => { if (event.target.closest('[role=option]')) event.preventDefault(); });
  optionsMenu.addEventListener('pointermove', event => { if (activePicker) highlightOption(event.target.closest('[role=option]')); });
  optionsMenu.addEventListener('click', event => chooseOption(event.target.closest('[role=option]')));
  document.addEventListener('pointerdown', event => {
    if (activePicker && !activePicker.button.contains(event.target) && !optionsMenu.contains(event.target)) closeOptions();
  }, true);
  byId('replay-body').addEventListener('scroll', () => {
    closeOptions(false, false);
    if (game) requestThumbnails();
  });
  window.addEventListener('resize', () => closeOptions(false, false));

  const directReplays = query.get('panel') === 'replays';
  setView(query.get('view') === 'title' ? 'title' : 'pause');
  if (live) {
    if (game) document.body.dataset.game = '';
    else document.body.dataset.client = '';
    editVideo.removeAttribute('poster');
    editVideo.setAttribute('aria-label', 'Видео Minecraft');
    byId('clip-folder').hidden = false;
    recordAction.hidden = true;
    if (game) {
      editVideo.hidden = true;
      videoSurface.hidden = false;
      window.paradox = {editShortcut({command, text}) {
        const field = document.activeElement;
        if (field !== youtubeTemplate && field !== customDuration && field !== bitrate && field !== startInput && field !== endInput) return;
        if (command === 'select') {
          field.setSelectionRange(0, field.value.length);
          return;
        }
        const inserted = field === youtubeTemplate ? text.replace(/[\r\n]/g, '') : text.replace(/[^0-9.,]/g, '');
        if (!inserted) return;
        const start = field.selectionStart ?? field.value.length;
        const end = field.selectionEnd ?? start;
        const value = field.value.slice(0, start) + inserted + field.value.slice(end);
        if (value.length > (field === youtubeTemplate ? 240 : 12)) return;
        field.value = value;
        field.setSelectionRange(start + inserted.length, start + inserted.length);
        if (field === youtubeTemplate) field.dispatchEvent(new Event('input'));
      }, update(snapshot) {
        if (snapshot.player) {
          if (!edit || snapshot.player.id !== edit.clip.id) return;
          player = snapshot.player;
          playing = player.playing;
          if (player.hasFrame && videoSurface.getAttribute('src') !== 'assets/gleam-replay-video.png')
            videoSurface.src = 'assets/gleam-replay-video.png';
          if (!scrubbing && !editVideo.seeking) {
            editorPosition.value = player.position;
            clock();
          }
          syncPlayer();
        } else if (snapshot.thumbnailId) {
          const clip = clips.find(item => item.id === snapshot.thumbnailId && item.revision === snapshot.revision);
          if (!clip) return;
          clip.thumbnail = snapshot.thumbnail;
          if (edit?.clip === clip && !player.hasFrame) videoSurface.src = clip.thumbnail;
          const card = byId('clip-grid').querySelector('[data-clip-id="' + clip.id + '"]');
          if (card) {
            card.querySelector('.clip-placeholder').hidden = true;
            const image = card.querySelector('img');
            image.src = clip.thumbnail;
            image.hidden = false;
          }
        } else if (snapshot.toast) toast(snapshot.toast);
        else applyLive(snapshot);
      }};
    } else refreshLive();
  }
  const state = query.get('state');
  setEnabled(state === 'ready' || state === 'buffering', state === 'buffering' ? 24 : state === 'ready' ? length() : 0);
  syncRecording();
  syncBindings();
  renderClips();
  if (directReplays) {
    const showSettings = query.get('tab') === 'settings';
    openReplays(live || view === 'title' ? titleOpener : pauseOpener, showSettings ? 1 : 0);
    if (!showSettings && query.get('editor') === '1') byId('clip-grid').querySelector('.clip-open')?.click();
  }
})();
