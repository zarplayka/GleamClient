(() => {
  Object.assign(launcherTranslations,{
    'Быстрый вход':['Quick Play','Schnellstart','Acceso rápido'],
    'Все друзья':['All friends','Alle Freunde','Todos los amigos'],
    'Все новости':['All news','Alle Neuigkeiten','Todas las noticias'],
    'Выбрать версию Minecraft':['Choose a Minecraft version','Minecraft-Version auswählen','Elegir versión de Minecraft'],
    'Персонаж Minecraft. Поворот мышью или стрелками':[
      'Minecraft character. Drag or use arrow keys to rotate',
      'Minecraft-Figur. Mit Maus oder Pfeiltasten drehen',
      'Personaje de Minecraft. Arrastra o usa las flechas para girar'
    ],
    'Мои сборки':['My profiles','Meine Profile','Mis perfiles'],
    'Поиск версии':['Search versions','Versionen suchen','Buscar versiones'],
    'Ничего не найдено':['No results','Keine Treffer','Sin resultados'],
    'Не удалось загрузить версии':['Could not load versions','Versionen konnten nicht geladen werden','No se pudieron cargar las versiones'],
    'Своих сборок пока нет':['No custom profiles yet','Noch keine eigenen Profile','Aún no hay perfiles propios'],
    'Создай сборку, чтобы хранить её моды и настройки отдельно.':[
      'Create a profile to keep its mods and settings separate.',
      'Erstelle ein Profil, um seine Mods und Einstellungen getrennt zu speichern.',
      'Crea un perfil para mantener sus mods y ajustes separados.'
    ],
    'Попробуй изменить поисковый запрос.':[
      'Try a different search term.','Versuche einen anderen Suchbegriff.','Prueba con otro término de búsqueda.'
    ],
    'Аккаунт Minecraft не выбран':['No Minecraft account selected','Kein Minecraft-Konto ausgewählt','Ninguna cuenta de Minecraft seleccionada'],
    'Не удалось загрузить друзей':['Could not load friends','Freunde konnten nicht geladen werden','No se pudieron cargar los amigos'],
    'Друзья Gleam':['Gleam friends','Gleam-Freunde','Amigos de Gleam'],
    'Войди в Gleam, чтобы увидеть друзей.':['Sign in to Gleam to see your friends.','Melde dich bei Gleam an, um deine Freunde zu sehen.','Inicia sesión en Gleam para ver a tus amigos.'],
    'Установка {0} пока недоступна в Gleam Launcher':['Installing {0} is currently unavailable in Gleam Launcher','Die Installation von {0} ist in Gleam Launcher derzeit nicht verfügbar','Por ahora no se puede instalar {0} en Gleam Launcher'],
    'Пока нет друзей':['No friends yet','Noch keine Freunde','Aún no tienes amigos'],
    'Добавь друга по его нику в Gleam.':['Add a friend by their Gleam username.','Füge einen Freund mit seinem Gleam-Namen hinzu.','Añade a un amigo por su nombre de Gleam.']
  });
  const home = $('page-play');
  const native = Boolean(window.__TAURI__);
  const supportedLoaders = new Set(['vanilla','fabric','quilt']);
  const homeAsset = file => (native ? '' : 'launcher-home-assets/') + file;
  if (native) home.style.setProperty('--home-news-image','url(gameplay-orion.webp)');
  const board = home.querySelector('.home-board');
  const launchColumn = board.querySelector('.launch-column');
  const launchActions = launchColumn.querySelector('.launch-actions');
  const news = board.querySelector('.news');
  const cosmetics = board.querySelector('.cosmetics');
  const layout = $('home-layout').content.cloneNode(true);
  board.replaceChildren();
  for (const image of layout.querySelectorAll('.concept-server-icon')) {
    image.src = homeAsset(image.getAttribute('src').split('/').pop());
  }
  board.append(layout);
  home.classList.add('home-concept');
  for (const id of ['concept-quick-title','concept-friends-title','concept-open-friends','concept-versions-title','concept-picker-account','concept-manage-profiles-label']) {
    const element = $(id);
    writeLocale(element,element.textContent.trim());
  }
  writeLabel($('concept-close-versions'),'Закрыть');
  writeLocale(board.querySelector('[data-concept-kind="profiles"] span'),'Мои сборки');
  writeLabel(board.querySelector('.concept-loader-tabs'),'Загрузчик модов');
  const versionQuery = $('concept-version-query');
  const querySource = versionQuery.placeholder;
  writeLabel(versionQuery,querySource);
  versionQuery.placeholder = translated(querySource);
  localeAttributes.push({element:versionQuery,attribute:'placeholder',source:querySource});

  const stage = board.querySelector('.concept-stage');
  launchColumn.querySelector('h1').remove();
  const profileName = cosmetics.querySelector('#minecraft-name'), loadout = cosmetics.querySelector('.home-loadout');
  const avatar = cosmetics.querySelector('.home-preview');
  avatar.querySelector('.home-zoom').remove();
  loadout.hidden = true;
  home.append(loadout);
  stage.append(avatar,launchColumn);
  writeLabel(skinStage,'Персонаж Minecraft. Поворот мышью или стрелками');

  const nameFont = '600 20px "Pixelify Sans", monospace';
  class HomeAvatar extends GleamCosmeticPreview {
    meshSources = new WeakMap();
    wingSides = new WeakMap();

    setSkin(image,slim,headLayer) {
      this.skinAtlasHeight = image.height;
      try { super.setSkin(image,slim,headLayer); }
      finally { this.skinAtlasHeight = 0; }
    }

    mesh(vertices) {
      if (this.skinAtlasHeight) {
        // Pull face coordinates inside their atlas cells so adjacent pixels cannot bleed at the edges.
        const insetU = .25/64, insetV = .25/this.skinAtlasHeight;
        for (let face = 0; face < vertices.length; face += 60) {
          const centerU = (vertices[face+3]+vertices[face+23])/2;
          const centerV = (vertices[face+4]+vertices[face+24])/2;
          for (let uv = face+3; uv < face+60; uv += 10) {
            vertices[uv] += Math.sign(centerU-vertices[uv])*insetU;
            vertices[uv+1] += Math.sign(centerV-vertices[uv+1])*insetV;
          }
        }
      }
      const mesh = super.mesh(vertices);
      if (!this.skinAtlasHeight) this.meshSources?.set(mesh,vertices);
      return mesh;
    }

    async loadGeometry(wing) {
      await super.loadGeometry(wing);
      const cosmetic = this.cosmetics[wing];
      const mesh = cosmetic?.plane ?? cosmetic;
      if (!mesh || this.wingSides.has(mesh)) return;
      const source = this.meshSources.get(mesh);
      if (!source) return;
      const left = [], right = [];
      for (let face = 0; face < source.length; face += 60) {
        let x = 0;
        for (let vertex = face; vertex < face+60; vertex += 10) x += source[vertex];
        (x < 0 ? left : right).push(...source.slice(face,face+60));
      }
      this.wingSides.set(mesh,[super.mesh(left),super.mesh(right)]);
      this.meshSources.delete(mesh);
    }

    drawMesh(mesh,texture,model,alpha) {
      const sides = this.wingSides.get(mesh);
      if (!sides || !this.cosmeticTime) return super.drawMesh(mesh,texture,model,alpha);
      const unit = model[0];
      const flap = Math.sin(this.cosmeticTime*3.2)*.24;
      sides.forEach((sideMesh,index) => {
        const side = index ? 1 : -1;
        const c = Math.cos(flap), s = Math.sin(flap)*side;
        const x = side*.24, z = .15;
        super.drawMesh(sideMesh,texture,[
          c*unit,0,-s*unit,0, 0,unit,0,0, s*unit,0,c*unit,0,
          x-c*x-s*z,0,z+s*x-c*z,1
        ],alpha);
      });
    }

    setName(name) {
      if (this.nameplate?.name === name) return;
      if (this.nameplate) {
        this.gl.deleteBuffer(this.nameplate.mesh.buffer);
        this.gl.deleteTexture(this.nameplate.texture);
      }
      this.nameplate = null;
      if (!name) return;
      const label = document.createElement('canvas');
      const context = label.getContext('2d');
      context.font = nameFont;
      context.letterSpacing = '.7px';
      const width = Math.ceil(context.measureText(name).width) + 14;
      const height = 24, rasterScale = 8;
      label.width = width * rasterScale;
      label.height = height * rasterScale;
      context.scale(rasterScale,rasterScale);
      context.font = nameFont;
      context.letterSpacing = '.7px';
      const {actualBoundingBoxAscent:ascent,actualBoundingBoxDescent:descent} = context.measureText('Mg');
      context.fillStyle = '#1a3b7688';
      context.fillRect(0,0,width,height);
      context.fillStyle = '#fff';
      context.fillText(name,7,(height+ascent-descent)/2);
      const x = width * .0052, y = height * .0052;
      const vertices = [
        [-x,-y,0,0,0], [x,-y,0,1,0], [x,y,0,1,1],
        [-x,-y,0,0,0], [x,y,0,1,1], [-x,y,0,0,1]
      ].flatMap(corner => [...corner,1,1,1,1,1]);
      // Keep the skin pixelated while sampling the separate high-resolution text texture smoothly.
      const texture = this.texture(label);
      const gl = this.gl;
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
      this.nameplate = {name,halfWidth:x,halfHeight:y,mesh:this.mesh(vertices),texture};
    }

    draw(options) {
      const slots = options.slots ?? {};
      this.nameplateYaw = options.yaw - Math.sin(options.yaw) * .46;
      this.cosmeticTime = settings.motion && !reducedMotion.matches ? options.time : 0;
      this.nameplateY = slots.hat || slots.headwear ? -2.46 : slots.halo ? -2.28 : -2.07;
      super.draw({...options,time:this.cosmeticTime,zoom:.79});
    }

    drawPlayer(dance,time) {
      const label = this.nameplate;
      if (!label) return super.drawPlayer(dance,time);
      const c = Math.cos(this.nameplateYaw), s = Math.sin(this.nameplateYaw);
      const y = this.nameplateY, z = -.08;
      const pose = [c,0,s,0, 0,1,0,0, -s,0,c,0, 0,y,z,1];
      const view = this.view;
      view[12] *= this.canvas.clientWidth < 440 ? .35 : 0;
      const labelTop = Math.abs(view[1]*c + view[9]*s)*label.halfWidth
        + Math.abs(view[5])*label.halfHeight + view[5]*y + view[9]*z + view[13];
      // Keep the entire name visible when zooming.
      view[13] -= Math.max(0,labelTop-.91);
      const head = super.drawPlayer(dance,time);
      this.drawMesh(label.mesh,label.texture,pose,true);
      return head;
    }
  }

  try { homeView = new HomeAvatar(homeCanvas); }
  catch (error) { console.warn('Using the CSS model for the launcher preview',error); }

  const title = stage.querySelector('#play-heading');
  const defaultName = homeDemo ? 'LayOffick' : '';
  const typeface = new FontFace('Pixelify Sans','url('+homeAsset('PixelifySans.ttf')+')',{weight:'400 700'});
  document.fonts.add(typeface);
  const fontReady = typeface.load().catch(() => {});
  async function updateNameplate() {
    const name = profileName.textContent.trim() || defaultName;
    title.textContent = name || 'Minecraft';
    skinModel.dataset.playerName = name;
    await fontReady;
    if ((profileName.textContent.trim() || defaultName) !== name) return;
    homeView?.setName(name);
    queueCosmeticFrame();
  }
  if (homeDemo) {
    const demoLoadout = {halo:true,pet:'moon_moth'};
    const loadoutKey = 'gleam.launcher.home-preview.loadout';
    try { wardrobeSlots = {...demoLoadout,...JSON.parse(localStorage.getItem(loadoutKey))}; }
    catch { wardrobeSlots = {...demoLoadout}; }
    refreshWardrobe = async change => {
      if (!change) return;
      wardrobeSlots[change.slot] = change.value;
      if (change.value && change.slot === 'hat') wardrobeSlots.headwear = '';
      if (change.value && change.slot === 'headwear') wardrobeSlots.hat = false;
      try { localStorage.setItem(loadoutKey,JSON.stringify(wardrobeSlots)); }
      catch {}
      renderWardrobe();
    };
  }
  updateNameplate();
  new MutationObserver(updateNameplate).observe(profileName,{childList:true});
  updateCosmeticPreviews();

  launchColumn.classList.add('concept-launch');
  const copy = document.createElement('span');
  copy.append($('launch-label'),launchColumn.querySelector('.profile-spec'));
  $('launch').append(copy);
  const quickSettings = $('quick-settings');
  quickSettings.hidden = true;
  $('first-launch-note').before(quickSettings);

  const picker = $('concept-versions');
  launcher.append(picker);
  const sheet = picker.querySelector('.concept-version-sheet');
  const workspace = home.closest('.workspace');
  const toolbar = launcher.querySelector('.topbar');
  const versionButton = document.createElement('button');
  versionButton.type = 'button';
  versionButton.className = 'button profile-button';
  versionButton.setAttribute('aria-haspopup','dialog');
  versionButton.setAttribute('aria-controls',picker.id);
  versionButton.setAttribute('aria-expanded','false');
  versionButton.title = '';
  writeLabel(versionButton,'Выбрать версию Minecraft');
  versionButton.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-profiles"/></svg>';
  launchActions.append(versionButton);

  const feedback = document.createElement('p');
  feedback.className = 'concept-launch-status';
  feedback.setAttribute('role','status');
  launchColumn.querySelector('.launch-note-slot').append(feedback);
  const versionLabel = $('profile-version');
  const edition = document.createElement('small');
  edition.hidden = true;
  versionLabel.after(edition);
  const rows = $('concept-version-options');
  const gleamVersions = [...versionTiles].reverse().filter(tile => playableVersions.has(tile.dataset.version));
  const tabs = [...picker.querySelectorAll('[data-concept-kind]')];
  const loaderTabs = [...picker.querySelectorAll('[data-concept-loader]')];
  const loaderLabels = new Map(loaderTabs.map(tab => [tab.dataset.conceptLoader,tab.textContent.trim()]));
  const svgNamespace = 'http://www.w3.org/2000/svg';
  function pickerIcon(kind) {
    if (kind === 'gleam') {
      const icon = launcher.querySelector('.brand img').cloneNode();
      icon.className = 'concept-version-icon';
      icon.alt = '';
      return icon;
    }
    const icon = document.createElementNS(svgNamespace,'svg');
    icon.setAttribute('class','icon concept-version-icon');
    icon.setAttribute('aria-hidden','true');
    const use = document.createElementNS(svgNamespace,'use');
    use.setAttribute('href',kind === 'profiles' ? '#i-profiles' : '#i-picker-'+kind);
    icon.append(use);
    return icon;
  }
  picker.querySelector('[data-concept-kind="gleam"]').prepend(pickerIcon('gleam'));
  for (const tab of loaderTabs) tab.prepend(pickerIcon(tab.dataset.conceptLoader));
  let browseKind = 'classic', browseLoader = 'vanilla', catalog, catalogRequest, catalogFailed = false;
  let pickerMotions = [];

  function fitPicker() {
    const area = workspace.getBoundingClientRect();
    const top = Math.max(0,toolbar.getBoundingClientRect().bottom,area.top);
    const right = Math.max(0,document.documentElement.clientWidth-area.right);
    picker.style.inset = top+'px '+right+'px 0 '+Math.max(0,area.left)+'px';
  }

  function updatePickerProfile() {
    const nickname = $('concept-picker-nick');
    const name = profileName.textContent.trim() || defaultName;
    writeLocale(nickname,name || 'Аккаунт Minecraft не выбран');
    const currentSkin = cosmeticSkin;
    const face = picker.querySelector('canvas').getContext('2d');
    void previewSkin().then(({image,headLayer}) => {
      if (currentSkin !== cosmeticSkin) return;
      face.clearRect(0,0,8,8);
      face.drawImage(image,8,8,8,8,0,0,8,8);
      if (headLayer) face.drawImage(image,40,8,8,8,0,0,8,8);
    }).catch(() => {});
  }

  new MutationObserver(() => { if (picker.open) updatePickerProfile(); }).observe(profileName,{childList:true});
  function togglePicker(open,restoreFocus = false) {
    if (!open && (!picker.open || versionButton.getAttribute('aria-expanded') === 'false')) return;
    if (open) fitPicker();
    const opacity = picker.open ? getComputedStyle(picker).opacity : 0;
    const position = picker.open ? getComputedStyle(sheet).transform : 'translateX(-22px)';
    for (const motion of pickerMotions) motion.cancel();
    pickerMotions = [];
    if (open && !picker.open) {
      updatePickerProfile();
      picker.show();
    }
    versionButton.setAttribute('aria-expanded',String(open));
    if (open) versionQuery.focus({preventScroll:true});
    function finish() {
      if (!open) {
        picker.close();
        if (restoreFocus) versionButton.focus({preventScroll:true});
      }
    }
    if (!settings.motion || reducedMotion.matches) return finish();
    const timing = {duration:open ? 265 : 170,easing:'cubic-bezier(.22,1,.36,1)',fill:'both'};
    const motions = [
      picker.animate([{opacity},{opacity:open ? 1 : 0}],timing),
      sheet.animate([{transform:position},{transform:open ? 'none' : 'translateX(-17px)'}],timing)
    ];
    pickerMotions = motions;
    Promise.all(motions.map(motion => motion.finished)).then(() => {
      if (pickerMotions !== motions) return;
      finish();
      for (const motion of motions) motion.cancel();
      pickerMotions = [];
    }).catch(() => {});
  }

  function pickerNotice(source,...args) {
    const note = document.createElement('p');
    note.className = 'concept-version-note';
    note.setAttribute('role','status');
    writeLocale(note,source,...args);
    rows.append(note);
  }

  function pickerEmpty(icon,title,description,retry) {
    const state = $('concept-picker-empty').content.firstElementChild.cloneNode(true);
    state.querySelector('use').setAttribute('href','#i-'+icon);
    writeLocale(state.querySelector('strong'),title);
    writeLocale(state.querySelector('p'),description);
    if (retry) {
      const button = state.querySelector('button');
      button.hidden = false;
      writeLocale(button,'Повторить');
      button.addEventListener('click',retry);
    }
    rows.append(state);
  }

  function versionRow(value,kind,icon,selected,action) {
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'concept-version-row';
    row.setAttribute('aria-pressed',String(selected));
    const title = document.createElement('strong');
    title.textContent = value;
    const label = document.createElement('small');
    label.textContent = kind;
    row.append(pickerIcon(icon),title,label);
    row.disabled = !action;
    if (action) row.addEventListener('click',action);
    return row;
  }

  function loadVersionCatalog() {
    if (catalog || catalogRequest) return;
    catalogFailed = false;
    catalogRequest = fetch(homeAsset('minecraft-releases.json')).then(response => {
      if (!response.ok) throw Error(response.status);
      return response.json();
    }).then(data => {
      if (!Array.isArray(data.versions) || !data.versions.length) throw Error('Empty Minecraft version catalog');
      catalog = {vanilla:data.versions,...data.loaders};
    })
      .catch(() => { catalogFailed = true; }).finally(() => {
        catalogRequest = undefined;
        if (browseKind === 'classic') renderVersionList();
      });
  }

  function chooseVersion(version, tile) {
    classicTarget = tile ? undefined : {version,loader:browseLoader};
    edition.hidden = Boolean(tile);
    edition.textContent = tile ? '' : ' · '+loaderLabels.get(browseLoader);
    if (tile) tile.click();
    else {
      versionLabel.textContent = version;
      renderLaunchAvailability();
    }
    togglePicker(false,true);
  }

  function renderVersionList() {
    const query = versionQuery.value.trim().toLowerCase();
    rows.replaceChildren();
    if (browseKind === 'classic' && !catalog) {
      if (catalogFailed) {
        pickerEmpty('download','Не удалось загрузить версии','Проверь подключение и повтори попытку',() => {
          loadVersionCatalog();
          renderVersionList();
        });
      } else pickerNotice('Загрузка…');
      return;
    }
    if (browseKind === 'profiles') {
      const profiles = importedProfiles.filter(profile => (profile.name+' '+profile.version).toLowerCase().includes(query));
      for (const profile of profiles) rows.append(versionRow(profile.name,profile.version,'profiles',false,() => {
        togglePicker(false);
        navigate('profiles',profile.id);
      }));
      if (!profiles.length && !query) {
        pickerEmpty('profiles','Своих сборок пока нет','Создай сборку, чтобы хранить её моды и настройки отдельно.');
      }
    } else if (browseKind === 'gleam') {
      for (const tile of gleamVersions) {
        const version = tile.dataset.version;
        if (version.includes(query)) rows.append(versionRow(version,'GleamClient','gleam',!classicTarget && selectedVersion === version,
          () => chooseVersion(version,tile)));
      }
    } else {
      const supported = !native || supportedLoaders.has(browseLoader);
      if (!supported) {
        pickerNotice('Установка {0} пока недоступна в Gleam Launcher',loaderLabels.get(browseLoader));
      }
      for (const version of catalog[browseLoader] ?? []) {
        const selected = classicTarget?.version === version && classicTarget.loader === browseLoader;
        if (version.includes(query)) rows.append(versionRow(version,loaderLabels.get(browseLoader),browseLoader,selected,supported ? () => chooseVersion(version) : null));
      }
    }
    if (!rows.childElementCount) pickerEmpty('search','Ничего не найдено','Попробуй изменить поисковый запрос.');
  }

  function browseVersions(kind) {
    browseKind = kind;
    for (const tab of tabs) tab.setAttribute('aria-pressed',String(tab.dataset.conceptKind === kind));
    picker.querySelector('.concept-loader-tabs').hidden = kind !== 'classic';
    versionQuery.value = '';
    if (kind === 'classic') loadVersionCatalog();
    renderVersionList();
  }

  versionButton.addEventListener('click',() => {
    const open = versionButton.getAttribute('aria-expanded') === 'false';
    if (open) browseVersions(classicTarget ? 'classic' : browseKind);
    togglePicker(open,!open);
  });
  for (const tab of tabs) tab.addEventListener('click',() => browseVersions(tab.dataset.conceptKind));
  for (const tab of loaderTabs) tab.addEventListener('click',() => {
    browseLoader = tab.dataset.conceptLoader;
    for (const choice of loaderTabs) choice.setAttribute('aria-pressed',String(choice === tab));
    renderVersionList();
    rows.scrollTop = 0;
  });
  versionQuery.addEventListener('input',renderVersionList);
  $('concept-close-versions').addEventListener('click',() => togglePicker(false,true));
  $('concept-manage-profiles').addEventListener('click',() => {
    togglePicker(false);
    navigate('profiles');
    $('profiles-create').click();
  });
  createProfileForm.addEventListener('submit',() => {
    if (window.__TAURI__ || !createProfileName.value.trim()) return;
    const status = $('create-profile-status');
    writeLocale(status,'Доступно в приложении Gleam Launcher');
    status.hidden = false;
  });
  picker.addEventListener('keydown',event => {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      togglePicker(false,true);
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      const choices = [...rows.querySelectorAll('button:enabled')];
      const index = choices.indexOf(document.activeElement);
      if (index < 0 && document.activeElement !== versionQuery) return;
      event.preventDefault();
      const next = index + (event.key === 'ArrowDown' ? 1 : -1);
      (index < 0 ? choices.at(event.key === 'ArrowDown' ? 0 : -1)
        : next < 0 ? versionQuery : choices[Math.min(next,choices.length-1)])?.focus();
    }
  });
  document.addEventListener('pointerdown',event => {
    if (picker.open && !sheet.contains(event.target) && !versionButton.contains(event.target)) togglePicker(false,event.target === picker);
  });
  new ResizeObserver(() => { if (picker.open) fitPicker(); }).observe(workspace);
  addEventListener('resize',() => { if (picker.open) fitPicker(); });
  new MutationObserver(() => { if (home.hidden) togglePicker(false); }).observe(home,{attributes:true,attributeFilter:['hidden']});

  for (const button of board.querySelectorAll('[data-concept-server]')) {
    button.addEventListener('click',() => void launchGame(null,button,null,button.dataset.conceptServer));
  }
  for (const tile of versionTiles) tile.addEventListener('click',() => {
    edition.hidden = true;
    edition.textContent = '';
  });
  updateHomeLaunchStatus = (title,detail) => {
    if (title === 'Игра закрыта') title = '';
    writeLocale(feedback,title);
    feedback.title = detail;
  };

  news.classList.add('concept-panel','concept-news');
  const cover = document.createElement('button');
  cover.type = 'button';
  cover.className = 'concept-news-art';
  const topStory = news.querySelector('.news-entry');
  topStory.querySelector('strong').id = 'concept-news-title';
  cover.setAttribute('aria-labelledby','concept-news-title');
  cover.addEventListener('click',() => topStory.click());
  news.querySelector('header').after(cover);
  writeLocale(news.querySelector('#news-shortcut'),'Все новости');
  board.querySelector('.concept-rail').append(news);

  const friends = [
    ['SoraFox','В своём мире','#b65d36','#f2b088','#2b5851'],
    ['NoxCraft','В игре','#242941','#bd8b71','#749cc9'],
    ['MiraSky','В лаунчере','#7b496b','#e6a783','#d7b6e4','away'],
    ['Frostbyte','','#243d56','#dcad94','#76a2a3','offline'],
    ['PixelPanda','','#323845','#eed9ce','#8098cb','offline']
  ];
  const people = $('concept-people');
  const openFriends = () => {
    navigate('friends');
    $('friends-heading').focus({preventScroll:true});
  };
  $('concept-open-friends').addEventListener('click',openFriends);
  if (homeDemo) {
    for (const [index,[name,status,hair,skin,eyes,presence]] of friends.entries()) {
      if (index && presence === 'offline' && friends[index-1].at(-1) !== 'offline') {
        const label = document.createElement('li');
        label.className = 'concept-offline';
        writeLocale(label,'Не в сети');
        people.append(label);
      }
      const item = document.createElement('li');
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'concept-person';
      if (presence) row.toggleAttribute('data-'+presence,true);
      const face = document.createElement('span');
      face.className = 'concept-face';
      face.setAttribute('aria-hidden','true');
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 8;
      const pixel = canvas.getContext('2d');
      pixel.fillStyle = hair;
      pixel.fillRect(0,0,8,8);
      pixel.fillStyle = skin;
      pixel.fillRect(1,2+index%2,6,6-index%2);
      pixel.fillStyle = eyes;
      pixel.fillRect(2,4,1,1);
      pixel.fillRect(5,4,1,1);
      pixel.fillStyle = hair;
      pixel.fillRect(index%2 ? 3 : 1,2,index%2 ? 3 : 2,1);
      pixel.fillRect(3,6,2,1);
      face.append(canvas);
      const person = document.createElement('span');
      person.className = 'concept-person-copy';
      const nick = document.createElement('strong');
      nick.textContent = name;
      person.append(nick);
      if (status) {
        const detail = document.createElement('small');
        writeLocale(detail,status);
        person.append(detail);
      }
      row.append(face,person);
      row.addEventListener('click',openFriends);
      item.append(row);
      people.append(item);
    }
    return;
  }
  const empty = $('concept-picker-empty').content.firstElementChild.cloneNode(true);
  empty.querySelector('use').setAttribute('href','#i-players');
  empty.querySelector('button').addEventListener('click',() => void refreshFriends(true));
  people.after(empty);
  let previous = '';
  renderHomeFriends = () => {
    const friends = signedIn ? social.friends.filter(person => person.status === 'friend') : [];
    const presence = person => social.worlds.people?.[person.username]?.status ?? 'offline';
    friends.sort((a,b) => Number(presence(a) === 'offline') - Number(presence(b) === 'offline'));
    const state = JSON.stringify([signedIn,social.owner,social.homeReady,social.homeError,friends.map(person => [person,presence(person)])]);
    if (state === previous) return;
    previous = state;
    people.replaceChildren();
    empty.hidden = Boolean(friends.length);
    if (!friends.length) {
      const [heading,description] = !signedIn ? ['Друзья Gleam','Войди в Gleam, чтобы увидеть друзей.']
        : social.homeError ? ['Не удалось загрузить друзей','Проверь подключение и повтори попытку']
        : social.homeReady ? ['Пока нет друзей','Добавь друга по его нику в Gleam.'] : ['Загрузка…',''];
      writeLocale(empty.querySelector('strong'),heading);
      writeLocale(empty.querySelector('p'),description);
      empty.querySelector('p').hidden = !description;
      empty.querySelector('button').hidden = !social.homeError;
      return;
    }
    for (const [index,person] of friends.entries()) {
      const state = presence(person);
      if (index && state === 'offline' && presence(friends[index-1]) !== 'offline') {
        const label = catalogElement('li','concept-offline');
        writeLocale(label,'Не в сети');
        people.append(label);
      }
      const item = catalogElement('li','');
      const row = catalogElement('button','concept-person');
      row.type = 'button';
      row.toggleAttribute('data-offline',state === 'offline');
      row.toggleAttribute('data-away',state === 'launcher');
      const face = catalogElement('span','concept-face');
      face.append(friendFace(person));
      const copy = catalogElement('span','concept-person-copy');
      copy.append(catalogElement('strong','',person.username));
      const label = {world:'В своём мире',game:'В игре',launcher:'В лаунчере'}[state];
      if (label) {
        const detail = catalogElement('small','');
        writeLocale(detail,label);
        copy.append(detail);
      }
      row.append(face,copy);
      row.addEventListener('click',() => {
        openFriends();
        social.tab = 'all';
        social.selected = person.username;
        $('friends-search').value = '';
        renderFriends();
      });
      item.append(row);
      people.append(item);
    }
  };
  renderHomeFriends();
})();
