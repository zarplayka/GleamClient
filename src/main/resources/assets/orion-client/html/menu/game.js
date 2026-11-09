function sendGame(action,data={}) {
  window.webkit.messageHandlers.paradox.postMessage(JSON.stringify({action,...data}));
}
let glintMotion, glintFrame;
function updateGlintPreview() {
  const swatch = $('glint-swatch');
  if (!swatch) return;
  const settings = byId.get('ENCHANT_GLINT').settings;
  const [first,second,bands,intensity,speed] = settings.slice(1,6).map((setting,i) => {
    const field = $('field-' + (i + 1));
    return field && (i >= 2 || /^#[0-9a-f]{6}$/i.test(field.value)) ? field.value : setting.value;
  });
  if (glintMotion?.swatch !== swatch) {
    glintMotion = {swatch, layers:[...swatch.querySelectorAll('.glint-light > i')], offset:0, timestamp:0};
  }
  const period = 166 / Number(bands), brightness = Number(intensity) / 100;
  const fingerprint = [first,second,bands,intensity].join('|');
  if (glintMotion.fingerprint !== fingerprint) {
    glintMotion.fingerprint = fingerprint;
    const rgba = (hex,alpha) => 'rgba(' + [1,3,5].map(i => parseInt(hex.slice(i,i+2),16)).join(',') + ',' + alpha + ')';
    const stop = (color,where) => color + ' ' + (period * where).toFixed(2) + 'px';
    const stops = [stop('transparent',0),stop(rgba(first,.23 * brightness),.18),stop(rgba(first,.86 * brightness),.40),
      stop(rgba(second,.97 * brightness),.53),stop(rgba(second,.28 * brightness),.70),stop('transparent',1)];
    for (const layer of glintMotion.layers) layer.style.background = 'repeating-linear-gradient(135deg,' + stops.join(',') + ')';
    const chips = swatch.parentElement.querySelectorAll('.glint-preview-colors i');
    chips[0].style.backgroundColor = first;
    chips[1].style.backgroundColor = second;
  }
  glintMotion.period = period * Math.SQRT2;
  glintMotion.speed = Number(speed) / 100;
  if (!glintFrame && glintMotion.speed > 0 && !reducedMotion.matches) glintFrame = requestAnimationFrame(animateGlint);
}
function animateGlint(now) {
  glintFrame = 0;
  const motion = glintMotion;
  if (!motion || !motion.swatch.isConnected) return;
  if (motion.speed === 0 || reducedMotion.matches) {
    motion.timestamp = 0;
    return;
  }
  const elapsed = motion.timestamp ? Math.min(64,now - motion.timestamp) : 0;
  motion.offset = (motion.offset + elapsed * .046 * motion.speed) % motion.period;
  motion.timestamp = now;
  for (const layer of motion.layers) layer.style.transform = 'translateX(' + motion.offset.toFixed(2) + 'px)';
  glintFrame = requestAnimationFrame(animateGlint);
}
function refreshSettingsArtwork() {
  if (colorPicker && !colorPicker.trigger.isConnected) closeColorPicker();
  if (glintFrame) cancelAnimationFrame(glintFrame);
  glintFrame = 0;
  glintMotion = null;
  updateGlintPreview();
  updateWorldPreview();
}
function updateWorldPreview() {
  const art = $('world-preview-art');
  if (!art) return;
  const settings = byId.get('WORLD_CHANGER').settings;
  const get = index => {
    const field = $('field-' + index);
    const fallback = settings[index].value;
    if (field?.getAttribute('role') === 'switch') return field.getAttribute('aria-checked') === 'true';
    if (field?.type === 'range' || field?.value && /^#[0-9a-f]{6}$/i.test(field.value)) return field.value;
    return fallback;
  };
  const [skyEnabled,sky,skyStrength,sunsetEnabled,sunset,,fogEnabled,fog,fogStrength,distance,,cloudsEnabled,cloud,opacity]
    = Array.from({length:14},(_,i)=>get(i));
  const mixColor = (base,tint,amount) => {
    const b = colorChannels(base), t = colorChannels(tint);
    return '#' + b.map((c,i) => Math.round(c + (t[i]-c) * amount).toString(16).padStart(2,'0')).join('');
  };
  const zenith = mixColor('#81AFDF',sky,skyEnabled ? skyStrength / 100 : 0);
  const haze = mixColor('#A5BAD1',fog,fogEnabled ? fogStrength / 100 : 0);
  const horizon = sunsetEnabled ? sunset : mixColor(zenith,haze,.7);
  const distanceTint = fogEnabled && Number(distance) > 0 ? .3 + .5 * (1 - distance / 256) : .2;
  art.style.background = 'linear-gradient(180deg,' + zenith + ' 0%,' + horizon + ' 62%,' + haze + ' 100%)';
  art.querySelector('.world-preview-far').style.color = mixColor('#536673',haze,distanceTint);
  art.querySelector('.world-preview-near').style.color = mixColor('#303E49',haze,distanceTint * .54);
  const clouds = art.querySelector('.world-preview-clouds');
  clouds.style.color = cloudsEnabled ? cloud : '#F5F7FF';
  clouds.style.opacity = String(cloudsEnabled ? opacity / 100 : .75);
}
let crosshairStroke;
let crosshairShift = false;
const crosshairEdits = {undo:[],redo:[],anchor:null};
function rememberCrosshair(pixels,anchor) {
  crosshairEdits.undo.push({pixels,anchor});
  if (crosshairEdits.undo.length > 64) crosshairEdits.undo.shift();
  crosshairEdits.redo.length = 0;
}
function restoreCrosshair(command) {
  const editor = document.querySelector('[data-crosshair][data-mode="0"]');
  if (!editor) return;
  finishCrosshairStroke();
  const source = crosshairEdits[command], destination = crosshairEdits[command === 'undo' ? 'redo' : 'undo'];
  if (!source.length) return;
  destination.push({pixels:editor.dataset.pixels,anchor:crosshairEdits.anchor});
  const snapshot = source.pop();
  crosshairEdits.anchor = snapshot.anchor;
  syncCrosshair(editor,snapshot.pixels,false);
  sendCrosshair(editor,snapshot.pixels);
}
function sendCrosshair(editor,value) {
  sendGame('setting',{id:'CROSSHAIR',index:Number(editor.dataset.crosshair),value});
}
function syncCrosshair(editor,pixels,remember=true) {
  if (editor.dataset.pixels !== pixels) {
    if (remember) {
      rememberCrosshair(editor.dataset.pixels,crosshairEdits.anchor);
      crosshairEdits.anchor = null;
    }
    editor.dataset.pixels = pixels;
    const cells = editor.querySelector('.crosshair-grid').children;
    for (let i = 0; i < pixels.length; i++) cells[i].classList.toggle('painted',pixels[i] === '1');
  }
  const [row] = byId.get('CROSSHAIR').settings;
  const active = editor.dataset.mode === '1' ? row.presets[row.selected].pixels : pixels;
  editor.querySelector('.crosshair-preview path').setAttribute('d',crosshairPath(active));
}
function paintCrosshair(event) {
  const stroke = crosshairStroke;
  const size = crosshairGridSize;
  const bounds = stroke.grid.getBoundingClientRect();
  const x = Math.floor((event.clientX - bounds.left) * size / bounds.width);
  const y = Math.floor((event.clientY - bounds.top) * size / bounds.height);
  if (x < 0 || y < 0 || x >= size || y >= size) {
    stroke.previous = null;
    return;
  }
  const [fromX,fromY] = stroke.previous ?? [x,y];
  if (stroke.previous && fromX === x && fromY === y) return;
  const dx = x - fromX, dy = y - fromY, steps = Math.max(1,Math.abs(dx),Math.abs(dy));
  const pixels = [...stroke.editor.dataset.pixels];
  for (let i = 0; i <= steps; i++) {
    pixels[Math.round(fromY + dy * i / steps) * size + Math.round(fromX + dx * i / steps)] = stroke.ink;
  }
  stroke.previous = [x,y];
  stroke.last = [x,y];
  syncCrosshair(stroke.editor,pixels.join(''),false);
}
document.addEventListener('pointerdown',event => {
  const grid = event.target.closest('.crosshair-grid');
  if (!grid || crosshairStroke || (event.button !== 0 && event.button !== 2)) return;
  event.preventDefault();
  grid.focus({preventScroll:true});
  const editor = grid.closest('[data-crosshair]');
  crosshairStroke = {grid,editor,button:event.button,ink:event.button === 0 ? '1' : '0',initial:editor.dataset.pixels,
    anchor:crosshairEdits.anchor,previous:(event.shiftKey || crosshairShift) ? crosshairEdits.anchor : null};
  paintCrosshair(event);
});
document.addEventListener('pointermove',event => {
  if (crosshairStroke) paintCrosshair(event);
});
function finishCrosshairStroke() {
  if (!crosshairStroke) return;
  const {editor,initial,anchor,last} = crosshairStroke;
  crosshairStroke = null;
  if (editor.dataset.pixels !== initial) {
    rememberCrosshair(initial,anchor);
    sendCrosshair(editor,editor.dataset.pixels);
  }
  if (last) crosshairEdits.anchor = last;
}
document.addEventListener('pointerup',event => {
  if (crosshairStroke?.button === event.button) {
    paintCrosshair(event);
    finishCrosshairStroke();
  }
});
document.addEventListener('pointercancel',finishCrosshairStroke);
document.addEventListener('contextmenu',event => {
  if (event.target.closest('.crosshair-grid')) event.preventDefault();
});
document.addEventListener('keydown',event => {
  if (!(event.ctrlKey || event.metaKey) || event.altKey || editableTextField(event.target)) return;
  if (!document.querySelector('[data-crosshair][data-mode="0"]')) return;
  const key = event.key.toLowerCase();
  if (key !== 'z' && key !== 'y') return;
  event.preventDefault();
  restoreCrosshair(key === 'y' || event.shiftKey ? 'redo' : 'undo');
},true);
document.documentElement.setAttribute('data-pointer-focus','');
document.addEventListener('pointerdown',() => document.documentElement.setAttribute('data-pointer-focus',''),true);
document.addEventListener('keydown',event => {
  if (event.key === 'Tab') document.documentElement.removeAttribute('data-pointer-focus');
},true);
const editHistory = new WeakMap(), restoringTextField = new WeakSet();
function editableTextField(field) {
  if (!field?.matches('input,textarea') || field.disabled) return false;
  return field.matches('textarea') || ['text','search','url','tel','password'].includes(field.getAttribute('type') ?? 'text');
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
function applyEditShortcut({command,text:clipboard}) {
  const field = document.activeElement;
  if (!editableTextField(field)) {
    if (command === 'undo' || command === 'redo') restoreCrosshair(command);
    return;
  }
  const history = textFieldHistory(field);
  let start = field.selectionStart ?? 0;
  const end = field.selectionEnd ?? start;
  if (command === 'select') {
    if (typeof field.select === 'function') field.select();
    else field.setSelectionRange(0,field.value.length);
    return;
  }
  if (command === 'copy' || command === 'cut') {
    if (field.type === 'password' || start === end || command === 'cut' && field.readOnly) return;
    sendGame('clipboard-copy',{text:field.value.slice(start,end)});
    if (command === 'copy') return;
  }
  if (field.readOnly) return;
  if (command === 'undo' || command === 'redo') {
    const source = history[command], destination = history[command === 'undo' ? 'redo' : 'undo'];
    if (!source.length) return;
    destination.push(textFieldSnapshot(field));
    restoreTextField(field,history,source.pop());
  } else if (command === 'paste' || command === 'cut' || command === 'delete-word') {
    if (command === 'delete-word' && start === end) {
      if (!start) return;
      const before = field.value.slice(0,start).trimEnd();
      const word = before.match(/[\p{L}\p{M}\p{N}_]+$/u) ?? before.match(/[^\p{L}\p{M}\p{N}_\s]+$/u);
      start = before.length - (word?.[0].length ?? 0);
    }
    let inserted = command === 'paste' ? (clipboard ?? '').replace(/[\u0000-\u001f\u007f]/g,'') : '';
    if (field.maxLength >= 0) {
      const limit = Math.max(0,field.maxLength - field.value.length + end - start);
      inserted = inserted.slice(0,limit).replace(/[\uD800-\uDBFF]$/,'');
    }
    if (command === 'paste' && !inserted) return;
    history.current = textFieldSnapshot(field);
    field.value = field.value.slice(0,start) + inserted + field.value.slice(end);
    field.setSelectionRange(start + inserted.length,start + inserted.length);
    field.dispatchEvent(new Event('input',{bubbles:true}));
  }
  if (field.dataset?.setting !== undefined || field.id === 'custom-accent' || field.id === 'color-popup-hex')
    field.dispatchEvent(new Event('change',{bubbles:true}));
}
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
let lastChatRoute = 0;
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
  'chat-retry'() { sendGame('chat-retry'); },
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
  } else if (d.settingToggle !== undefined || d.chatPreference !== undefined) {
    const checked = button.getAttribute('aria-checked') !== 'true';
    button.setAttribute('aria-checked',String(checked));
    if (d.settingToggle !== undefined) {
      const index = Number(d.settingToggle);
      state.values.set(state.module + ':' + index,checked);
      sendGame('setting',{id:state.module,index,value:checked});
      if (state.module === 'WORLD_CHANGER') updateWorldPreview();
    } else {
      state[d.chatPreference] = checked;
      sendGame('look',{key:d.chatPreference,value:checked});
    }
  } else if (d.colorToggle !== undefined) {
    if (colorPicker?.trigger === button) closeColorPicker();
    else openColorPicker(button);
  } else if (d.colorValue !== undefined && colorPicker) {
    setPickerValue(d.colorValue,true);
  } else if (d.crosshairOperation) {
    const editor = button.closest('[data-crosshair]');
    const operation = d.crosshairOperation, index = Number(d.crosshairIndex);
    if (operation === 'clear') {
      const pixels = '0'.repeat(crosshairGridSize * crosshairGridSize);
      syncCrosshair(editor,pixels);
      sendCrosshair(editor,pixels);
    } else if (operation === 'mode') {
      sendGame('setting',{id:'CROSSHAIR',index:1,value:index});
    } else if (operation === 'save-mode') {
      const saving = editor.toggleAttribute('data-saving');
      button.setAttribute('aria-pressed',String(saving));
    } else if (operation === 'slot') {
      const save = editor.hasAttribute('data-saving') || d.filled === 'false';
      editor.removeAttribute('data-saving');
      editor.querySelector('[data-crosshair-operation="save-mode"]').setAttribute('aria-pressed','false');
      sendCrosshair(editor,{operation:save ? 'save' : 'load',index});
    } else sendCrosshair(editor,{operation,index});
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
  if (field.id === 'color-popup-hex') {
    if (hexColor.test(field.value)) setPickerValue(field.value,false);
    return;
  }
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
  const colorRow = field.closest('.setting-color-row');
  if (colorRow && colorDrag?.row !== colorRow) syncColorPicker(colorRow);
  if (field.id === 'custom-accent' && hexColor.test(field.value)) {
    state.accent = field.value;
    applyAccent();
  }
  if (state.page === 'module' && state.module === 'CROSSHAIR' && field.dataset?.setting !== undefined) {
    const editor = document.querySelector('[data-crosshair]');
    if (editor && field.id === 'field-2' && /^#[0-9a-f]{6}$/i.test(field.value)) editor.style.setProperty('--crosshair-ink',field.value);
    if (editor && field.id === 'field-3') editor.style.setProperty('--crosshair-size',String(Number(field.value) / 100));
    if (editor && field.id === 'field-4') editor.style.setProperty('--crosshair-turn',field.value + 'deg');
  }
  if (state.page === 'module' && state.module === 'ENCHANT_GLINT' && field.dataset?.setting !== undefined) updateGlintPreview();
  if (state.page === 'module' && state.module === 'WORLD_CHANGER' && field.dataset?.setting !== undefined) updateWorldPreview();
});
let rangeDrag, colorDrag, colorPicker;
const clampUnit = value => Math.max(0,Math.min(1,value));
const hexColor = /^#[0-9a-f]{6}$/i;
function rangeLimits(field) {
  return [Number(field.getAttribute('min')),Number(field.getAttribute('max')),Number(field.getAttribute('step'))];
}
function setRangeValue(field,raw) {
  const [min,max,step] = rangeLimits(field);
  const decimals = String(step).split('.')[1]?.length ?? 0;
  const number = Math.max(min,Math.min(max,min + Math.round((raw - min) / step) * step));
  const value = String(Number(number.toFixed(decimals)));
  if (Number(field.value) === Number(value)) return;
  field.value = value;
  field.dispatchEvent(new Event('input',{bubbles:true}));
  if (field.id === 'window-scale') {
    state.windowScale = Number(value);
    fitWindow();
  }
}
function moveRange(event) {
  const {field,bounds} = rangeDrag;
  if (!field.isConnected) return;
  const [min,max] = rangeLimits(field);
  setRangeValue(field,min + clampUnit((event.clientX - bounds.left) / bounds.width) * (max - min));
}
function toHsv(hex) {
  const rgb = [1,3,5].map(index => parseInt(hex.slice(index,index + 2),16) / 255);
  const [r,g,b] = rgb, value = Math.max(...rgb), chroma = value - Math.min(...rgb);
  const section = !chroma ? 0 : value === r ? (g - b) / chroma : value === g ? 2 + (b - r) / chroma : 4 + (r - g) / chroma;
  return {h:((section + 6) % 6) / 6,s:value ? chroma / value : 0,v:value};
}
function fromHsv(h,s,v) {
  return '#' + [5,3,1].map(channel => {
    const k = (channel + 6 * h) % 6;
    const value = v - v * s * Math.max(0,Math.min(k,4 - k,1));
    return Math.round(value * 255).toString(16).padStart(2,'0');
  }).join('').toUpperCase();
}
function paintColorPicker(row,{h,s,v},value) {
  row.pickerValue = value.toUpperCase();
  row.style.setProperty('--picker-color',value);
  row.querySelector('.color-code').textContent = row.pickerValue;
  if (colorPicker?.row !== row) return;
  const popup = $('color-popup');
  popup.style.setProperty('--picker-color',value);
  popup.style.setProperty('--picker-hue',fromHsv(h,1,1));
  popup.style.setProperty('--picker-h',h * 100 + '%');
  popup.style.setProperty('--picker-x',s * 100 + '%');
  popup.style.setProperty('--picker-y',(1 - v) * 100 + '%');
  const hex = $('color-popup-hex');
  if (hex.value !== value) hex.value = value;
  for (const preset of popup.querySelectorAll('[data-color-value]')) {
    preset.setAttribute('aria-pressed',String(preset.dataset.colorValue.toUpperCase() === value.toUpperCase()));
  }
}
function syncColorPicker(row) {
  const value = row.querySelector('input').value;
  if (!hexColor.test(value)) return;
  row.pickerHsv = toHsv(value);
  paintColorPicker(row,row.pickerHsv,value);
}
function setPickerValue(value,commit) {
  const field = colorPicker?.field;
  if (!field || !hexColor.test(value)) return;
  if (field.value !== value) {
    field.value = value;
    field.dispatchEvent(new Event('input',{bubbles:true}));
  }
  colorPicker.dirtyHex = !commit;
  if (commit) field.dispatchEvent(new Event('change',{bubbles:true}));
}
function positionColorPicker() {
  if (!colorPicker) return;
  const popup = $('color-popup'), anchor = colorPicker.trigger.getBoundingClientRect();
  const viewport = document.documentElement.getBoundingClientRect();
  const margin = 8, gap = 6;
  const scale = Math.min(state.windowScale / 100, (viewport.width - 2 * margin) / 226,
    (window.innerHeight - 2 * margin) / 250);
  popup.style.setProperty('zoom',String(scale));
  const rect = popup.getBoundingClientRect();
  const left = Math.max(margin,Math.min(viewport.width - rect.width - margin,anchor.right - rect.width));
  const above = anchor.top - rect.height - gap;
  const top = above >= margin ? above : Math.max(margin,Math.min(window.innerHeight - rect.height - margin,anchor.bottom + gap));
  popup.style.left = left / scale + 'px';
  popup.style.top = top / scale + 'px';
}
function openColorPicker(trigger) {
  closeColorPicker();
  const row = trigger.closest('.setting-color-row'), field = row.querySelector('input');
  const palette = [...new Set([...trigger.dataset.colorPalette.split(',').filter(value => hexColor.test(value)), '#000000'])];
  colorPicker = {trigger,row,field};
  const popup = $('color-popup');
  popup.innerHTML = colorPopup(palette,field.value);
  popup.hidden = false;
  trigger.setAttribute('aria-expanded','true');
  syncColorPicker(row);
  positionColorPicker();
}
function closeColorPicker() {
  if (!colorPicker) return;
  finishSettingDrag({type:'pointercancel'});
  if (colorPicker.dirtyHex && colorPicker.field.isConnected) setPickerValue(colorPicker.field.value,true);
  colorPicker.trigger.setAttribute('aria-expanded','false');
  $('color-popup').hidden = true;
  colorPicker = null;
}
function moveColor(event) {
  const {row,field,axis,bounds,hsv} = colorDrag;
  const x = clampUnit((event.clientX - bounds.left) / bounds.width);
  if (axis === 'hue') hsv.h = x;
  else {
    hsv.s = x;
    hsv.v = 1 - clampUnit((event.clientY - bounds.top) / bounds.height);
  }
  const value = fromHsv(hsv.h,hsv.s,hsv.v);
  paintColorPicker(row,hsv,value);
  if (field.value === value) return;
  field.value = value;
  field.dispatchEvent(new Event('input',{bubbles:true}));
}
document.addEventListener('pointerdown',event => {
  if (colorPicker && !event.target.closest('#color-popup,[data-color-toggle]')) closeColorPicker();
  if (event.button !== 0 || rangeDrag || colorDrag) return;
  const track = event.target.closest('.range-control');
  if (track) {
    const field = track.querySelector('input');
    const bounds = track.querySelector('.range-track').getBoundingClientRect();
    if (bounds.width <= 0) return;
    event.preventDefault();
    field.focus({preventScroll:true});
    rangeDrag = {field,bounds};
    moveRange(event);
    return;
  }
  const surface = event.target.closest('[data-color-axis]');
  if (!surface || !colorPicker) return;
  const {row,field} = colorPicker;
  const bounds = surface.getBoundingClientRect();
  if (bounds.width <= 0 || bounds.height <= 0) return;
  event.preventDefault();
  const current = hexColor.test(field.value) ? field.value : '#FFFFFF';
  colorDrag = {row,field,bounds,axis:surface.dataset.colorAxis,hsv:row.pickerHsv ?? toHsv(current)};
  moveColor(event);
});
document.addEventListener('pointermove',event => {
  if (rangeDrag) moveRange(event);
  if (colorDrag) moveColor(event);
});
function finishSettingDrag(event) {
  if (event.type === 'pointerup' && event.button !== 0) return;
  if (rangeDrag) {
    if (event.type === 'pointerup') moveRange(event);
    const {field} = rangeDrag;
    rangeDrag = null;
    if (field.isConnected) field.dispatchEvent(new Event('change',{bubbles:true}));
  }
  if (colorDrag) {
    if (event.type === 'pointerup') moveColor(event);
    const {row,field,hsv} = colorDrag;
    colorDrag = null;
    row.pickerHsv = hsv;
    if (field.isConnected) field.dispatchEvent(new Event('change',{bubbles:true}));
  }
}
document.addEventListener('pointerup',finishSettingDrag);
document.addEventListener('pointercancel',finishSettingDrag);
window.addEventListener('resize',positionColorPicker);
$('content').addEventListener('scroll',positionColorPicker);
document.addEventListener('keydown',event => {
  const field = event.target;
  if (field.type !== 'range') return;
  const [min,max,step] = rangeLimits(field), value = Number(field.value);
  const next = {ArrowLeft:value - step,ArrowDown:value - step,ArrowRight:value + step,ArrowUp:value + step,
    PageDown:value - 10 * step,PageUp:value + 10 * step,Home:min,End:max}[event.key];
  if (next === undefined) return;
  event.preventDefault();
  setRangeValue(field,next);
  field.dispatchEvent(new Event('change',{bubbles:true}));
},true);
document.addEventListener('change',event => {
  const field = event.target;
  if (field.id === 'color-popup-hex') {
    if (hexColor.test(field.value)) setPickerValue(field.value,true);
  } else if (field.dataset?.setting !== undefined) {
    sendGame('setting',{id:state.module,index:Number(field.dataset.setting),
      value:field.value});
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
      const reply = chatReplies.get(chat.peer);
      pendingChatDraft = {peer:chat.peer,draft,reply};
      sendGame('chat-send',reply ? {body,replyTo:String(reply.id)} : {body});
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
  if (colorPicker) {
    const {trigger} = colorPicker;
    closeColorPicker();
    trigger.focus({preventScroll:true});
    event.preventDefault();
    return;
  }
  finishCrosshairStroke();
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
    if (setting.kind === 'crosshair' && !crosshairStroke) syncCrosshair(field,setting.value);
    else if (module.id === 'CROSSHAIR' && index === 1) {
      const editor = document.querySelector('[data-crosshair]');
      editor.dataset.mode = String(setting.value);
      editor.removeAttribute('data-saving');
      editor.querySelector('[data-crosshair-operation="save-mode"]').setAttribute('aria-pressed','false');
      for (const button of field.querySelectorAll('button')) {
        button.setAttribute('aria-pressed',String(Number(button.dataset.crosshairIndex) === setting.value));
      }
      syncCrosshair(editor,row.settings[0].value);
    }
    else if (setting.kind === 'choice') updateChoice(field,setting.value);
    else if (setting.kind === 'toggle') field.setAttribute('aria-checked',String(setting.value));
    else if (setting.kind === 'value') field.textContent = setting.value;
    else if (setting.kind === 'slider') {
      if (rangeDrag?.field !== field && Number(field.value) !== setting.value) {
        field.value = setting.value;
        updateRange(field);
      }
    } else if (setting.kind === 'text' || setting.kind === 'color' && colorDrag?.field !== field) {
      field.value = setting.value;
      if (setting.kind === 'color' && field.value.toUpperCase() !== field.closest('.setting-color-row').pickerValue) {
        syncColorPicker(field.closest('.setting-color-row'));
      }
    }
  }
  if (module.id === 'CROSSHAIR') {
    const editor = document.querySelector('[data-crosshair]');
    if (editor) {
      if (row.settings[2].value !== previous[2].value && colorDrag?.field.id !== 'field-2') editor.style.setProperty('--crosshair-ink',row.settings[2].value);
      if (row.settings[3].value !== previous[3].value && rangeDrag?.field.id !== 'field-3') editor.style.setProperty('--crosshair-size',String(row.settings[3].value / 100));
      if (row.settings[4].value !== previous[4].value && rangeDrag?.field.id !== 'field-4') editor.style.setProperty('--crosshair-turn',row.settings[4].value + 'deg');
    }
  }
  if (module.id === 'ENCHANT_GLINT') updateGlintPreview();
  if (module.id === 'WORLD_CHANGER') updateWorldPreview();
  return false;
}
window.paradox = {
  pointerModifiers({shift}) { crosshairShift = Boolean(shift); },
  refreshSettingsArtwork,
  editShortcut:applyEditShortcut,
  chatContext({x,y}) { openChatContext(x,y); },
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
    const titleAccount = !gameReady && data.openTitleAccount;
    if (!gameReady) {
      gameReady = true;
      applyGameLanguage(data.language);
      state.page = titleAccount && data.account ? 'profile' : data.page || 'all';
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
    if (data.chatRoute > lastChatRoute) {
      lastChatRoute = data.chatRoute;
      state.page = 'chat';
      $('search').value = state.query = '';
      closeChatContext();
      closeDialog();
      accountOpen(false);
      redraw = true;
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
        chatReplies.clear();
        closeChatContext();
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
    const unread = data.chatUnread ?? {};
    if (JSON.stringify(unread) !== JSON.stringify(chatUnread)) {
      chatUnread = unread;
      renderNavigation();
      chatChanged = true;
    }
    if (data.chat && JSON.stringify(chat) !== JSON.stringify(data.chat)) {
      chat = data.chat;
      const latest = chat.items[chat.items.length-1];
      if (chat.peer && latest) chatRecent.set(chat.peer,latest);
      if (chat.sent !== previousChat.sent && pendingChatDraft?.peer === chat.peer) {
        if (chatDrafts.get(chat.peer) === pendingChatDraft.draft) chatDrafts.delete(chat.peer);
        if (chatReplies.get(chat.peer) === pendingChatDraft.reply) chatReplies.delete(chat.peer);
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
    if (titleAccount) {
      if (state.account) sendGame('profile');
      else if (accounts.some(account => account.key)) {
        accountOpen(true);
        actions.accounts();
      } else accountForm();
    }
  }
};
