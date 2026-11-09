'use strict';
const $ = id => document.getElementById(id);
const island = $('island'), compact = $('compact'), full = $('full'), surface = island.querySelector('.surface');
const CAP_W = 344, HUD_H = 34, TRACK_H = 57, FULL_W = 370, FULL_H = 205, SHOULDER = 11;
const clamp = (value,min=0,max=1) => Math.max(min,Math.min(max,value));
const mix = (a,b,t) => a + (b-a)*t;
let state = {}, phase = 0, speed = 0, lastFrame = 0;
let mouse = {x:-1000,y:-1000}, hoverStarted = 0, lastHovered = -10000;
let dragging = null, range = null, custom = null, lastLayout = '', position = 0, positionAt = 0;
let lastAudible = .7, meterFrame = -1, currentBounds = null, displayedService = '';
const clock = () => performance.now();
const send = (action,values={}) => window.webkit?.messageHandlers?.paradox?.postMessage(JSON.stringify({action,...values}));
const t = key => state.labels?.[key] ?? key;
const text = (id,value) => { if ($(id).textContent !== String(value)) $(id).textContent = value; };
const track = value => (value.source ?? '') + '\0' + (value.title ?? '') + '\0' + (value.artist ?? '');
const active = () => Boolean(state.media && state.active);
const formatTime = value => {
  const total = Math.max(0,Math.floor(value || 0));
  return Math.floor(total/60) + ':' + String(total%60).padStart(2,'0');
};
const serviceIcons = {
  spotify: '<path fill-rule="evenodd" d="M12 0a12 12 0 1 0 0 24 12 12 0 0 0 0-24zm5.5 17.3a.75.75 0 0 1-1 .25c-2.82-1.72-6.37-2.11-10.54-1.16a.75.75 0 0 1-.33-1.46c4.57-1.04 8.49-.59 11.62 1.32.36.21.47.67.25 1.05zm1.47-3.27a.93.93 0 0 1-1.28.31c-3.22-1.98-8.14-2.55-11.95-1.4a.93.93 0 1 1-.54-1.78c4.35-1.32 9.76-.68 13.46 1.59a.93.93 0 0 1 .31 1.28zm.13-3.4c-3.87-2.3-10.25-2.52-13.95-1.4a1.12 1.12 0 1 1-.65-2.14c4.24-1.29 11.29-1.04 15.74 1.61a1.12 1.12 0 0 1-1.14 1.93z"/>',
  youtube: '<path fill-rule="evenodd" d="M12 4.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15zm0 1a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM10 8.5l6 3.5-6 3.5z"/>',
  // The impulse mark comes from the favicon published by music.yandex.ru.
  yandex: '<path d="m130.863 57.739-.468-2.327-19.788-3.457 11.498-15.557-1.337-1.462-16.913 8.11 2.139-21.54-1.738-.997-10.295 17.418L82.395 12H80.39l2.74 25.064-29.08-23.269-2.474.732 22.396 28.122-44.323-14.76-2.006 2.261L67.22 52.686l-54.618 4.521-.602 3.39 56.757 6.184-47.33 39.157 2.005 2.726 56.356-30.648-11.164 53.983h3.41l21.592-50.792 13.17 39.756 2.34-1.795-5.415-40.42 20.524 23.268 1.337-2.128-15.711-28.853 21.928 8.111.201-2.46-19.655-14.493 18.518-4.454Z"/>',
  generic: '<path d="M10 16.2V7.8l8-1.6v9.2c0 1.4-1.1 2.3-2.5 2.3-1 0-1.7-.5-1.7-1.4 0-1.2 1.3-2.1 2.9-2V9.4l-5.4 1.1v6.7c0 1.4-1.1 2.3-2.5 2.3-1 0-1.8-.6-1.8-1.5 0-1.3 1.3-2.1 3-1.8z"/>'
};
function serviceFor(source) {
  const id = (source ?? '').toLowerCase();
  if (/yandex[. _-]?(?:desktop[. _-])?music|music\.yandex|яндекс[. _-]?музык/.test(id)) return {icon:'yandex',name:'Яндекс Музыка'};
  if (id.includes('spotify')) return {icon:'spotify',name:'Spotify'};
  if (/music\.youtube|youtube[. _-]?music/.test(id)) return {icon:'youtube',name:'YouTube Music'};
  if (/apple[. _-]?music|music\.apple|itunes/.test(id)) return {icon:'apple',name:'Apple Music'};
  for (const [key,name] of [['msedge','Microsoft Edge'],['chrome','Google Chrome'],['firefox','Firefox'],
    ['brave','Brave'],['opera','Opera'],['vivaldi','Vivaldi']]) {
    if (id.includes(key)) return {icon:'generic',name,browser:true};
  }
  for (const [key,name] of [['telegram','Telegram'],['vlc','VLC'],['aimp','AIMP']]) {
    if (id.includes(key)) return {icon:'generic',name};
  }
  const name = (source ?? '').split('!').pop().replace(/\.exe$/i,'');
  return {icon:'generic',name:name && name.length <= 30 ? name : 'MediaPlayer'};
}
function setEnabled(id,enabled,label) {
  const element = $(id);
  element.disabled = !enabled;
  if (label) { element.setAttribute('aria-label',label); element.setAttribute('title',label); }
}
function setRangeEnabled(id,enabled,label) {
  const element = $(id);
  element.setAttribute('aria-disabled',String(!enabled));
  element.setAttribute('aria-label',label);
  element.tabIndex = enabled ? 0 : -1;
}
function fill(id,value) {
  const fraction = clamp(value);
  const percent = (fraction*100).toFixed(2) + '%';
  const bar = $(id+'-fill'), thumb = $(id+'-thumb');
  if (bar.style.width !== percent) { bar.style.width = percent; thumb.style.left = percent; }
  const range = $(id), accessibleValue = String(Math.round(fraction*100));
  if (range.getAttribute('aria-valuenow') !== accessibleValue) range.setAttribute('aria-valuenow',accessibleValue);
}
function playbackPosition(now) {
  if (!active()) return 0;
  const elapsed = state.playing ? Math.max(0,(now-positionAt)/1000) : 0;
  const duration = Number(state.duration) || 0;
  return duration > 0 ? clamp(position+elapsed,0,duration) : Math.max(0,position+elapsed);
}
function updateProgress(now) {
  const duration = Number(state.duration) || 0;
  const current = range?.kind === 'seek' ? range.value*duration : playbackPosition(now);
  fill('seek',duration > 0 ? current/duration : 0);
  text('elapsed',formatTime(current));
  text('remaining',duration > 0 ? '−'+formatTime(Math.max(0,duration-current)) : '—:—');
  if (range?.kind !== 'volume') {
    fill('volume',Number(state.volume) || 0);
    text('volume-value',state.canVolume ? Math.round((Number(state.volume)||0)*100)+'%'
      : '—');
  }
}
function refresh() {
  const playing = active() && state.playing;
  const service = serviceFor(state.source);
  document.documentElement.toggleAttribute('data-light',Boolean(state.light));
  island.toggleAttribute('data-solid',!state.glass);
  island.toggleAttribute('data-interactive',Boolean(state.interactive));
  const accent = /^#[0-9a-f]{6}$/i.test(state.accent ?? '') ? state.accent : state.light ? '#596fa8' : '#c2d2f7';
  island.style.setProperty('--accent',accent);
  for (const id of ['version','username','metrics']) $(id).hidden = !state.hud;
  text('version',state.version ?? '');
  text('username',state.username ?? '');
  text('fps',state.fps ?? '—');
  text('ping',Number(state.ping)>=0 ? state.ping : '—');
  compact.hidden = !active();
  text('compact-title',state.title || service.name);
  text('status',playing ? t('Сейчас играет') : t('На паузе'));
  if (!playing) for (const bar of $('compact-eq').children) bar.style.height = '4px';
  text('song-title',!state.media ? t('Твоя музыка — рядом') : active() ? state.title || service.name : t('Музыка не играет'));
  text('song-artist',active() ? state.artist || '' : t('Открой музыку в своём плеере'));
  text('source-name',active() ? service.name : '');
  $('full-service').hidden = !active();
  if (displayedService !== service.icon) {
    displayedService = service.icon;
    const size = service.icon === 'yandex' ? 144 : 24;
    const markup = '<svg viewBox="0 0 '+size+' '+size+'">' + (serviceIcons[service.icon] ?? serviceIcons.generic) + '</svg>';
    for (const id of ['compact-service','full-service']) {
      $(id).setAttribute('data-service',service.icon);
      $(id).innerHTML = markup;
    }
  }
  const showCover = active() && Boolean(state.cover);
  const showCompactCover = showCover && service.icon === 'generic';
  for (const [id,visible] of [['cover',showCover],['compact-cover',showCompactCover]]) {
    const cover = $(id);
    if (visible && cover.getAttribute('src') !== state.cover) cover.src = state.cover;
    cover.hidden = !visible;
  }
  $('compact-service').hidden = showCompactCover;
  $('cover-fallback').hidden = showCover;
  $('play-icon').hidden = Boolean(playing);
  $('pause-icon').hidden = !playing;
  const capabilities = {play:'canPlay',next:'canNext',previous:'canPrevious',shuffle:'canShuffle',repeat:'canRepeat'};
  const labels = {play:playing?'Пауза':'Воспроизвести',next:'Следующий трек',previous:'Предыдущий трек',shuffle:'Перемешать',repeat:'Повтор'};
  for (const [id,key] of Object.entries(capabilities)) setEnabled(id,active() && state[key],t(labels[id]));
  $('shuffle').setAttribute('aria-pressed',String(Boolean(state.shuffle)));
  $('repeat').setAttribute('aria-pressed',String(Number(state.repeat)>0));
  $('repeat-one').hidden = Number(state.repeat)!==2;
  setRangeEnabled('seek',active() && state.canSeek,t('Перемотка'));
  const browserVolume = active() && service.browser;
  const volumeSource = t('Громкость')+' · '+service.name;
  $('volume-source').hidden = !browserVolume;
  text('volume-source',browserVolume ? volumeSource : '');
  const volumeLabel = state.canVolume ? (browserVolume ? volumeSource : t('Громкость приложения')+' · '+service.name)
    : t('Громкость недоступна') + (browserVolume ? ' · '+service.name : '');
  setRangeEnabled('volume',active() && state.canVolume,volumeLabel);
  $('volume').title = volumeLabel;
  setEnabled('mute',active() && state.canVolume,volumeLabel);
  setEnabled('settings',true,t('Настройки'));
  $('enable').hidden = Boolean(state.media);
  $('transport').hidden = !state.media;
  text('enable',t('Включить MediaPlayer'));
  $('control-error').hidden = !state.error;
  text('control-error',state.error ? t(state.error) : '');
  $('header').setAttribute('aria-label',t('Перемести шторку за заголовок'));
  $('sound-waves').hidden = Number(state.volume)<=.001;
  updateProgress(clock());
}
function layout() {
  const expandedHeight = FULL_H + (state.error ? 16 : 0);
  const scale = Math.min(Number(state.scale)||1,window.innerWidth/(FULL_W+2*SHOULDER),
    window.innerHeight/expandedHeight);
  if (scale <= 0) return currentBounds;
  const width = mix(CAP_W,FULL_W,phase), height = mix(active()?TRACK_H:HUD_H,expandedHeight,phase);
  const radius = mix(13,20,phase);
  const edge = (FULL_W/2+SHOULDER)*scale;
  let center = window.innerWidth/2;
  if (custom !== null || state.custom) center=(custom ?? Number(state.x))+CAP_W*scale/2;
  else if (state.placement === 'TOP_LEFT') center=edge;
  else if (state.placement === 'TOP_RIGHT') center=window.innerWidth-edge;
  center=clamp(center,edge,Math.max(edge,window.innerWidth-edge));
  const x = center-width*scale/2;
  const signature = [x.toFixed(2),width.toFixed(2),height.toFixed(2),scale.toFixed(4)].join(',');
  if (signature !== lastLayout) {
    island.style.setProperty('zoom',String(scale));
    island.style.left=x/scale+'px';
    island.style.width=width+'px'; island.style.height=height+'px';
    surface.style.borderRadius='0 0 '+radius+'px '+radius+'px';
    compact.style.opacity=String(clamp(1-phase/.25));
    full.style.opacity=String(clamp((phase-.22)/.7));
    full.style.transform='translateY('+mix(-8,0,phase)+'px)';
    full.style.pointerEvents=phase>.95?'auto':'none';
    $('header').setAttribute('aria-expanded',String(phase>.95));
    currentBounds={x,y:0,width:width*scale,height:height*scale,radius:radius*scale,scale,center,edge};
    send('bounds',{x,y:0,width:width*scale,height:height*scale,radius:radius*scale});
    lastLayout=signature;
  }
  return currentBounds;
}
function animate(now) {
  const delta = lastFrame ? Math.min(.05,Math.max(0,(now-lastFrame)/1000)) : 0;
  lastFrame=now;
  const bounds=layout();
  const inside=state.interactive && bounds && mouse.x>=bounds.x-3 && mouse.x<=bounds.x+bounds.width+3
    && mouse.y>=bounds.y-3 && mouse.y<=bounds.y+bounds.height+3;
  if (inside) { if(!hoverStarted)hoverStarted=now; lastHovered=now; } else hoverStarted=0;
  const target = state.interactive && (range || inside && now-hoverStarted>=85 || phase>.005 && now-lastHovered<130) ? 1 : 0;
  if (!dragging) {
    if (state.animated===false) {phase=target;speed=0;}
    else {
      const distance=phase-target, coefficient=speed+19*distance, decay=Math.exp(-19*delta);
      phase=clamp(target+(distance+coefficient*delta)*decay);
      speed=(speed-19*coefficient*delta)*decay;
      if(Math.abs(phase-target)<.001 && Math.abs(speed)<.02){phase=target;speed=0;}
    }
  }
  layout();
  if(phase>.2) updateProgress(now);
  const frame=Math.floor(now/80);
  if(frame!==meterFrame) {
    meterFrame=frame;
    if(active() && state.playing && state.animated!==false && phase<.25)
      for(const [i,bar] of [...$('compact-eq').children].entries())
        bar.style.height=(3+(Math.sin(frame*.57+i*1.7)+1)*4).toFixed(2)+'px';
  }
  requestAnimationFrame(animate);
}
function setRange(event) {
  const value=clamp((event.clientX-range.left)/range.width);
  range.value=value;
  fill(range.kind==='seek'?'seek':'volume',value);
  if(range.kind==='volume') {
    text('volume-value',Math.round(value*100)+'%');
    if(value>.001)lastAudible=value;
    if(clock()-range.sent>=130) {
      send('volume',{source:range.source,value,final:false});
      range.sent=clock();
    }
  }
}
document.addEventListener('pointerdown',event=>{
  if(event.button!==0 || !state.interactive)return;
  mouse={x:event.clientX,y:event.clientY};
  const control=event.target.closest('[data-control]');
  if(control && control.getAttribute('aria-disabled')!=='true') {
    const rect=control.querySelector('.track-line').getBoundingClientRect();
    if(rect.width<=0)return;
    event.preventDefault();
    range={kind:control.dataset.control,element:control,left:rect.left,width:rect.width,
      source:state.source,track:track(state),value:0,sent:-1000};
    control.setAttribute('data-dragging',''); control.focus({preventScroll:true});
    setRange(event);
    return;
  }
  if(!event.target.closest('button') && event.target.closest('[data-drag-handle]') && currentBounds) {
    event.preventDefault();
    dragging={x:event.clientX,center:currentBounds.center,moved:false};
  }
});
document.addEventListener('pointermove',event=>{
  mouse={x:event.clientX,y:event.clientY};
  if(range){setRange(event);return;}
  if(!dragging || !currentBounds)return;
  if(Math.abs(event.clientX-dragging.x)<4 && !dragging.moved)return;
  dragging.moved=true;
  const bounds=currentBounds;
  const center=clamp(dragging.center+event.clientX-dragging.x,bounds.edge,Math.max(bounds.edge,window.innerWidth-bounds.edge));
  custom=center-CAP_W*bounds.scale/2;
  island.setAttribute('data-dragging','');
  layout();
});
function release(event) {
  if(event.button!==0)return;
  if(range) {
    setRange(event);
    const finished=range;
    range=null; finished.element.removeAttribute('data-dragging');
    if(finished.source===state.source && (finished.kind==='volume'||finished.track===track(state)))
      send(finished.kind,{source:finished.source,value:finished.kind==='seek'?finished.value*(Number(state.duration)||0):finished.value,final:true});
  }
  if(dragging) {
    if(dragging.moved && custom!==null)send('move',{x:custom});
    dragging=null; island.removeAttribute('data-dragging');
  }
}
function cancelPointer() {
  if(range)range.element.removeAttribute('data-dragging');
  if(dragging)custom=null;
  range=null;
  dragging=null;
  island.removeAttribute('data-dragging');
}
document.addEventListener('pointerup',release);
document.addEventListener('pointercancel',cancelPointer);
document.addEventListener('click',event=>{
  if(!state.interactive)return;
  const button=event.target.closest('[data-action]');
  if(!button || button.disabled)return;
  const action=button.dataset.action;
  if(action==='mute') {
    if(!state.canVolume)return;
    const value=Number(state.volume)>.001?0:lastAudible;
    if(Number(state.volume)>.001)lastAudible=Number(state.volume);
    state.volume=value; fill('volume',value); text('volume-value',Math.round(value*100)+'%');
    send('volume',{source:state.source,value,final:true}); return;
  }
  send(action,{source:state.source??''});
});
document.addEventListener('keydown',event=>{
  if(!state.interactive)return;
  const element=event.target.closest('[data-control]');
  if(!element || element.getAttribute('aria-disabled')==='true')return;
  const kind=element.dataset.control;
  let value=kind==='volume'?Number(state.volume):playbackPosition(clock())/(Number(state.duration)||1);
  const step=kind==='volume'?.05:5/(Number(state.duration)||1);
  const next={ArrowLeft:value-step,ArrowDown:value-step,ArrowRight:value+step,ArrowUp:value+step,Home:0,End:1}[event.key];
  if(next===undefined)return;
  event.preventDefault();value=clamp(next);
  send(kind,{source:state.source,value:kind==='seek'?value*state.duration:value,final:true});
  if(kind==='volume')state.volume=value;
});
window.paradox={
  cancelPointer,
  update(next) {
    const changed=track(next)!==track(state), sourceChanged=next.source!==state.source;
    if(range && (sourceChanged || range.kind==='seek' && changed)) {
      range.element.removeAttribute('data-dragging'); range=null;
    }
    if(changed || Number(next.position)!==Number(state.position) || next.playing!==state.playing) {
      position=Number(next.position)||0;positionAt=clock();
    }
    if(!next.interactive && state.interactive) {
      cancelPointer();
      mouse={x:-1000,y:-1000}; hoverStarted=0; lastHovered=-10000;
      phase=0; speed=0;
    }
    if(!dragging && (next.x!==state.x || next.custom!==state.custom || next.placement!==state.placement))custom=null;
    if(sourceChanged)lastAudible=Number(next.volume)>.001?Number(next.volume):.7;
    state=next;refresh();layout();
  }
};
refresh();
requestAnimationFrame(animate);
