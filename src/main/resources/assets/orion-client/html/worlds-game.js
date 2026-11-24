// The shared preview owns the markup, layout, and interactions. This adapter supplies game data.
game.send = (action, id = '', fields = {}) => {
  window.webkit.messageHandlers.paradox.postMessage(JSON.stringify({action, id, ...fields}));
};

game.accountRow = () => {
  const wrapper = document.createElement('div');
  const template = $('accountRow');
  if (template.content?.firstElementChild) return template.content.firstElementChild.cloneNode(true);
  wrapper.innerHTML = template.innerHTML.trim();
  return wrapper.firstElementChild;
};

game.menuItems = () => {
  const it = current();
  const run = (action, id = it.id) => () => game.send(action, id);
  if (state.tab === 'single') return [
    it.canEdit && ['edit', t('Изменить'), openRename],
    it.canEdit && ['backup', t('Резервная копия'), run('backup')],
    ['folder', t('Открыть папку'), run('folder')],
    it.canRecreate && ['recreate', t('Пересоздать'), run('recreate')],
    it.canDelete && ['trash', t('Удалить мир'), confirmDelete, true]
  ].filter(Boolean);
  if (it.create) return [['link', t('Прямое подключение'), openDirect]];
  const position = SERVERS.indexOf(it);
  return [
    ['edit', t('Изменить'), () => openServerForm(it)],
    ['refresh', t('Обновить'), run('refresh')],
    ['link', t('Прямое подключение'), openDirect],
    position > 0 && ['left', t('Переместить левее'), run('move-left')],
    position < SERVERS.length - 1 && ['right', t('Переместить правее'), run('move-right')],
    ...(game.protocol ? [['link', 'ViaFabricPlus', run('protocol')]] : []),
    null,
    ['trash', t('Удалить сервер'), confirmDelete, true]
  ].filter(item => item !== false);
};

let lastRows, lastHero, lastLabels, lastProfiles, lastAccount;
function updateGame(next) {
  const tab = next.single ? 'single' : 'multi';
  const changedTab = !game.loaded || state.tab !== tab;
  const selected = game.selected === next.selected && !changedTab ? current()?.id || '+' : next.selected;
  const sizeChanged = game.width !== next.width || game.height !== next.height;
  const labelsChanged = lastLabels !== JSON.stringify(next.labels);
  const profilesChanged = lastProfiles !== JSON.stringify(next.profiles);
  const activeChanged = lastAccount !== next.activeAccount;
  Object.assign(game, {
    loaded:true, selected:next.selected, width:next.width, height:next.height,
    protocol:next.hasProtocolButton, labels:next.labels
  });

  if (changedTab) {
    state.tab = tab;
    state.zone = 'tiles';
    document.getElementById('preview-tab-multi').disabled = !next.multiplayer;
    for (const button of document.querySelectorAll('.tab')) button.setAttribute('aria-selected', String(button.dataset.tab === tab));
  }
  if (labelsChanged) {
    lastLabels = JSON.stringify(next.labels);
    for (const button of document.querySelectorAll('.tab')) button.textContent = t(button.dataset.tab === 'single' ? 'Одиночная игра' : 'Сетевая игра');
    $('query').placeholder = t('Поиск по названию');
    $('accountHeading').textContent = t('Аккаунты Minecraft');
    $('offlineProfileLabel').textContent = t('Офлайн-профиль');
    $('emptyChip').textContent = t('Пока пусто');
    for (const [id, label] of [['back','Назад'], ['edit','Изменить'], ['more','Ещё'], ['emptyDirect','Прямое подключение']]) {
      $(id).title = t(label);
    }
    for (const [type, label] of [['microsoft','Добавить аккаунт Microsoft'], ['offline','Добавить офлайн-профиль']]) {
      document.querySelector(`[data-account-type="${type}"]`).setAttribute('aria-label', t(label));
    }
  }
  if (profilesChanged || activeChanged) {
    lastProfiles = JSON.stringify(next.profiles);
    lastAccount = next.activeAccount;
    accountState.profiles = next.profiles;
    accountState.active = next.activeAccount;
    if (profilesChanged) renderAccounts(activeChanged); else updateActiveAccount(true);
  }
  document.documentElement.setAttribute('data-theme', next.theme);
  if (next.accent) {
    document.documentElement.style.setProperty('--accent', next.accent);
    document.documentElement.style.setProperty('--accent-ink', next.accentInk);
  }
  $('clock').textContent = next.clock;

  const list = next.single ? WORLDS : SERVERS;
  list.splice(0, list.length, ...next.entries.filter(entry => entry.kind === (next.single ? 'world' : 'server')));
  LAN.splice(0, LAN.length, ...next.entries.filter(entry => entry.kind === 'lan'));
  FRIENDS.splice(0, FRIENDS.length, ...next.entries.filter(entry => entry.kind === 'invite'));
  state.empty = list.length === 0 && !state.query;
  const options = entries();
  const selectionIndex = selected === '+' ? 0 : options.findIndex(entry => entry.id === selected);
  state.focus[tab] = selectionIndex < 0 ? Math.min(1, options.length - 1) : selectionIndex;
  const signature = JSON.stringify(options.map(it => [it.id, it.iconUrl, it.hardcore, it.upgrade, it.offline, it.pending, it.incompatible]));
  if (signature !== lastRows || changedTab) {
    lastRows = signature;
    buildTiles();
  }
  if (sizeChanged) layoutStage();
  else placeTiles();
  const currentEntry = current();
  const hero = JSON.stringify([tab, state.empty, currentEntry]);
  if (hero !== lastHero || labelsChanged) {
    const animate = !lastHero || JSON.parse(lastHero)[2]?.id !== currentEntry?.id || changedTab;
    lastHero = hero;
    renderHero(animate);
  } else renderShelf();

  if (game.feedback !== next.feedback?.revision) {
    game.feedback = next.feedback?.revision;
    if (next.feedback?.message) toast(next.feedback.message);
  }
  if (game.serverResponse !== next.serverResponse) {
    game.serverResponse = next.serverResponse;
    if (next.status && state.dialog) {
      let error = $('serverFormError');
      if (!error) {
        error = document.createElement('p'); error.id = 'serverFormError';
        error.className = 'account-form-error';
        $('dialogBox').appendChild(error);
      }
      error.textContent = next.status;
    } else if (state.dialog && next.serverResponse) closeDialog();
  }
}

window.paradox = {
  update: updateGame,
  editShortcut(shortcut) {
    if (shortcut.command === 'previous-field') {
      if (state.dialog) focusDialogField(-1);
      return;
    }
    const field = document.activeElement;
    if (!field || field.tagName !== 'INPUT') return;
    if (field === query ? !search.classList.contains('open') : !state.dialog || !$('dialogBox').contains(field)) return;
    if (shortcut.command === 'select') { field.select(); return; }
    if (shortcut.command !== 'paste' && shortcut.command !== 'type') return;
    const start = field.selectionStart ?? field.value.length;
    const end = field.selectionEnd ?? start;
    const max = field.maxLength > 0 ? field.maxLength : 255;
    const value = shortcut.text.replace(/[\r\n]/g, '').slice(0, Math.max(0, max - (field.value.length - end + start)));
    field.value = field.value.slice(0, start) + value + field.value.slice(end);
    field.setSelectionRange?.(start + value.length, start + value.length);
    if (field === query) updateQuery();
  }
};
