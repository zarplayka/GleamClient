'use strict';

(() => {
  const form = document.querySelector('#auth-form');
  if (!form) return;
  const t = source => window.OrionLocale?.text(source) ?? source;
  const username = document.querySelector('#username');
  const registerEmail = document.querySelector('#register-email');
  const password = document.querySelector('#password');
  const confirmation = document.querySelector('#confirm-password');
  const consent = document.querySelector('#accept-terms');
  const submit = document.querySelector('#auth-submit');
  const message = document.querySelector('#auth-message');
  const loading = document.querySelector('#auth-loading');
  const entry = document.querySelector('#auth-entry');
  const profile = document.querySelector('#auth-profile');
  const authCard = document.querySelector('.auth-card');
  const emailEdit = document.querySelector('#profile-email-edit');
  const plusStatus = document.querySelector('#profile-plus-status');
  const plusRemaining = document.querySelector('#profile-plus-remaining');
  const sessionsList = document.querySelector('#profile-sessions');
  const sessionsStatus = document.querySelector('#profile-sessions-status');
  const revokeOthers = document.querySelector('#revoke-other-sessions');
  const passwordForm = document.querySelector('#profile-password-form');
  const contactsForm = document.querySelector('#profile-contacts-form');
  const socialRows = [...contactsForm.querySelectorAll('[data-provider]')];
  const socialReturn = new URLSearchParams(location.hash.slice(1));
  const returnedProvider = socialReturn.get('social');
  const socialCallback = returnedProvider === 'discord' || returnedProvider === 'github';
  if (socialCallback) history.replaceState(null, '', location.pathname + location.search);
  const avatarFile = document.querySelector('#profile-avatar-file');
  const avatarRemove = document.querySelector('#profile-avatar-remove');
  const publicCard = document.querySelector('#public-profile');
  const publicId = new URLSearchParams(location.search).get('profile');
  const returnProfile = new URLSearchParams(location.search).get('returnProfile');
  const feedbackPanel = document.querySelector('#profile-feedback');
  const reactions = document.querySelector('#profile-reactions');
  const voteButtons = [...reactions.querySelectorAll('[data-profile-vote]')];
  const commentForm = document.querySelector('#profile-comment-form');
  const commentList = document.querySelector('#profile-comments');
  const commentsMore = document.querySelector('#profile-comments-more');
  const feedbackSignin = document.querySelector('#profile-feedback-signin');
  let feedback = null;
  let feedbackBusy = false;
  let feedbackMessage = '';
  const profileTabs = [...document.querySelectorAll('.profile-tabs [role="tab"]')];
  const adminTab = document.querySelector('#profile-tab-admin');
  const friendForm = document.querySelector('#friend-request-form');
  const adminForm = document.querySelector('#admin-search-form');
  const deleteDialog = document.querySelector('#account-delete-dialog');
  const deleteForm = document.querySelector('#account-delete-form');
  let deleteTarget = '';
  const publicFriendAction = document.querySelector('#public-friend-action');
  const publicFriendButton = document.querySelector('#public-friend-request');
  let friendItems = [];
  let adminAccounts = null;
  let friendsBusy = false;
  let adminBusy = false;
  const profileTabList = document.querySelector('.profile-tabs');
  const compactProfile = window.matchMedia('(max-width: 760px)');
  const emailReminder = document.querySelector('#profile-email-reminder');
  const emailReminderTitle = document.querySelector('#profile-email-reminder-title');
  const profileTier = document.querySelector('#profile-tier');
  let profileDetails = null;

  function selectProfileTab(tab) {
    for (const item of profileTabs) {
      const selected = item === tab;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
      document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
    }
    if (tab.id === 'profile-tab-friends') loadFriends();
    if (tab === adminTab) loadAdmin();
  }

  function syncTabOrientation() {
    profileTabList.setAttribute('aria-orientation', compactProfile.matches ? 'horizontal' : 'vertical');
  }
  compactProfile.addEventListener('change', syncTabOrientation);
  syncTabOrientation();

  document.querySelector('#profile-open-security').addEventListener('click', () => {
    const security = document.querySelector('#profile-tab-security');
    selectProfileTab(security);
    if (user?.email) {
      emailVerify.focus();
    } else {
      emailEdit.open = true;
      profileEmail.focus();
    }
  });

  profileTabs.forEach(tab => {
    tab.addEventListener('click', () => selectProfileTab(tab));
    tab.addEventListener('keydown', event => {
      const visibleTabs = profileTabs.filter(item => !item.hidden);
      const count = visibleTabs.length;
      const index = visibleTabs.indexOf(tab);
      const target = {ArrowUp: (index + count - 1) % count, ArrowDown: (index + 1) % count,
        ArrowLeft: (index + count - 1) % count, ArrowRight: (index + 1) % count,
        Home: 0, End: count - 1}[event.key];
      if (target === undefined) return;
      event.preventDefault();
      selectProfileTab(visibleTabs[target]);
      visibleTabs[target].focus();
    });
  });
  const logout = document.querySelector('#auth-logout');
  const emailForm = document.querySelector('#email-form');
  const profileEmail = document.querySelector('#profile-email');
  const emailPassword = document.querySelector('#email-password');
  const emailSubmit = document.querySelector('#email-submit');
  const emailStatus = document.querySelector('#email-status');
  const emailVerify = document.querySelector('#email-verify');
  const emailVerifyStatus = document.querySelector('#email-verify-status');
  const recovery = document.querySelector('#auth-recovery');
  const recoveryForm = document.querySelector('#recovery-form');
  const recoveryEmail = document.querySelector('#recovery-email');
  const recoveryPassword = document.querySelector('#recovery-password');
  const recoveryConfirmation = document.querySelector('#recovery-confirmation');
  const recoverySubmit = document.querySelector('#recovery-submit');
  const recoveryMessage = document.querySelector('#recovery-message');
  const link = /^#(verify|reset)=([A-Za-z0-9_-]{43})$/.exec(location.hash);
  let recoveryMode = link?.[1] || (location.hash === '#forgot' ? 'forgot' : '');
  let recoveryText = '';
  let recoveryDone = false;
  let verificationText = '';
  if (link) history.replaceState(null, '', location.pathname + location.search);
  let mode = 'login';
  let csrf = null;
  let user = null;
  let plusActive = false;
  let plusExpiresAt = null;
  let sessions = [];
  let sessionsBusy = false;
  let sessionsFeedback = '';
  let busy = false;
  let errorSource = '';
  let profileError = '';
  let emailSaved = false;
  const identityChecks = new Map();
  const markers = new Map([...form.querySelectorAll('[data-check]')].map(marker => [marker.dataset.check, marker]));
  const usernamePattern = /^[A-Za-z0-9_]{3,24}$/;
  const passwordError = 'Пароль: от 12 до 128 символов, не более 256 байт';
  const passwordMismatch = 'Пароли не совпадают';

  function validPassword(value, minimum = 12) {
    const length = [...value].length;
    return length >= minimum && length <= 128 && new TextEncoder().encode(value).length <= 256;
  }

  async function request(path, body) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const headers = {'X-Orion-Request': '1', 'Accept-Language': document.documentElement.lang};
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      if (csrf) headers['X-CSRF-Token'] = csrf;
      const response = await fetch(path, {method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin', cache: 'no-store', headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal});
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const error = new Error(data.error || 'unavailable');
        error.code = data.error;
        error.status = response.status;
        throw error;
      }
      return data;
    } finally { clearTimeout(timeout); }
  }

  function explain(error) {
    if (error.code === 'banned') return 'Этот аккаунт заблокирован.';
    if (error.code === 'credentials') return 'Неверное имя пользователя или пароль';
    if (error.code === 'taken') return 'Это имя уже занято — выбери другое';
    if (error.code === 'email') return 'Введи корректный email';
    if (error.code === 'email_taken') return 'Этот email уже используется';
    if (error.code === 'mail_token') return 'Срок действия ссылки истёк или она уже использована';
    if (error.code === 'unavailable') return 'Отправка писем временно недоступна — попробуй позже';
    if (error.code === 'rate' || error.status === 429) return 'Слишком много попыток — попробуй позже';
    if (error.code === 'username') return 'Имя: от 3 до 24 латинских букв, цифр или _';
    if (error.code === 'password') return 'Пароль: от 12 до 128 символов, не более 256 байт';
    if (error.code === 'origin') return 'Не удалось проверить запрос — обнови страницу';
    if (error.code === 'auth') return 'Сессия истекла — войди ещё раз';
    return 'Не удалось связаться с сервером — попробуй ещё раз';
  }

  function renderCheck(input, problem, success) {
    const marker = markers.get(input.id);
    const known = mode === 'register' && problem !== undefined;
    if (known) input.setAttribute('aria-invalid', String(Boolean(problem)));
    else input.removeAttribute('aria-invalid');
    marker.dataset.state = known ? problem ? 'invalid' : 'valid' : '';
    const description = known ? t(problem || success) : '';
    if (marker.textContent !== description) marker.textContent = description;
    marker.title = description;
  }

  function renderChecks() {
    for (const [input, success] of [[username, 'Имя свободно'], [registerEmail, 'Почта свободна']]) {
      const check = identityChecks.get(input);
      renderCheck(input, check?.value === input.value.trim() ? check.problem : undefined, success);
    }
    renderCheck(password, password.value ? validPassword(password.value) ? '' : passwordError : undefined, 'Пароль подходит');
    renderCheck(confirmation, confirmation.value ? confirmation.value === password.value ? '' : passwordMismatch : undefined, 'Пароли совпадают');
  }

  function checkIdentity(input) {
    const value = input.value.trim();
    if (identityChecks.get(input)?.value === value) return;
    clearTimeout(identityChecks.get(input)?.timer);
    const check = {value};
    identityChecks.set(input, check);
    if (!value) {
      renderChecks();
      return;
    }
    const isUsername = input === username;
    const valid = isUsername ? usernamePattern.test(value) : input.validity.valid;
    if (!valid) {
      check.problem = explain({code: isUsername ? 'username' : 'email'});
    } else {
      check.timer = setTimeout(async () => {
        try {
          const data = await request('/api/register/availability', {[input.name]: value});
          check.problem = data.available ? '' : explain({code: isUsername ? 'taken' : 'email_taken'});
        } catch (error) {
          if (error.code === 'username' || error.code === 'email') check.problem = explain(error);
        }
        if (identityChecks.get(input) === check && input.value.trim() === value) renderChecks();
      }, 450);
    }
    renderChecks();
  }

  function render() {
    const registering = mode === 'register';
    const recoveryTitle = {forgot: 'Восстановление пароля', reset: 'Новый пароль', verify: 'Подтверждение почты'};
    document.querySelector('#auth-heading').textContent = t(recoveryTitle[recoveryMode] || (user ? 'Аккаунт' : registering ? 'Присоединяйся.' : 'С возвращением.'));
    recovery.hidden = !recoveryMode;
    entry.hidden = !!recoveryMode || !loading.hidden || !!user;
    profile.hidden = !!recoveryMode || !user;
    document.body.classList.toggle('profile-page', !profile.hidden);
    authCard.hidden = !profile.hidden;
    document.querySelector('#forgot-password').parentElement.hidden = registering;
    recovery.querySelectorAll('[data-recovery]').forEach(field => { field.hidden = field.dataset.recovery !== recoveryMode; });
    recoveryForm.hidden = recoveryDone;
    recoveryEmail.required = recoveryMode === 'forgot';
    recoveryPassword.required = recoveryConfirmation.required = recoveryMode === 'reset';
    recoverySubmit.disabled = busy;
    recoverySubmit.textContent = t(busy ? 'Подожди…' : recoveryMode === 'verify' ? 'Подтвердить почту' : recoveryMode === 'reset' ? 'Сохранить пароль' : 'Отправить ссылку');
    recoveryMessage.textContent = t(recoveryText);
    recoveryMessage.hidden = !recoveryText;
    document.querySelectorAll('[data-auth-mode]').forEach(button => {
      button.setAttribute('aria-pressed', String(button.dataset.authMode === mode));
      button.disabled = busy;
    });
    document.querySelectorAll('[data-register-only]').forEach(element => { element.hidden = !registering; });
    if (registering) {
      username.setAttribute('aria-describedby', 'username-help');
      password.setAttribute('aria-describedby', 'password-help');
    } else {
      username.removeAttribute('aria-describedby');
      password.removeAttribute('aria-describedby');
    }
    confirmation.required = registering;
    registerEmail.required = registering;
    registerEmail.disabled = busy || !registering;
    consent.required = registering;
    password.autocomplete = registering ? 'new-password' : 'current-password';
    submit.textContent = t(busy ? 'Подожди…' : registering ? 'Создать аккаунт' : 'Войти');
    submit.disabled = busy;
    username.disabled = password.disabled = confirmation.disabled = consent.disabled = busy;
    logout.disabled = busy;
    profileEmail.disabled = emailPassword.disabled = emailSubmit.disabled = busy;
    for (const control of [...passwordForm.elements, ...contactsForm.elements, avatarFile, avatarRemove]) {
      control.disabled = busy;
    }
    renderSocialContacts();
    emailStatus.textContent = t('Почта сохранена');
    emailStatus.hidden = !emailSaved;
    document.querySelector('#profile-email-address').textContent = user?.email || t('Почта не указана');
    document.querySelector('#email-verification-state').textContent = t(user?.email ? user.email_verified ? 'Почта подтверждена' : 'Почта не подтверждена' : '');
    emailEdit.querySelector('summary').textContent = t(user?.email ? 'Изменить почту' : 'Добавить почту');
    renderPlusStatus();
    emailReminder.hidden = !user || Boolean(user.email_verified);
    emailReminderTitle.textContent = t(user?.email ? 'Подтвердите почту' : 'Добавьте почту к аккаунту');
    emailVerify.hidden = !user?.email || user.email_verified;
    emailVerify.disabled = busy;
    emailVerifyStatus.textContent = t(verificationText);
    emailVerifyStatus.hidden = !verificationText;
    logout.textContent = t(busy ? 'Подожди…' : 'Выйти из аккаунта');
    message.textContent = t(errorSource);
    message.hidden = !errorSource;
    const profileMessage = document.querySelector('#profile-message');
    profileMessage.textContent = t(profileError);
    profileMessage.hidden = !profileError;
    const visibility = document.querySelector('#password-visibility');
    visibility.setAttribute('aria-label', t(password.type === 'password' ? 'Показать пароль' : 'Скрыть пароль'));
    renderChecks();
  }

  function renderSessions() {
    const date = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'medium', timeStyle: 'short'});
    const items = sessions.map(session => {
      const item = document.createElement('li');
      const info = document.createElement('div');
      const name = document.createElement('strong');
      const time = document.createElement('time');
      const action = document.createElement(session.current ? 'span' : 'button');
      name.textContent = t(session.device || 'Неизвестное устройство');
      const signedIn = new Date(session.signed_in_at * 1000);
      time.dateTime = signedIn.toISOString();
      time.textContent = date.format(signedIn);
      info.append(name, time);
      if (session.current) {
        action.textContent = t('Текущий сеанс');
      } else {
        action.type = 'button';
        action.className = 'hero-action hero-action-secondary';
        action.textContent = t('Завершить');
        action.disabled = sessionsBusy;
        action.addEventListener('click', () => revokeSession(session.id));
      }
      item.append(info, action);
      return item;
    });
    sessionsList.replaceChildren(...items);
    revokeOthers.hidden = !sessions.some(session => !session.current);
    revokeOthers.disabled = sessionsBusy;
    sessionsStatus.textContent = t(sessionsFeedback);
    sessionsStatus.hidden = !sessionsFeedback;
  }

  async function loadSessions() {
    const key = csrf;
    try {
      const data = await request('/api/sessions');
      if (csrf !== key) return;
      sessions = data.sessions;
      sessionsFeedback = '';
    } catch {
      if (csrf !== key) return;
      sessionsFeedback = 'Не удалось загрузить сеансы. Обновите страницу.';
    }
    renderSessions();
  }

  async function revokeSession(id) {
    if (busy || sessionsBusy || !csrf) return;
    const key = csrf;
    sessionsBusy = true;
    sessionsFeedback = '';
    renderSessions();
    try {
      await request('/api/sessions/revoke', {id});
      if (csrf !== key) return;
      sessions = sessions.filter(session => session.current || (id !== 'others' && session.id !== id));
      sessionsFeedback = id === 'others' ? 'Остальные сеансы завершены.' : 'Сеанс завершён.';
    } catch (error) {
      if (csrf !== key) return;
      if (error.status === 401) {
        showSession({user: null});
        errorSource = explain(error);
        render();
      } else {
        sessionsFeedback = 'Не удалось завершить сеанс. Попробуйте ещё раз.';
      }
    } finally {
      if (csrf === key) {
        sessionsBusy = false;
        renderSessions();
        sessionsStatus.tabIndex = -1;
        sessionsStatus.focus({preventScroll: true});
      }
    }
  }

  function showSession(data) {
    let detailsRequest;
    user = data.user || null;
    if (user && /^[a-f0-9]{16}$/.test(returnProfile || '')) {
      location.replace('/auth.html?profile=' + returnProfile);
      return;
    }
    try { sessionStorage.setItem('orion.header-name', user?.username || ''); } catch { /* Storage is optional. */ }
    plusActive = data.plus_active ?? null;
    plusExpiresAt = data.plus_expires_at ?? null;
    csrf = user ? data.csrf : null;
    adminTab.hidden = !['admin', 'owner'].includes(user?.role);
    friendItems = [];
    adminAccounts = null;
    friendsBusy = adminBusy = false;
    renderFriends();
    renderAdmin();
    sessions = [];
    sessionsBusy = false;
    sessionsFeedback = user ? 'Загрузка сеансов…' : '';
    renderSessions();
    if (user) {
      loadSessions();
      loadFriends();
    }
    loading.hidden = true;
    entry.hidden = Boolean(user);
    profile.hidden = !user;
    emailForm.reset();
    emailEdit.open = false;
    profileError = '';
    emailSaved = false;
    for (const id of profileStatuses.keys()) profileStatus(id, '');
    if (user) {
      const key = csrf;
      detailsRequest = request('/api/profile').then(details => {
        if (csrf === key) setProfileDetails(details);
      }).catch(() => {});
      document.querySelector('#profile-name').textContent = user.username;
      profileEmail.value = user.email || '';
      form.reset();
      password.type = 'password';
      document.querySelector('#password-visibility').setAttribute('aria-pressed', 'false');
    } else {
      profileDetails = null;
      contactsForm.reset();
      passwordForm.reset();
      document.querySelector('#profile-meta').hidden = true;
      document.querySelector('#profile-share').hidden = true;
      avatarRemove.hidden = true;
      document.querySelector('#profile-avatar-image').src = 'assets/orion-badge.png';
      document.querySelector('#profile-avatar-image').classList.remove('custom-avatar');
    }
    render();
    selectProfileTab(profileTabs[0]);
    if (user && location.hash === '#profile-email') {
      selectProfileTab(document.querySelector('#profile-tab-security'));
      emailEdit.open = true;
      profileEmail.focus();
    }
    return detailsRequest;
  }

  function renderPlusStatus() {
    const minutes = Math.ceil((plusExpiresAt * 1000 - Date.now()) / 60000);
    if (plusExpiresAt !== null && minutes <= 0) plusActive = false;
    plusStatus.hidden = plusActive === null;
    plusStatus.textContent = t(plusActive ? 'Активен' : 'Не активен');
    plusStatus.classList.toggle('is-active', plusActive === true);
    plusRemaining.hidden = plusActive !== true;
    if (plusActive === true) {
      const unit = (value, unit) => new Intl.NumberFormat(document.documentElement.lang,
        {style: 'unit', unit, unitDisplay: 'short'}).format(value);
      plusRemaining.querySelector('strong').textContent = plusExpiresAt === null ? '∞'
        : minutes >= 1440 ? `${unit(Math.floor(minutes / 1440), 'day')} ${unit(Math.floor(minutes % 1440 / 60), 'hour')}`
        : minutes >= 60 ? `${unit(Math.floor(minutes / 60), 'hour')} ${unit(minutes % 60, 'minute')}`
        : unit(minutes, 'minute');
    }
    renderProfileTier();
  }

  function renderProfileTier() {
    const role = profileDetails?.role;
    profileTier.hidden = !profileDetails || role === 'owner' || role === 'admin';
    profileTier.textContent = plusActive === true ? 'GleamClient Plus' : plusActive === false ? 'Free' : t('Аккаунт GleamClient');
    profileTier.classList.toggle('is-active', plusActive === true);
  }

  function profileDate(prefix, details) {
    const date = new Date(details.created_at * 1000);
    const time = document.querySelector('#' + prefix + '-date');
    time.dateTime = date.toISOString();
    time.textContent = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'medium'}).format(date);
    document.querySelector('#' + prefix + '-id').textContent = details.id;
    const role = document.querySelector('#' + prefix + '-role');
    const label = details.role === 'owner' ? 'Owner' : details.role === 'admin' ? 'Admin' : '';
    role.textContent = label;
    role.hidden = !label;
    role.classList.toggle('is-owner', details.role === 'owner');
  }

  function setAvatar(image, details) {
    image.classList.toggle('custom-avatar', details.has_avatar);
    const path = details.has_avatar ? details.avatar_url || '/api/profile/' + details.id + '/avatar' : '/assets/orion-badge.png';
    if (image.getAttribute('src') !== path) image.src = path;
  }
  for (const image of document.querySelectorAll('#profile-avatar-image, #public-avatar')) {
    image.addEventListener('error', () => {
      if (!image.classList.contains('custom-avatar')) return;
      image.classList.remove('custom-avatar');
      image.src = 'assets/orion-badge.png';
    });
  }

  function setProfileDetails(details, updateContacts = true) {
    profileDetails = details;
    adminTab.hidden = !['admin', 'owner'].includes(details.role);
    profileDate('profile', details);
    renderProfileTier();
    document.querySelector('#profile-meta').hidden = false;
    setAvatar(document.querySelector('#profile-avatar-image'), details);
    avatarRemove.hidden = !details.has_avatar;
    if (updateContacts) {
      contactsForm.elements.published.checked = details.published;
    }
    renderSocialContacts();
    const link = document.querySelector('#profile-share');
    link.hidden = !details.published;
    link.href = '/auth.html?profile=' + details.id;
  }

  function renderSocialContacts() {
    for (const row of socialRows) {
      const provider = row.dataset.provider;
      const connection = profileDetails?.connections?.[provider];
      const verified = connection?.verified;
      const label = provider === 'discord' ? 'Discord' : 'GitHub';
      const button = row.querySelector('button');
      const state = row.querySelector('.profile-contact-state');
      const name = profileDetails?.[provider];
      state.textContent = verified ? name : t(name ? 'Требуется подтверждение' : connection?.enabled ? 'Не привязан' : 'Подключение пока недоступно');
      state.classList.toggle('is-verified', Boolean(verified));
      button.disabled = busy || (!verified && !connection?.enabled);
      button.textContent = t(verified ? 'Отвязать' : 'Привязать');
      button.setAttribute('aria-label', button.textContent + ' ' + label);
    }
  }

  async function showPublicProfile(id, initial) {
    try {
      if (!/^[a-f0-9]{16}$/.test(id)) throw new Error();
      const details = initial === undefined ? await request('/api/profile/' + id) : initial;
      if (!details) throw new Error();
      profileDetails = details;
      document.querySelector('#public-profile-name').textContent = details.username;
      profileDate('public-profile', details);
      setAvatar(document.querySelector('#public-avatar'), details);
      renderBan(details);
      const discord = document.querySelector('#public-discord');
      discord.hidden = !details.discord;
      discord.querySelector('dd').textContent = details.discord;
      const github = document.querySelector('#public-github');
      github.hidden = !details.github;
      const link = github.querySelector('a');
      link.href = 'https://github.com/' + encodeURIComponent(details.github);
      link.textContent = details.github;
      authCard.hidden = true;
      publicCard.hidden = false;
      document.body.classList.add('profile-page');
      if (details.published) {
        feedbackPanel.hidden = false;
        feedbackSignin.href = '/auth.html?returnProfile=' + id;
        loadFeedback();
      }
    } catch {
      loading.hidden = true;
      document.querySelector('#auth-heading').textContent = t('Профиль недоступен');
    }
  }

  function renderBan(details) {
    const banner = document.querySelector('#public-profile-ban');
    banner.hidden = !details.banned_at;
    if (banner.hidden) return;
    const date = new Date(details.banned_at * 1000);
    const time = document.querySelector('#public-profile-ban-date');
    time.dateTime = date.toISOString();
    time.textContent = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'medium'}).format(date);
  }

  function renderFeedback() {
    const status = document.querySelector('#profile-feedback-status');
    status.textContent = t(feedbackMessage);
    status.hidden = !feedbackMessage;
    if (!feedback) return;
    reactions.hidden = false;
    for (const button of voteButtons) {
      const value = Number(button.dataset.profileVote);
      button.querySelector('b').textContent = value === 1 ? feedback.likes : feedback.dislikes;
      button.setAttribute('aria-pressed', String(feedback.vote === value));
      button.disabled = feedbackBusy || !feedback.can_vote;
    }
    feedbackSignin.hidden = feedback.signed_in;
    const friendship = feedback.friendship;
    publicFriendAction.hidden = !friendship;
    publicFriendButton.disabled = feedbackBusy || friendship !== 'none';
    publicFriendButton.textContent = t(({friend: 'Уже в друзьях', outgoing: 'Заявка отправлена',
      incoming: 'Есть входящая заявка'})[friendship] || 'Добавить в друзья');
    document.querySelector('#profile-comments-gate').hidden = feedback.can_comment;
    commentForm.hidden = !feedback.can_comment;
    commentForm.querySelector('button').disabled = feedbackBusy;
    document.querySelector('#profile-comments-empty').hidden = feedback.comments.length > 0;
    commentsMore.hidden = !feedback.next;
    commentsMore.disabled = feedbackBusy;
    const formatter = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'medium', timeStyle: 'short'});
    const rows = feedback.comments.map(comment => {
      const row = document.createElement('li');
      const heading = document.createElement('div');
      const author = document.createElement('strong');
      const time = document.createElement('time');
      const body = document.createElement('p');
      author.textContent = comment.author;
      const date = new Date(comment.created_at * 1000);
      time.dateTime = date.toISOString();
      time.textContent = formatter.format(date);
      heading.append(author, time);
      if (comment.can_delete) {
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.textContent = t('Удалить комментарий');
        remove.disabled = feedbackBusy;
        remove.addEventListener('click', () => changeFeedback('/comments/' + comment.id + '/delete', {}));
        heading.append(remove);
      }
      body.textContent = comment.body;
      row.append(heading, body);
      return row;
    });
    commentList.replaceChildren(...rows);
  }

  function feedbackPath(path) { return '/api/profile/' + publicId + path; }

  async function loadFeedback(before) {
    if (feedbackBusy) return;
    feedbackBusy = true;
    feedbackMessage = feedback ? '' : 'Загрузка комментариев…';
    renderFeedback();
    try {
      const data = await request(feedbackPath('/feedback') + (before ? '?before=' + before : ''));
      if (before) data.comments = [...feedback.comments, ...data.comments];
      feedback = data;
      csrf = data.csrf;
      feedbackMessage = '';
    } catch {
      feedbackMessage = 'Не удалось загрузить реакции и комментарии.';
    } finally {
      feedbackBusy = false;
      renderFeedback();
    }
  }

  async function changeFeedback(path, body) {
    if (feedbackBusy || !csrf) return;
    feedbackBusy = true;
    feedbackMessage = '';
    renderFeedback();
    try {
      await request(feedbackPath(path), body);
      if (path === '/comments') commentForm.reset();
      feedback = await request(feedbackPath('/feedback'));
      csrf = feedback.csrf;
    } catch (error) {
      feedbackMessage = error.code === 'plus_required' ? 'Комментировать можно с активным Plus.'
        : error.code === 'comment_body' ? 'Комментарий должен содержать от 1 до 500 символов.'
        : error.status === 401 ? 'Сеанс истёк. Войдите в GleamClient ещё раз.' : explain(error);
    } finally {
      feedbackBusy = false;
      renderFeedback();
    }
  }

  for (const button of voteButtons) {
    button.addEventListener('click', () => {
      const value = Number(button.dataset.profileVote);
      changeFeedback('/vote', {value: feedback.vote === value ? 0 : value});
    });
  }
  commentForm.addEventListener('submit', event => {
    event.preventDefault();
    changeFeedback('/comments', {body: commentForm.elements.body.value});
  });
  commentsMore.addEventListener('click', () => loadFeedback(feedback.next));

  const profileStatuses = new Map();
  function communityButton(label, handler, disabled = false, primary = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = primary ? 'community-button community-button-primary' : 'community-button';
    button.textContent = t(label);
    button.disabled = disabled;
    button.addEventListener('click', handler);
    return button;
  }

  function communityEmpty(list, message) {
    if (list.children.length) return;
    const empty = document.createElement('li');
    empty.className = 'community-empty';
    empty.textContent = t(message);
    list.append(empty);
  }

  function renderFriends() {
    const labels = {
      incoming: [['Принять', 'accept'], ['Отклонить', 'decline']],
      outgoing: [['Отозвать', 'cancel']],
      friend: [['Удалить из друзей', 'remove']]
    };
    const count = friendItems.filter(item => item.status === 'incoming').length;
    const badge = document.querySelector('#friends-tab-count');
    badge.hidden = !count;
    badge.textContent = count;
    friendForm.querySelector('button').disabled = friendsBusy;
    for (const [status, actions] of Object.entries(labels)) {
      const list = document.querySelector('#friends-' + status);
      list.replaceChildren();
      for (const item of friendItems.filter(entry => entry.status === status)) {
        const row = document.createElement('li');
        const name = document.createElement(item.public_id ? 'a' : 'strong');
        name.textContent = item.username;
        if (item.public_id) name.href = '/auth.html?profile=' + encodeURIComponent(item.public_id);
        const buttons = document.createElement('div');
        buttons.className = 'community-actions';
        for (const [label, action] of actions) {
          buttons.append(communityButton(label, () => changeFriend(item.username, action), friendsBusy, action === 'accept'));
        }
        row.append(name, buttons);
        list.append(row);
      }
      communityEmpty(list, status === 'friend' ? 'Друзей пока нет.' : 'Заявок пока нет.');
    }
  }

  async function loadFriends() {
    if (friendsBusy || !csrf) return;
    const key = csrf;
    friendsBusy = true;
    renderFriends();
    try {
      const data = await request('/api/friends');
      if (csrf === key) friendItems = data.items;
    } catch (error) {
      if (csrf === key) profileStatus('friends-status', explain(error));
    } finally {
      if (csrf === key) {
        friendsBusy = false;
        renderFriends();
      }
    }
  }

  function friendError(error) {
    return ({friend_missing: 'Игрок с таким ником не найден.', friend_self: 'Нельзя добавить себя в друзья.',
      friend_state: 'Статус заявки уже изменился.', friend_limit: 'Достигнут лимит друзей или заявок.',
      friend_unavailable: 'Этот аккаунт заблокирован.'})[error.code] || explain(error);
  }

  async function changeFriend(username, action) {
    if (friendsBusy || !csrf) return;
    const key = csrf;
    friendsBusy = true;
    renderFriends();
    try {
      await request('/api/friends', {username, action});
      if (csrf === key) {
        if (action === 'request') friendForm.reset();
        profileStatus('friends-status', action === 'request' ? 'Заявка отправлена' : '');
      }
    } catch (error) {
      if (csrf === key) profileStatus('friends-status', friendError(error));
    } finally {
      if (csrf === key) {
        friendsBusy = false;
        await loadFriends();
      }
    }
  }

  friendForm.addEventListener('submit', event => {
    event.preventDefault();
    changeFriend(friendForm.elements.username.value.trim(), 'request');
  });

  publicFriendButton.addEventListener('click', async () => {
    if (!csrf || !profileDetails || feedback?.friendship !== 'none' || feedbackBusy) return;
    feedbackBusy = true;
    renderFeedback();
    try {
      await request('/api/friends', {username: profileDetails.username, action: 'request'});
      feedback.friendship = 'outgoing';
      profileStatus('public-friend-status', '');
    } catch (error) {
      profileStatus('public-friend-status', friendError(error));
    } finally {
      feedbackBusy = false;
      renderFeedback();
    }
  });

  function renderAdmin() {
    const list = document.querySelector('#admin-accounts');
    const summary = document.querySelector('#admin-result-label');
    list.replaceChildren();
    adminForm.querySelector('button').disabled = adminBusy;
    summary.textContent = adminBusy ? t('Подожди…') : adminAccounts
      ? t(adminAccounts.query ? 'Результаты поиска' : 'Последние регистрации') : '';
    if (!adminAccounts) return;
    const dateFormat = new Intl.DateTimeFormat(document.documentElement.lang, {dateStyle: 'medium'});
    for (const item of adminAccounts.items) {
      const row = document.createElement('li');
      const identity = document.createElement('div');
      identity.className = 'admin-identity';
      const title = document.createElement('div');
      title.className = 'admin-account-title';
      const name = document.createElement('strong');
      const role = document.createElement('span');
      const date = document.createElement('time');
      const visibility = document.createElement('span');
      name.textContent = item.username;
      role.className = 'admin-role';
      role.dataset.role = item.role;
      role.textContent = ({member: t('Игрок'), admin: 'Admin', owner: 'Owner'})[item.role];
      date.textContent = dateFormat.format(new Date(item.created_at * 1000));
      visibility.className = 'admin-visibility';
      visibility.dataset.published = String(Boolean(item.published));
      visibility.classList.toggle('is-banned', Boolean(item.banned_at));
      visibility.textContent = t(item.banned_at ? 'Заблокирован' : item.published ? 'Профиль открыт' : 'Профиль скрыт');
      title.append(name, role);
      identity.append(title, date);
      const buttons = document.createElement('div');
      buttons.className = 'community-actions';
      if (item.username !== adminAccounts.username && item.role !== 'owner') {
        const owner = adminAccounts.role === 'owner';
        if (item.published && (owner || item.role === 'member')) {
          buttons.append(communityButton('Скрыть профиль', () => changeAdmin(item.username, 'hide'), adminBusy));
        }
        if (owner || item.role === 'member') {
          const banAction = item.banned_at ? 'unban' : 'ban';
          buttons.append(communityButton(item.banned_at ? 'Разблокировать' : 'Заблокировать',
            () => changeAdmin(item.username, banAction), adminBusy));
          const remove = communityButton('Удалить аккаунт', () => openDeleteDialog(item.username), adminBusy);
          remove.classList.add('community-button-danger');
          buttons.append(remove);
        }
        if (owner) {
          const grant = item.role === 'member';
          buttons.append(communityButton(grant ? 'Назначить Admin' : 'Снять Admin',
            () => changeAdmin(item.username, grant ? 'grant_admin' : 'revoke_admin'), adminBusy));
        }
      }
      const details = document.createElement('details');
      details.className = 'admin-actions';
      const actionsLabel = document.createElement('summary');
      actionsLabel.textContent = t('Действия');
      details.append(actionsLabel, buttons);
      row.append(identity, visibility);
      if (buttons.children.length) row.append(details);
      list.append(row);
    }
    communityEmpty(list, 'Аккаунты не найдены.');
  }

  async function loadAdmin() {
    if (adminBusy || adminTab.hidden || !csrf) return;
    const key = csrf;
    adminBusy = true;
    renderAdmin();
    try {
      const query = adminForm.elements.username.value.trim();
      const data = await request('/api/admin/accounts?q=' + encodeURIComponent(query));
      if (csrf === key) adminAccounts = {...data, query};
    } catch (error) {
      if (csrf === key) {
        adminAccounts = null;
        profileStatus('admin-status', error.status === 403 ? 'Доступ к админ-панели закрыт.' : explain(error));
      }
    } finally {
      if (csrf === key) {
        adminBusy = false;
        renderAdmin();
      }
    }
  }

  function openDeleteDialog(username) {
    deleteTarget = username;
    deleteForm.reset();
    document.querySelector('#account-delete-name').textContent = username;
    deleteForm.querySelector('[type=submit]').disabled = true;
    deleteDialog.showModal();
    deleteForm.elements.confirmation.focus();
  }

  document.querySelector('#account-delete-cancel').addEventListener('click', () => deleteDialog.close());
  deleteForm.elements.confirmation.addEventListener('input', () => {
    deleteForm.querySelector('[type=submit]').disabled = deleteForm.elements.confirmation.value !== deleteTarget;
  });
  deleteForm.addEventListener('submit', event => {
    event.preventDefault();
    const confirmation = deleteForm.elements.confirmation.value;
    if (confirmation !== deleteTarget) return;
    deleteDialog.close();
    changeAdmin(deleteTarget, 'delete', confirmation);
  });

  async function changeAdmin(username, action, confirmation) {
    const label = {hide: 'Скрыть профиль', grant_admin: 'Назначить Admin', revoke_admin: 'Снять Admin',
      ban: 'Заблокировать', unban: 'Разблокировать'}[action];
    if (adminBusy || !csrf || (action !== 'delete' && !confirm(t(label) + ' · ' + username + '?'))) return;
    const key = csrf;
    adminBusy = true;
    renderAdmin();
    try {
      await request('/api/admin/accounts', {username, action, confirmation});
      if (csrf === key) profileStatus('admin-status', action === 'delete' ? 'Аккаунт удалён.' : 'Изменения сохранены.');
    } catch (error) {
      if (csrf === key) profileStatus('admin-status', error.status === 403 ? 'Недостаточно прав для этого действия.' : explain(error));
    } finally {
      if (csrf === key) {
        adminBusy = false;
        await loadAdmin();
      }
    }
  }

  adminForm.addEventListener('submit', event => {
    event.preventDefault();
    profileStatus('admin-status', '');
    loadAdmin();
  });

  function profileStatus(id, message) {
    profileStatuses.set(id, message);
    const status = document.getElementById(id);
    status.textContent = t(message);
    status.hidden = !message;
  }

  async function profileAction(status, action) {
    if (busy || sessionsBusy || !csrf) return;
    busy = true;
    profileStatus(status, '');
    render();
    try {
      profileStatus(status, await action());
    } catch (error) {
      const messages = {credentials: 'Неверный текущий пароль', avatar: 'Не удалось обработать изображение',
        oauth_disabled: 'Подключение пока недоступно', oauth_state: 'Запрос привязки устарел. Начните заново.',
        oauth_unavailable: 'Сервис привязки временно недоступен.', oauth_taken: 'Этот контакт уже привязан к другому аккаунту GleamClient.'};
      profileStatus(status, messages[error.code] || explain(error));
    } finally {
      busy = false;
      render();
    }
  }

  passwordForm.addEventListener('submit', event => {
    event.preventDefault();
    const {current_password, new_password, confirmation} = passwordForm.elements;
    const status = 'profile-password-status';
    if (!validPassword(new_password.value)) return profileStatus(status, passwordError);
    if (new_password.value !== confirmation.value) return profileStatus(status, passwordMismatch);
    profileAction(status, async () => {
      const data = await request('/api/password', {current_password: current_password.value, new_password: new_password.value});
      csrf = data.csrf;
      passwordForm.reset();
      await loadSessions();
      return 'Пароль изменён.';
    });
  });

  contactsForm.addEventListener('submit', event => {
    event.preventDefault();
    profileAction('profile-contacts-status', async () => {
      setProfileDetails(await request('/api/profile', {published: contactsForm.elements.published.checked}));
      return 'Настройки видимости сохранены.';
    });
  });

  for (const row of socialRows) {
    row.querySelector('button').addEventListener('click', () => {
      const provider = row.dataset.provider;
      profileAction('profile-contacts-status', async () => {
        if (profileDetails?.connections?.[provider]?.verified) {
          setProfileDetails(await request('/api/profile/oauth/' + provider + '/unlink', {}), false);
          return 'Контакт отвязан.';
        }
        const {url} = await request('/api/profile/oauth/' + provider + '/start', {});
        location.assign(url);
        return '';
      });
    });
  }

  async function encodeAvatar(file) {
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 5_000_000) {
      throw Object.assign(new Error(), {code: 'avatar'});
    }
    let bitmap;
    try {
      bitmap = await createImageBitmap(file);
      const size = Math.min(bitmap.width, bitmap.height);
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 64;
      const context = canvas.getContext('2d', {alpha: false});
      context.drawImage(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size, 0, 0, 64, 64);
      for (const quality of [.85, .65, .45, .25]) {
        const encoded = canvas.toDataURL('image/jpeg', quality).split(',')[1];
        if (encoded.length <= 3600) return encoded;
      }
    } catch {
      // Повреждённое изображение обрабатывается как неподходящий файл.
    } finally {
      bitmap?.close();
    }
    throw Object.assign(new Error(), {code: 'avatar'});
  }

  avatarFile.addEventListener('change', () => {
    const file = avatarFile.files[0];
    avatarFile.value = '';
    if (file) profileAction('profile-avatar-status', async () => {
      setProfileDetails(await request('/api/profile/avatar', {avatar: await encodeAvatar(file)}), false);
      return 'Аватар обновлён.';
    });
  });
  avatarRemove.addEventListener('click', () => profileAction('profile-avatar-status', async () => {
    setProfileDetails(await request('/api/profile/avatar', {avatar: null}), false);
    return 'Аватар удалён.';
  }));

  document.querySelectorAll('[data-auth-mode]').forEach(button => button.addEventListener('click', () => {
    if (busy) return;
    mode = button.dataset.authMode;
    identityChecks.forEach(check => clearTimeout(check.timer));
    identityChecks.clear();
    errorSource = '';
    confirmation.value = '';
    consent.checked = false;
    render();
    if (mode === 'register') {
      checkIdentity(username);
      checkIdentity(registerEmail);
    }
  }));
  for (const input of [username, registerEmail]) {
    for (const event of ['input', 'change']) {
      input.addEventListener(event, () => { if (mode === 'register') checkIdentity(input); });
    }
  }
  for (const input of [password, confirmation]) {
    input.addEventListener('input', renderChecks);
    input.addEventListener('change', renderChecks);
  }
  document.querySelector('#password-visibility').addEventListener('click', event => {
    password.type = password.type === 'password' ? 'text' : 'password';
    event.currentTarget.setAttribute('aria-pressed', String(password.type === 'text'));
    render();
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy) return;
    errorSource = '';
    const name = username.value.trim();
    if (!usernamePattern.test(name)) {
      errorSource = 'Имя: от 3 до 24 латинских букв, цифр или _'; render(); username.focus(); return;
    }
    if (mode === 'register' && !registerEmail.validity.valid) {
      errorSource = 'Введи корректный email'; render(); registerEmail.focus(); return;
    }
    if (!validPassword(password.value, mode === 'register' ? 12 : 1)) {
      errorSource = mode === 'register' ? passwordError : 'Введи пароль'; render(); password.focus(); return;
    }
    if (mode === 'register' && confirmation.value !== password.value) {
      errorSource = passwordMismatch; render(); confirmation.focus(); return;
    }
    if (mode === 'register' && !consent.checked) {
      errorSource = 'Для регистрации прими пользовательское соглашение'; render(); consent.focus(); return;
    }
    const payload = {username: name, password: password.value};
    if (mode === 'register') payload.email = registerEmail.value.trim();
    busy = true; render();
    try {
      const data = await request(mode === 'register' ? '/api/register' : '/api/login', payload);
      showSession(data);
      request('/api/me').then(session => {
        if (csrf !== data.csrf || session.csrf !== data.csrf) return;
        plusActive = session.plus_active ?? null;
        plusExpiresAt = session.plus_expires_at ?? null;
        render();
      }).catch(() => {});
    } catch (error) {
      errorSource = explain(error);
      if (mode === 'register') {
        const input = {taken: username, email_taken: registerEmail, email: registerEmail, username}[error.code];
        if (input) {
          clearTimeout(identityChecks.get(input)?.timer);
          identityChecks.set(input, {value: input.value.trim(), problem: errorSource});
        }
      }
    }
    finally { busy = false; render(); }
  });
  emailForm.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || !user) return;
    profileError = '';
    emailSaved = false;
    if (!profileEmail.validity.valid) {
      profileError = 'Введи корректный email';
      render();
      profileEmail.focus();
      return;
    }
    if (!emailPassword.value) {
      profileError = 'Введи пароль';
      render();
      emailPassword.focus();
      return;
    }
    busy = true;
    render();
    try {
      const data = await request('/api/email', {email: profileEmail.value.trim(), current_password: emailPassword.value});
      user.email = data.email;
      user.email_verified = data.email_verified;
      verificationText = '';
      profileEmail.value = data.email;
      emailSaved = true;
    } catch (error) {
      if (error.code === 'auth') {
        showSession({user: null});
        mode = 'login';
        errorSource = explain(error);
      } else {
        profileError = error.code === 'credentials' ? 'Неверный текущий пароль' : explain(error);
      }
    } finally {
      emailPassword.value = '';
      busy = false;
      render();
    }
  });
  document.querySelector('#forgot-password').addEventListener('click', event => {
    event.preventDefault();
    recoveryMode = 'forgot';
    render();
    recoveryEmail.focus();
  });
  emailVerify.addEventListener('click', async () => {
    if (busy) return;
    busy = true;
    verificationText = '';
    render();
    try {
      await request('/api/email/verify/request', {});
      verificationText = 'Проверь почту. Письмо может прийти не сразу';
    } catch (error) { verificationText = explain(error); }
    finally { busy = false; render(); }
  });
  recoveryForm.addEventListener('submit', async event => {
    event.preventDefault();
    if (busy || recoveryDone) return;
    let endpoint = '/api/password/reset/request';
    let payload = {email: recoveryEmail.value.trim()};
    if (recoveryMode === 'verify') {
      endpoint = '/api/email/verify/confirm';
      payload = {token: link[2]};
    } else if (recoveryMode === 'reset') {
      const password = recoveryPassword.value;
      if (!validPassword(password)) {
        recoveryText = passwordError;
        render();
        recoveryPassword.focus();
        return;
      }
      if (recoveryConfirmation.value !== password) {
        recoveryText = passwordMismatch;
        render();
        recoveryConfirmation.focus();
        return;
      }
      endpoint = '/api/password/reset/confirm';
      payload = {token: link[2], password};
    }
    busy = true;
    recoveryText = '';
    render();
    try {
      await request(endpoint, payload);
      recoveryDone = true;
      recoveryText = recoveryMode === 'verify' ? 'Почта подтверждена' : recoveryMode === 'reset' ? 'Пароль изменён. Войди с новым паролем' : 'Если к этому адресу привязан аккаунт с подтверждённой почтой, мы отправим письмо';
      recoveryForm.reset();
    } catch (error) { recoveryText = explain(error); }
    finally { busy = false; render(); }
  });
  logout.addEventListener('click', async () => {
    if (busy) return;
    busy = true; profileError = ''; render();
    try {
      await request('/api/logout', {});
      showSession({user: null});
      mode = 'login';
    } catch (error) {
      if (error.status === 401) showSession({user: null});
      else profileError = explain(error);
    } finally { busy = false; render(); }
  });
  revokeOthers.addEventListener('click', () => revokeSession('others'));
  setInterval(() => { if (user && publicId === null) renderPlusStatus(); }, 60000);
  document.addEventListener('orion:languagechange', () => {
    if (publicId !== null) {
      if (profileDetails) {
        profileDate('public-profile', profileDetails);
        renderBan(profileDetails);
      }
      else if (loading.hidden) document.querySelector('#auth-heading').textContent = t('Профиль недоступен');
      renderFeedback();
      return;
    }
    render();
    renderSessions();
    renderFriends();
    renderAdmin();
    if (profileDetails) profileDate('profile', profileDetails);
    for (const [id, message] of profileStatuses) profileStatus(id, message);
  });
  const bootstrap = document.querySelector('#orion-session');
  const initial = bootstrap ? JSON.parse(bootstrap.textContent) : undefined;
  if (publicId !== null) {
    showPublicProfile(publicId, initial?.public_profile);
  } else if (recoveryMode) {
    loading.hidden = true;
    render();
  } else {
    if (bootstrap && location.pathname === '/api/profile-page') history.replaceState(null, '', '/auth.html' + location.search + location.hash);
    const initialSession = initial ? Promise.resolve(initial) : request('/api/me');
    initialSession.then(async data => {
      const detailsRequest = showSession(data);
      if (!socialCallback) return;
      if (!user) {
        errorSource = 'Сеанс завершён. Войдите в GleamClient и повторите привязку.';
        render();
        return;
      }
      selectProfileTab(document.querySelector('#profile-tab-contacts'));
      const code = socialReturn.get('code');
      const state = socialReturn.get('state');
      if (!code || !state) {
        profileStatus('profile-contacts-status', 'Привязка отменена.');
        return;
      }
      await detailsRequest;
      profileAction('profile-contacts-status', async () => {
        setProfileDetails(await request('/api/profile/oauth/' + returnedProvider + '/finish', {code, state}));
        return 'Контакт подтверждён.';
      });
    }).catch(error => { errorSource = explain(error); showSession({user: null}); });
  }
})();
