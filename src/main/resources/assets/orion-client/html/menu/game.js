function sendGame(action,data={}) {
  window.webkit.messageHandlers.paradox.postMessage(JSON.stringify({action,...data}));
}
document.documentElement.setAttribute('data-pointer-focus','');
document.addEventListener('pointerdown',() => document.documentElement.setAttribute('data-pointer-focus',''),true);
document.addEventListener('keydown',event => {
  if (event.key === 'Tab') document.documentElement.removeAttribute('data-pointer-focus');
},true);
const editHistory = new WeakMap(), restoringTextField = new WeakSet();
function editableTextField(field) {
  if (!(field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) || field.disabled) return false;
  return field instanceof HTMLTextAreaElement || ['text','search','url','tel','email','password'].includes(field.type);
}
function textFieldSnapshot(field) {
  return {value:field.value,start:field.selectionStart ?? 0,end:field.selectionEnd ?? 0};
}
function textFieldHistory(field) {
  let history = editHistory.get(field);
  if (!history) {
    history = {undo:[],redo:[],current:textFieldSnapshot(field)};
    editHistory.set(field,history);
  }
  return history;
}
document.addEventListener('focusin',event => {
  if (editableTextField(event.target)) textFieldHistory(event.target);
},true);
document.addEventListener('input',event => {
  const field = event.target;
  if (!editableTextField(field)) return;
  const history = textFieldHistory(field), next = textFieldSnapshot(field);
  if (restoringTextField.has(field)) { history.current = next; return; }
  if (history.current.value !== next.value) {
    history.undo.push(history.current);
    if (history.undo.length > 80) history.undo.shift();
    history.redo.length = 0;
  }
  history.current = next;
},true);
function restoreTextField(field,history,snapshot) {
  field.value = snapshot.value;
  field.setSelectionRange(snapshot.start,snapshot.end);
  restoringTextField.add(field);
  field.dispatchEvent(new Event('input',{bubbles:true}));
  restoringTextField.delete(field);
  history.current = snapshot;
}
document.addEventListener('keydown',event => {
  const field = event.target;
  if (!editableTextField(field) || !(event.ctrlKey || event.metaKey) || event.altKey) return;
  const key = event.key.toLowerCase(), history = textFieldHistory(field);
  if (key === 'a') {
    event.preventDefault(); event.stopImmediatePropagation();
    field.setSelectionRange(0,field.value.length);
  } else if (key === 'c' || key === 'x') {
    if (field.type === 'password' || key === 'x' && field.readOnly) return;
    const start = field.selectionStart ?? 0, end = field.selectionEnd ?? start;
    if (end <= start) return;
    event.preventDefault(); event.stopImmediatePropagation();
    sendGame('clipboard-copy',{text:field.value.slice(start,end)});
    if (key === 'x') {
      field.setRangeText('',start,end,'start');
      field.dispatchEvent(new Event('input',{bubbles:true}));
    }
  } else if (key === 'z' || key === 'y') {
    event.preventDefault(); event.stopImmediatePropagation();
    const redo = key === 'y' || event.shiftKey, source = redo ? history.redo : history.undo;
    const destination = redo ? history.undo : history.redo;
    if (!source.length) return;
    destination.push(textFieldSnapshot(field));
    restoreTextField(field,history,source.pop());
  }
},true);
function accountForm() {
  showDialog(t('Вход в GleamClient'),'<form id="game-login"><input name="login" placeholder="' + text(t('Логин')) + '" maxlength="24" required>'
    + '<input name="password" type="password" placeholder="' + text(t('Пароль')) + '" maxlength="128" required>'
    + '<button class="button primary" type="submit">' + text(t('Войти')) + '</button></form>');
}
function sendConfigAction(action) {
  const entry = currentConfig();
  if (entry) sendGame(action,{tab:state.configTab,key:entry.key});
}
function showConfigDialog(action,title,button,named) {
  const entry = currentConfig();
  if (!entry) return;
  const field = named ? '<input name="name" required maxlength="48" value="' + text(entry.name)
    + '" aria-label="' + text(t('Название')) + '">' : '<p>' + text(entry.name) + '</p>';
  showDialog(t(title),'<form id="game-config-command" data-action="' + action
    + '" data-tab="' + state.configTab + '" data-key="' + entry.key + '">' + field
    + '<button class="button primary" type="submit">' + text(t(button)) + '</button></form>');
}
let gameStatus = '';
let lastProfile = '';
let gameReady = false;
let pendingChatDraft;
const gameForms = new Set(['game-login','game-publish','game-config-command','add-account','add-config','add-point','add-friend','chat-compose']);
function applyGameLanguage(language) {
  uiLanguage = ['en','de','es'].includes(language) ? language : 'ru';
  document.documentElement.setAttribute('lang',uiLanguage);
  $('search').setAttribute('placeholder',t('Поиск модулей'));
  $('search').setAttribute('aria-label',t('Поиск модулей'));
  $('category-toggle').firstElementChild.textContent = t('Категории');
  $('categories').setAttribute('aria-label',t('Категории модулей'));
  $('primary-nav').setAttribute('aria-label',t('Разделы'));
  document.querySelector('.workspace').setAttribute('aria-label',t('Текущий раздел'));
  const settings = document.querySelector('.account-settings');
  settings.setAttribute('title',t('Настройки клиента'));
  settings.setAttribute('aria-label',t('Настройки клиента'));
  $('close-window').setAttribute('title',t('Закрыть'));
  $('close-window').setAttribute('aria-label',t('Закрыть'));
}
Object.assign(actions,{
  copy() { sendGame('copy'); },
  accounts() { showAccounts(); sendGame('saved-accounts'); },
  login:accountForm,
  'my-configs'() { configTab('published'); selectPage('configs'); sendGame('configs',{tab:'published'}); },
  'apply-preset'() { sendGame('preset',{index:state.preset}); },
  'restore-preset'() { sendGame('restore-preset'); },
  'apply-config'() { sendConfigAction('config-use'); },
  'save-config'() { sendConfigAction('config-use'); },
  'rename-config'() { showConfigDialog('config-rename','Переименовать','Сохранить',true); },
  'overwrite-config'() { sendConfigAction('config-overwrite'); },
  'publish-config-copy'() { showConfigDialog('config-publish-copy','Опубликовать копию','Опубликовать',true); },
  'remove-config'() { showConfigDialog('config-remove','Удалить конфиг?','Удалить',false); },
  'config-prev'() { sendGame('config-page',{tab:state.configTab,direction:-1}); },
  'config-next'() { sendGame('config-page',{tab:state.configTab,direction:1}); },
  'refresh-configs'() { sendGame('configs',{tab:state.configTab,refresh:true}); },
  'friends-login':accountForm,
  'friends-refresh'() { sendGame('friends-refresh'); },
  'chat-friends'() { selectPage('friends'); sendGame('section',{page:'friends'}); },
  'chat-older'() { sendGame('chat-older'); },
  'chat-wave'() { if (chat.peer && !chat.busy) sendGame('chat-send',{body:'👋'}); },
  'friend-profile-back'() {
    state.friendProfile = null;
    sendGame('friend-profile-back');
    render();
  },
  'friend-profile-copy'() { sendGame('friend-profile-copy'); },
  publication() {
    if (!state.publicationReady) return;
    sendGame('publication',{value:!state.publication});
  },
  'friends-add'() {
    const label = text(t('Ник игрока'));
    showDialog(t('Добавить в друзья'),'<form id="add-friend"><input name="name" required maxlength="24"'
      + ' pattern="[A-Za-z0-9_]{3,24}" aria-label="' + label + '" placeholder="' + label + '">'
      + '<button type="submit" class="button primary">' + text(t('Отправить заявку')) + '</button></form>');
  },
  'publish-config'() {
    showDialog(t('Опубликовать конфиг'),'<form id="game-publish"><input name="name" required maxlength="32" placeholder="' + text(t('Название')) + '">'
      + '<button class="button primary" type="submit">' + text(t('Опубликовать')) + '</button></form>');
  },
  'remove-point'() { sendGame('point-remove',{key:state.point}); },
  'new-point'() {
    showDialog(t('Новая метка'),'<form id="add-point"><input name="name" required maxlength="32" placeholder="' + text(t('Название')) + '">'
      + '<button type="submit" class="button primary">' + text(t('Добавить')) + '</button></form><p>' + text(t('Метка появится на текущих координатах игрока.')) + '</p>');
  },
  logout() { sendGame('logout'); }
});
document.addEventListener('click',event => {
  const button = event.target.closest('button');
  if (!button) return;
  const form = button.closest('form');
  if (button.getAttribute('type') === 'submit' && gameForms.has(form?.id)) {
    event.preventDefault();
    event.stopImmediatePropagation();
    submitGameForm(form);
    return;
  }
  const d = button.dataset || {};
  if (d.page && d.page !== 'configs' && d.page !== 'profile') sendGame('section',{page:d.page});
  if (d.account) {
    event.stopImmediatePropagation();
    sendGame('account',{key:Number(d.account)});
  } else if (d.moduleToggle) {
    event.stopImmediatePropagation();
    sendGame('toggle',{id:d.moduleToggle});
  } else if (d.favorite) {
    event.stopImmediatePropagation();
    sendGame('favorite',{id:d.favorite});
  } else if (d.moduleSettings) {
    sendGame('module',{id:d.moduleSettings});
  } else if (d.settingButton !== undefined) {
    sendGame('setting-button',{id:state.module,index:Number(d.settingButton)});
  } else if (d.keybind !== undefined) {
    sendGame('bind',{id:state.module,index:Number(d.keybind)});
  } else if (d.page === 'configs') {
    sendGame('configs',{tab:state.configTab});
  } else if (d.page === 'profile') {
    sendGame('profile');
  } else if (d.friendChange) {
    sendGame('friend-change',{username:d.username,change:d.friendChange});
  } else if (d.friendProfile) {
    sendGame('friend-profile',{username:d.friendProfile});
  } else if (d.chatPeer) {
    sendGame('chat-select',{username:d.chatPeer});
  } else if (d.equipCosmetic || d.cosmetic) {
    const [,module,,,style] = cosmeticById(d.equipCosmetic || d.cosmetic);
    if (style !== undefined && (!state.enabled.has(module) || state.wingsStyle !== style))
      sendGame('cosmetic-wings',{style});
    else sendGame('toggle',{id:module});
  } else if (d.configTab) {
    sendGame('configs',{tab:d.configTab});
  } else if (d.config !== undefined) {
    sendGame('config-select',{tab:state.configTab,key:Number(d.config)});
  } else if (d.look && d.look !== 'theme') {
    sendGame('look',{key:d.look,value:d.value});
  } else if (d.accent !== undefined) {
    sendGame('look',{key:'accent',value:d.accent});
  }
},true);
document.addEventListener('input',event => {
  const field = event.target;
  if (field.id === 'chat-search') {
    state.chatQuery = field.value;
    const contacts = $('chat-contacts');
    contacts.lastMarkup = renderChatContacts(chatContacts(),field.value.toLowerCase());
    contacts.innerHTML = contacts.lastMarkup;
  } else if (field.id === 'cosmetic-search') {
    state.cosmeticQuery = field.value;
    patchCosmeticShelf();
  } else if (field.name === 'body' && field.closest('#chat-compose')) {
    chatDrafts.set(chat.peer,field.value);
  }
  if (field.type === 'range' && field.dataset?.setting !== undefined) {
    sendGame('setting-preview',{id:state.module,index:Number(field.dataset.setting),value:field.value});
  }
});
let scaleDrag;
function moveScaleDrag(x) {
  const {field,left,width} = scaleDrag;
  const min = Number(field.getAttribute('min')), max = Number(field.getAttribute('max'));
  const fraction = Math.max(0,Math.min(1,(x - left) / width));
  field.value = String(Math.round(min + fraction * (max - min)));
  field.setAttribute('aria-valuetext',field.value + '%');
  updateRange(field,'%');
  state.windowScale = Number(field.value);
  fitWindow();
}
document.addEventListener('pointerdown',event => {
  const track = event.target.closest('.range-control');
  const field = track?.querySelector('#window-scale');
  if (!field || event.button !== 0) return;
  const bounds = track.getBoundingClientRect(), inset = 8 * state.windowScale / 100;
  scaleDrag = {field,left:bounds.left + inset,width:Math.max(1,bounds.width - 2 * inset)};
  field.focus({preventScroll:true});
  moveScaleDrag(event.clientX);
});
document.addEventListener('pointermove',event => {
  if (scaleDrag) moveScaleDrag(event.clientX);
});
document.addEventListener('pointerup',event => {
  if (!scaleDrag || event.button !== 0) return;
  moveScaleDrag(event.clientX);
  scaleDrag = null;
  sendGame('look',{key:'windowScale',value:state.windowScale});
});
document.addEventListener('pointercancel',() => {
  if (!scaleDrag) return;
  scaleDrag = null;
  sendGame('look',{key:'windowScale',value:state.windowScale});
});
document.addEventListener('change',event => {
  const field = event.target;
  if (field.dataset?.setting !== undefined) {
    sendGame('setting',{id:state.module,index:Number(field.dataset.setting),
      value:field.type === 'checkbox' ? field.checked : field.value});
  } else if (field.id === 'window-scale') {
    sendGame('look',{key:'windowScale',value:field.value});
  } else if (field.id === 'custom-accent' && /^#[0-9a-f]{6}$/i.test(field.value)) {
    state.accent = field.value;
    applyAccent();
    sendGame('look',{key:'accent',value:field.value});
  }
});
function submitGameForm(form) {
  if (form.id === 'chat-compose') {
    const draft = form.querySelector('input').value;
    const body = draft.trim();
    if (!chat.busy && body && Array.from(body).length <= 500) {
      pendingChatDraft = {peer:chat.peer,draft};
      sendGame('chat-send',{body});
    }
    return;
  }
  if (form.id === 'game-login') {
    sendGame('login',{name:form.querySelector('[name="login"]').value,password:form.querySelector('[name="password"]').value});
    closeDialog();
    return;
  }
  const field = form.querySelector('[name="name"]');
  const name = field?.value.trim();
  if (form.id === 'add-friend') {
    if (!/^[A-Za-z0-9_]{3,24}$/.test(name ?? '')) {
      toast(t('Ник должен содержать от 3 до 24 букв, цифр или подчёркиваний'));
      return;
    }
    closeDialog();
    sendGame('friend-change',{username:name,change:'request'});
    return;
  }
  if (form.id === 'game-config-command') {
    if (field && !name) return;
    closeDialog();
    sendGame(form.dataset.action,{tab:form.dataset.tab,key:Number(form.dataset.key),name});
    return;
  }
  if (!name) return;
  if (form.id === 'add-account') accountForm();
  else {
    closeDialog();
    sendGame(form.id === 'add-point' ? 'point-add' : form.id === 'game-publish' ? 'config-publish' : 'config-add',{name});
  }
}
document.addEventListener('submit',event => {
  if (!gameForms.has(event.target.id)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  submitGameForm(event.target);
},true);
document.addEventListener('keydown',event => {
  if (event.key === 'Enter' && !event.defaultPrevented
      && event.target.matches('input,button[type="submit"]')) {
    const form = event.target.closest('form');
    if (gameForms.has(form?.id)) {
      event.preventDefault();
      if (!event.repeat) submitGameForm(form);
      return;
    }
  }
  if (event.key !== 'Escape' || event.defaultPrevented) return;
  if ($('dialog').hasAttribute('open')) { closeDialog(); event.preventDefault(); }
  else if ($('account-trigger').getAttribute('aria-expanded') === 'true') {
    accountOpen(false); $('account-trigger').focus(); event.preventDefault();
  } else if (state.page === 'friends' && state.friendProfile) { actions['friend-profile-back'](); event.preventDefault(); }
  else if (state.page === 'module') { actions.back(); event.preventDefault(); }
  else if ($('search').value) {
    $('search').value = state.query = ''; render(); event.preventDefault();
  } else sendGame('close');
});
function syncModuleSettings(module) {
  const row = byId.get(module.id);
  if (!row) return false;
  const previous = row.settings;
  row.settings = module.settings;
  if (state.page !== 'module' || state.module !== row.id) return false;
  const schema = settings => JSON.stringify(settings.map(({value,...fields}) => fields));
  if (schema(previous) !== schema(row.settings)) {
    state.values.clear();
    return true;
  }
  for (const [index,setting] of row.settings.entries()) {
    const field = $('field-' + index);
    if (setting.kind === 'keybind' && field) {
      field.textContent = module.binding === index ? t('Нажми клавишу') : setting.value === '—' ? t('Назначить') : setting.value;
      field.removeAttribute('data-listening');
    }
    if (setting.value === previous[index].value) continue;
    state.values.delete(row.id + ':' + index);
    if (!field) continue;
    if (setting.kind === 'choice') updateChoice(field,setting.value);
    else if (setting.kind === 'toggle') field.checked = setting.value;
    else if (setting.kind === 'value') field.textContent = setting.value;
    else if (setting.kind === 'slider') {
      if (Number(field.value) !== setting.value) {
        field.value = setting.value;
        updateRange(field);
      }
    } else if (setting.kind === 'text' || setting.kind === 'color') field.value = setting.value;
  }
  return false;
}
window.paradox = {
  opening(progress) {
    if (reducedMotion.matches) return;
    const eased = 1 - (1 - progress) ** 3, menu = $('window');
    if (progress >= 1) {
      menu.style.removeProperty('opacity');
      menu.style.removeProperty('transform');
    } else {
      menu.style.setProperty('opacity',String(.3 + .7 * eased));
      menu.style.setProperty('transform','translateY(' + (10 * (1 - eased)) + 'px)');
    }
    if (state.page === 'cosmetics') scheduleCosmeticPreview();
  },
  update(data) {
    let redraw = false, look = false, friendsChanged = false, chatChanged = false;
    if (!gameReady) {
      gameReady = true;
      applyGameLanguage(data.language);
      state.page = data.page || 'all';
      for (const item of data.catalog) { catalog.push(item); byId.set(item.id,item); ordered.push(item); }
      for (const profile of data.presets) presets.push(profile);
      Object.assign(state,data.look);
      setCategoriesExpanded(state.categoriesExpanded);
      if (state.page === 'configs') sendGame('configs',{tab:state.configTab});
      look = redraw = true;
    } else if (data.language !== uiLanguage) {
      applyGameLanguage(data.language);
      catalog.splice(0,catalog.length,...data.catalog);
      ordered.splice(0,ordered.length,...data.catalog);
      for (const item of data.catalog) byId.get(item.id).description = item.description;
      presets.splice(0,presets.length,...data.presets);
      redraw = true;
    }
    if (state.wingsStyle !== data.look.wingsStyle && data.look.wingsStyle !== undefined) {
      state.wingsStyle = data.look.wingsStyle;
      redraw = redraw || state.page === 'cosmetics';
    }
    state.languageChoice = data.look.languageChoice ?? 'auto';
    const enabled = data.enabled || [], favorites = data.favorites || [];
    const activationChanged = enabled.length !== state.enabled.size || enabled.some(id => !state.enabled.has(id));
    if (activationChanged) {
      state.enabled = new Set(enabled);
      for (const control of document.querySelectorAll('[data-module-toggle]')) {
        const on = state.enabled.has(control.dataset.moduleToggle);
        control.setAttribute(control.getAttribute('role') === 'switch' ? 'aria-checked' : 'aria-pressed',String(on));
        const label = control.querySelector('.toggle-text');
        if (label) label.textContent = on ? 'ON' : 'OFF';
        const heading = control.querySelector('.module-state');
        if (heading) heading.textContent = on ? t('Включён') : t('Выключен');
      }
      redraw = redraw || state.page === 'cosmetics';
    }
    if (favorites.length !== state.favorites.size || favorites.some(id => !state.favorites.has(id))) {
      state.favorites = new Set(favorites);
      for (const button of document.querySelectorAll('[data-favorite]'))
        button.setAttribute('aria-pressed',String(state.favorites.has(button.dataset.favorite)));
      renderNavigation();
      redraw = redraw || state.page === 'favorites';
    }
    const accountKey = JSON.stringify(data.accounts) + data.account;
    if (accountKey !== lastProfile) {
      lastProfile = accountKey;
      accounts.splice(0,accounts.length,...data.accounts);
      if (state.account !== data.account) {
        state.friendTab = state.friendProfile = null;
        chatDrafts.clear();
        chatRecent.clear();
        pendingChatDraft = null;
        chat = {peer:'',items:[],busy:false,sent:data.chat?.sent ?? 0};
      }
      state.account = data.account;
      renderAccount();
      redraw = redraw || state.page === 'profile';
    }
    state.publication = data.publication;
    state.publicationReady = data.publicationReady ?? true;
    const publication = $('content').querySelector('[data-action="publication"]');
    if (publication) {
      publication.setAttribute('aria-checked',String(state.publication));
      publication.disabled = !state.publicationReady;
    }
    if (data.module && syncModuleSettings(data.module)) redraw = true;
    const nextFriends = data.friends ?? {signedIn:state.account !== 0,busy:true,status:'',items:[]};
    if (JSON.stringify(friends) !== JSON.stringify(nextFriends)) {
      if (state.page === 'friends' && nextFriends.status && friends.status !== nextFriends.status)
        toast(nextFriends.status,nextFriends.status === t('Заявка отправлена') ? 'success' : 'error');
      const authChanged = friends.signedIn !== nextFriends.signedIn;
      friends = nextFriends;
      friendsChanged = true;
      redraw = redraw || state.page === 'friends' || state.page === 'chat' && authChanged;
    }
    const previousChat = chat;
    if (data.chat && JSON.stringify(chat) !== JSON.stringify(data.chat)) {
      chat = data.chat;
      const latest = chat.items[chat.items.length-1];
      if (chat.peer && latest) chatRecent.set(chat.peer,latest);
      if (chat.peer && previousChat.peer === chat.peer && previousChat.items.length && state.page !== 'chat') {
        const previousId = previousChat.items.reduce((id,item) => Math.max(id,Number(item.id) || 0),0);
        const incoming = chat.items.filter(item => !item.own && Number(item.id) > previousId);
        if (incoming.length) {
          const newest = incoming[incoming.length-1];
          toast(t('Новое сообщение от') + ' ' + chat.peer + ': ' + newest.body,'chat');
          sendGame('chat-notification-sound');
        }
      }
      if (chat.sent !== previousChat.sent && pendingChatDraft?.peer === chat.peer) {
        if (chatDrafts.get(chat.peer) === pendingChatDraft.draft) chatDrafts.delete(chat.peer);
        pendingChatDraft = null;
      }
      chatChanged = true;
    }
    if (data.configTab) {
      const tab = data.configTab, status = data.configLoading ? 'loading' : data.configError || 'ready';
      const page = {busy:data.configBusy,offset:data.configOffset ?? 0,hasNext:data.configHasNext,selected:data.configSelected};
      const previous = configPages[tab];
      const switched = state.page === 'configs' && state.configTab !== tab;
      if (configLoadStatus[tab] !== status || JSON.stringify(configs[tab]) !== JSON.stringify(data.configs)
          || JSON.stringify(previous) !== JSON.stringify(page) || switched) {
        configPages[tab] = page;
        configLoadStatus[tab] = status;
        configs[tab] = data.configs;
        if (switched) configTab(tab);
        if (state.configTab === tab && (switched || page.offset !== previous?.offset || page.selected !== previous?.selected
            || !data.configs.some(item => item.key === state.config))) state.config = page.selected ?? data.configs[0]?.key ?? null;
        redraw = redraw || state.page === 'configs' && state.configTab === tab;
      }
    }
    if (JSON.stringify(points) !== JSON.stringify(data.points)) {
      points.splice(0,points.length,...data.points);
      if (!points.some(item => item.key === state.point)) state.point = points[0]?.key ?? null;
      redraw = redraw || state.page === 'waypoints';
    }
    if (state.appliedPreset !== data.appliedPreset || state.canRestorePreset !== data.canRestorePreset) {
      state.appliedPreset = data.appliedPreset;
      state.canRestorePreset = data.canRestorePreset;
      redraw = redraw || state.page === 'presets';
    }
    if (data.status && gameStatus !== data.status) toast(data.status,data.statusKind);
    gameStatus = data.status;
    if (look) applyLook();
    if (redraw) {
      const contentScroll = $('content').scrollTop;
      const scroll = $('content').querySelector('.modules-grid')?.scrollTop ?? 0;
      render(false,!gameReady);
      const grid = $('content').querySelector('.modules-grid');
      if (grid) grid.scrollTop = scroll;
      else if (state.page === 'friends') $('content').scrollTop = contentScroll;
    } else if (state.page === 'chat' && (friendsChanged || chatChanged)) patchChat(previousChat,friendsChanged);
  }
};
