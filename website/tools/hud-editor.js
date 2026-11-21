'use strict';
const $ = id => document.getElementById(id);
const stage = $('stage'), panel = $('panel'), layers = $('layers');
const clamp = (n,min,max) => Math.max(min,Math.min(max,n));
const glyphs={
  grid:'M3 3h14v14H3zM10 3v14M3 10h14',
  move:'M3 8V3h5m4 0h5v5m0 4v5h-5m-4 0H3v-5',
  add:'M10 5v10M5 10h10',
  close:'m6 6 8 8m0-8-8 8',
  more:'M4 10h.01M10 10h.01M16 10h.01',
  eye:'M2 10s3-5 8-5 8 5 8 5-3 5-8 5-8-5-8-5Zm10 0a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
  edit:'m5 12 8-8 3 3-8 8-4 1zM11.5 5.5l3 3',
  check:'m4 10 4 4 8-8',
  mouse:'M6 6a4 4 0 0 1 8 0v7a4 4 0 0 1-8 0zm0 3h8m-4-7v7',
  heart:'M10 17 4 11a4 4 0 0 1 6-5 4 4 0 0 1 6 5z'
};
const widgetGlyphs={
  keys:'M7 7h6v6H7zM7 7H5a2 2 0 1 1 2-2zm6 0V5a2 2 0 1 1 2 2zm0 6h2a2 2 0 1 1-2 2zm-6 0v2a2 2 0 1 1-2-2z',
  potions:'M10 3c2.7 3.5 5 6.1 5 9a5 5 0 1 1-10 0c0-2.9 2.3-5.5 5-9z',
  target:'M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0zM12.5 10a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z',
  nearby:'M12 6a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM4 16v-1a5.5 5.5 0 0 1 11 0v1m0-10a2 2 0 0 1 0 4m1 2a3 3 0 0 1 2 3',
  inventory:'M4 8h12l1 8H3zm3 0V6a3 3 0 0 1 6 0v2',
  football:'M3 16V5h14v11M3 11h14',
  basketball:'M5 4h10M3 8h14m-12 0 2 8h6l2-8M10 8v8',
  scoreboard:'M4 5h12M4 10h9M4 15h6m3 0 2 2 3-4',
  hotbar:'M2 7h16v6H2zm5 0v6m6-6v6',
  chat:'M3 4h14v10H8l-4 3v-3H3zm3 4h8m-8 3h5',
  invite:'M3 5h14v10H3zm0 0 7 6 7-6'
};
function uiIcon(name){
  const widgetGlyph=widgetGlyphs[name];
  if (!widgetGlyph && !glyphs[name]) return '';
  return '<svg class="ui-icon'+(widgetGlyph?' widget-icon':'')+'" viewBox="0 0 20 20" aria-hidden="true">'
    +'<path d="'+(widgetGlyph||glyphs[name])+'"/></svg>';
}
// The local redesign uses the same readouts as the game with its own layout.
const part=(kind,x,y,w,h,props={})=>({kind,x,y,w,h,...props});
const panelPart=(w,h,radius=7)=>part('panel',0,0,w,h,{radius});
const label=(text,x,y,w,h,size,color='var(--text)',align='left',bold=false)=>part('label',x,y,w,h,{text,size,color,align,bold});
const rule=(x,y,w,h=1)=>part('divider',x,y,w,h);
const box=(x,y,w,h,radius,color)=>part('box',x,y,w,h,{radius,color});
const well=(x,y,w,h,radius)=>part('well',x,y,w,h,{radius});
const keyPart=(text,x,y,w=17,mouse='')=>part('key',x,y,w,17,{text,mouse,radius:6,pressed:text==='W'});
const icon=(path,x,y,size,fill=false)=>part('icon',x,y,size,size,{path,fill,color:'var(--accent)'});
const art=(image,x,y,size,muted=false)=>({image,x,y,size,muted});
const health='#b7df52', muted='var(--muted)';
function targetScene(){
  return [panelPart(152,45,8),well(5,5,30,30,4),
    label('Quartz',39,5,33,12,9,'var(--text)','left',true),
    ...Array.from({length:4},(_,i)=>box(74+i*2,13-i*1.6,1.2,2+i*1.6,.55,'#67d99b')),
    label('38',84,6,27,11,7,'#67d99b','left',true),
    label('13.5',39,16,19,12,8,health,'left',true),
    label('/ 20',59,16,24,12,8,muted),label('2.5 m',116,16,30,12,7.5,muted,'right'),
    box(6,39,140,3,1.5,'#a3b39329'),box(6,39,95,3,1.5,health)];
}
const effects=[panelPart(123,56,4),part('icon',8,5,10,10,{path:widgetGlyphs.potions,color:'#8970e5'}),
  label('Potions',22,2,89,15,8,'var(--text)','left',true)];
for(const [index,[name,time]] of [['Resistance I','2m2s'],['Fire Resistance I','2m2s']].entries()){
  const y=22+15*index;
  effects.push(label(name,22,y,65,15,7.5,muted),label(time,88,y,26,15,7.5,'var(--text)','right',true));
}
const near=[panelPart(127,61,5),icon(widgetGlyphs.nearby,8,6,11),
  label('Игроки рядом',23,3,81,17,8,'var(--text)','left',true),
  rule(8,22,111),label('2',105,4,14,15,7,'var(--accent)','right',true)];
for(const [i,[name,distance]] of [['Quartz','8'],['Nova','21']].entries()){
  const y=25+i*16;
  if(i)near.push(rule(7,y-1,113));
  near.push(icon(i?'m5 11 5-5 5 5M10 6v9':'m7 6 7 0v7M14 6l-8 8',9,y+2,11),label(name,24,y,65,15,8),
    label(distance,80,y,28,15,8,'var(--text)','right',true),label('m',110,y,7,15,6.5,muted));
}
const inventory=[panelPart(178,64,5),
  ...Array.from({length:27},(_,i)=>box(4+i%9*19,4+Math.floor(i/9)*19,18,18,2,'#ffffff10'))];
function score(text,glyph){
  return [panelPart(67,18,4),icon(glyph,6,4,10),
    label(text,21,0,40,18,8,'var(--text)','left',true)];
}
const catalog={
  status:{name:'HUD / MediaPlayer',w:366,h:34,x:.5,y:.055,on:true,island:true},
  keys:{name:'Keystrokes',w:55,h:55,x:.18,y:.31,on:true,parts:[keyPart('W',19,0),keyPart('A',0,19),keyPart('S',19,19),keyPart('D',38,19),keyPart('',19,38),keyPart('',0,38,17,'L'),keyPart('',38,38,17,'R')]},
  potions:{name:'Potions',w:123,h:56,x:.96,y:.32,on:true,parts:effects,art:[art(2,9,25,8,true),art(2,9,40,8)]},
  target:{name:'TargetHud',w:152,h:45,x:.24,y:.64,on:true,parts:targetScene(),art:[art('face',6,6,28),...[7,8,9,10,3,5].map((id,i)=>art(id,39+10*i,28,9))]},
  nearby:{name:'BetterNear',w:127,h:61,x:.035,y:.43,on:true,parts:near},
  inventory:{name:'InventoryHUD',w:178,h:64,x:.94,y:.83,on:true,parts:inventory,art:[3,4,5,6].map((id,i)=>art(id,5+i*19,5,16))},
  football:{name:'Football · счёт',w:67,h:18,x:.5,y:.32,on:false,parts:score('Голы: 2',widgetGlyphs.football)},
  basketball:{name:'Basketball · счёт',w:67,h:18,x:.5,y:.4,on:false,parts:score('Счёт: 4',widgetGlyphs.basketball)},
  scoreboard:{name:'Scoreboard · контекст',w:84,h:27,x:1,y:.5,fixed:true,parts:[
    box(0,0,84,9,0,'#00000066'),box(0,9,84,18,0,'#0000004d'),
    label('Выживание',2,0,80,9,7,'white','center'),
    label('Онлайн',2,9,56,9,7,'white'),label('37',59,9,23,9,7,'#ff5555','right'),
    label('Сезон',2,18,56,9,7,'white'),label('2',59,18,23,9,7,'#ff5555','right')]},
  hotbar:{name:'Хотбар Minecraft · контекст',w:182,h:22,x:.5,y:1,fixed:true,parts:[
    box(0,0,182,22,0,'#171717c7'),
    ...Array.from({length:9},(_,i)=>box(2+i*20,2,18,18,0,'#555b5b8c')),
    part('box',1,1,20,20,{color:'transparent',border:'#dedede'})],
    art:[3,4,5,6].map((id,i)=>art(id,3+i*20,3,16))},
  chat:{name:'Уведомление чата',w:286,h:76,x:.99,y:.99,fixed:true,template:'chat-notification.html',
    state:{title:'Quartz',detail:'Готов зайти в мир?'}},
  invite:{name:'Приглашение в мир',w:306,h:70,x:.99,y:.08,fixed:true,template:'world-invite.html',
    state:{title:'Quartz приглашает в мир',world:'Летний мир · Minecraft 26.3',hint:'Удерживай Y, чтобы принять',
      key:true,keyboard:true,keyLabel:'Y',initial:'Q',remaining:.75,hold:0,opacity:1,offset:0}}
};
const previewTheme=`
  html:root[data-local-hud] {
    --accent: var(--preview-accent) !important;
    --text: var(--preview-ink);
    --muted: var(--preview-muted);
    --line: var(--preview-edge);
    --top: var(--preview-surface);
    --surface: var(--preview-surface);
    --hud-panel: var(--preview-glass);
    --hud-glass: var(--preview-glass);
    --hud-selected: var(--preview-accent-soft);
  }
  .panel, .surface, .toast, .invite { box-shadow: inset 0 0 0 1px var(--line); }
  .key { border-radius: 3px !important; }
  .key[data-pressed] { border-color: var(--preview-accent-line); color: var(--text); }
  .timer { background: var(--hud-selected); color: var(--accent); font-size: 6.4px; }
  .timer > i { width: 3px; height: 3px; }
  #island .surface { border-radius: 21px !important; }
  #island .shoulder { display: none; }
  #island .metric > span:first-child { font-weight: 700; color: var(--accent); }
  .key .mouse .ui-icon {
    display: block; fill: none; stroke: currentColor; stroke-width: 1.7;
    stroke-linecap: round; stroke-linejoin: round;
    width: 10px; height: 10px;
  }
  .invite {
    width: calc(100% - 4px); height: 66px; margin: 2px;
    padding: 9px 51px 10px 11px;
    display: flex; align-items: center;
    border-radius: 14px;
  }
  .invite .main { gap: 9px; width: 100%; min-width: 0; }
  .invite .face {
    width: 32px; height: 32px; border-radius: 7px;
    border: 1px solid var(--preview-accent-line);
    background: #182322 url('/tools/launcher-default-skin.png') -29px -29px / 240px 240px;
    image-rendering: pixelated;
  }
  .invite .face #initial { display: none; }
  .invite #title { font-size: 12px; line-height: 15px; }
  .invite #world { color: var(--muted); font-size: 10.5px; line-height: 13px; margin-top: 1px; }
  .invite #hint { color: var(--accent); font-size: 10px; line-height: 13px; margin-top: 1px; }
  .invite .hold-key {
    top: 20px; right: 19px; width: 26px; height: 26px; border-radius: 50%;
    border-color: #f5fffa33; background: #f0fffa10; color: #f0f9f5; font-size: 11px; font-weight: 700;
    transition: border-color 150ms ease, box-shadow 150ms ease;
  }
  .invite[data-accepted] .hold-key {
    border-color: var(--accent); box-shadow: 0 0 9px var(--preview-accent-soft);
  }
  .invite #fill { background: var(--accent); opacity: .48; }
  .invite .countdown { left: 13px; right: 13px; bottom: 5px; height: 1px; }
  .invite #remaining { height: 1px; background: var(--accent); }
  @media (prefers-reduced-motion: reduce) {
    .key, .invite .hold-key { transition: none; }
  }
`;
const editorTheme=getComputedStyle(document.documentElement);
const previewColors=['accent','ink','muted','edge','surface','glass','accent-soft','accent-line'];
const reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
let invitePreview;
let inviteDismissed=false;

function setupInvitePreview(doc,view,state){
  doc.querySelector('.dismiss').hidden=true;
  invitePreview={view,state,card:doc.getElementById('card')};
}

let inviteHoldFrame=0;
function stopInviteHold(){
  if(!inviteHoldFrame)return;
  cancelAnimationFrame(inviteHoldFrame);
  inviteHoldFrame=0;
  invitePreview.card.removeAttribute('data-accepted');
  invitePreview.view.update(invitePreview.state);
}

document.addEventListener('keydown',event=>{
  if(event.code!=='KeyY'||!viewing||inviteDismissed||!models.invite.on||!invitePreview||event.repeat
     ||event.altKey||event.ctrlKey||event.metaKey||inviteHoldFrame)return;
  const started=performance.now();
  const {view,state,card}=invitePreview;
  function tick(now){
    const hold=clamp((now-started)/830,0,1);
    if(!viewing||!models.invite.on){stopInviteHold();return;}
    view.update({...state,hold,hint:hold===1?'Принято':state.hint});
    if(hold===1)card.setAttribute('data-accepted','');
    else inviteHoldFrame=requestAnimationFrame(tick);
  }
  inviteHoldFrame=requestAnimationFrame(tick);
});
document.addEventListener('keyup',event=>{if(event.code==='KeyY')stopInviteHold();});
window.addEventListener('blur',stopInviteHold);

function mountScene(el,def,track=false){
  const unit=def.island?.85:def.template?1:1.32;
  const surface=document.createElement('div');
  el.querySelector('.scene')?.remove();
  el.style.width=def.w*unit+'px';el.style.height=def.h*unit+'px';
  surface.className='scene';surface.style.cssText='width:'+def.w+'px;height:'+def.h+'px;transform:scale('+unit+')';
  const frame=document.createElement('iframe');
  frame.title=def.name;frame.tabIndex=-1;
  frame.width=def.island?392:def.w;frame.height=def.island?222:def.h;
  if(def.island)frame.style.left='-13px';
  frame.onload=()=>{
    const doc=frame.contentDocument;
    doc.documentElement.style.colorScheme='dark';
    doc.documentElement.setAttribute('data-local-hud','');
    for(const color of previewColors){
      doc.documentElement.style.setProperty('--preview-'+color,editorTheme.getPropertyValue('--'+color));
    }
    const style=doc.createElement('style');
    style.textContent=el.dataset.key==='scoreboard'?'.label{font-family:Consolas,monospace;text-shadow:1px 1px #222}':previewTheme;
    doc.head.append(style);
    const state=def.island?{hud:true,version:'0.2.39',username:'Quartz',fps:144,ping:24,scale:1,animated:false,placement:'TOP_CENTER',
      accent:editorTheme.getPropertyValue('--accent').trim(),
      media:track,active:track,playing:track,title:'Midnight City',artist:'M83',source:'Spotify',position:83,duration:244}
      :def.state||{parts:def.parts};
    const view=frame.contentWindow.paradox;
    if(def.template==='world-invite.html')setupInvitePreview(doc,view,state);
    view.update(state);
    if(def.template==='chat-notification.html')view.opening(1);
    if(!reducedMotion.matches&&!el.hidden&&!el.classList.contains('inactive')){
      surface.animate([{opacity:0,translate:'0 5px'},{opacity:1,translate:'0 0'}],
        {duration:220,easing:'cubic-bezier(.2,.75,.25,1)'});
    }
  };
  frame.src='/editor-live/'+(def.island?'island/':def.template||'widgets.html');
  surface.append(frame);
  for(const {image,x,y,size,muted} of def.art||[]){
    const overlay=document.createElement('span');
    overlay.className='art';overlay.style.cssText='left:'+x+'px;top:'+y+'px;width:'+size+'px;height:'+size+'px';
    if(muted)overlay.style.filter='grayscale(1)';
    if(image==='face'){
      overlay.style.backgroundImage="url('launcher-default-skin.png')";
      overlay.style.backgroundSize=(size*8)+'px';overlay.style.backgroundPosition=(-size)+'px '+(-size)+'px';
    }else{
      const original=image<3?18:16, zoom=size/original;
      overlay.style.backgroundImage="url('hud-editor-items.png')";
      overlay.style.backgroundSize=(220*zoom)+'px '+(20*zoom)+'px';
      overlay.style.backgroundPosition=(-(image*20+1)*zoom)+'px '+(-zoom)+'px';
    }
    surface.append(overlay);
  }
  el.prepend(surface);
}
const key='gleam-hud-editor-v2';
let saved;
try { saved=JSON.parse(localStorage.getItem(key)) || {}; } catch { saved={}; }
const num=(v,f,min,max)=>Number.isFinite(v)?clamp(v,min,max):f;
let snap=saved.snap!==false, step=[8,16,24,32].includes(saved.step)?saved.step:16;
let selected=null, viewing=false, drag=null, previousPositions=null;
const models={};
for(const [id,def] of Object.entries(catalog)){
  const {name,x,y,on=false}=def;
  const s=saved.widgets?.[id] || {};
  const model=models[id]={
    x:def.fixed?x:num(s.x,x,0,1),
    y:def.fixed?y:num(s.y,y,0,1),
    scale:def.fixed?1:num(s.scale,1,.35,4),
    on:typeof s.on==='boolean'?s.on:on
  };
  const el=document.createElement('div');
  el.className='widget';el.id='widget-'+id;el.dataset.key=id;
  el.setAttribute('aria-label',name);
  if(def.fixed){
    el.classList.add('fixed-widget');
    if(id==='invite'){
      el.innerHTML='<button class="invite-dismiss" aria-label="Закрыть приглашение" hidden>'+uiIcon('close')+'</button>';
      el.querySelector('button').onclick=()=>{
        inviteDismissed=true;stopInviteHold();place(id);$('preview').focus();
      };
    }
  }
  else{
    el.tabIndex=0;
    el.innerHTML='<div class="widget-tag"><span class="tag-name">'+uiIcon(id)+'<span></span></span><button class="toggle-widget"></button></div>'
      +'<button class="handle" title="Изменить размер" aria-label="Изменить размер"></button>';
    const tag=el.querySelector('.widget-tag');
    tag.querySelector('.tag-name > span').textContent=name;
    tag.lastElementChild.onclick=()=>{model.on=!model.on;clear();place(id);updateCount();save();};
  }
  if(def.island)model.track=s.track===true;
  mountScene(el,model.track?{...def,h:57}:def,model.track);
  layers.append(el);
}
function save(){
  try {localStorage.setItem(key,JSON.stringify({widgets:models,step,snap}));$('saved').textContent='●　Сохранено локально';}
  catch {$('saved').textContent='Сохранение недоступно';}
}
function geometry(id){
  const el=$('widget-'+id), m=models[id];
  const base=clamp(Math.min(stage.clientWidth/960,stage.clientHeight/540),.55,1);
  const scale=Math.min(m.scale*base,stage.clientWidth/el.offsetWidth,stage.clientHeight/el.offsetHeight);
  return {base,scale,w:Math.max(0,stage.clientWidth-el.offsetWidth*scale),h:Math.max(0,stage.clientHeight-el.offsetHeight*scale)};
}
const overlayTransitions=new WeakMap();
function setWidgetVisible(el,visible,fixed){
  const next=String(visible), previous=el.dataset.visible;
  if(previous===next)return;
  el.dataset.visible=next;
  if(!fixed||previous===undefined||reducedMotion.matches){
    overlayTransitions.get(el)?.cancel();
    overlayTransitions.delete(el);
    el.hidden=!visible;
    return;
  }
  const transition=overlayTransitions.get(el);
  const style=transition?getComputedStyle(el):null;
  const from=style?{opacity:style.opacity,translate:style.translate}
    :{opacity:visible?0:1,translate:visible?'0 -6px':'0 0'};
  transition?.cancel();
  el.hidden=false;
  const animation=el.animate([from,{opacity:visible?1:0,translate:visible?'0 0':'0 -5px'}],
    {duration:visible?230:145,easing:'cubic-bezier(.2,.75,.25,1)',fill:'forwards'});
  overlayTransitions.set(el,animation);
  animation.onfinish=()=>{
    if(overlayTransitions.get(el)!==animation)return;
    el.hidden=!visible;
    overlayTransitions.delete(el);
    animation.cancel();
  };
}
function place(id){
  const m=models[id],el=$('widget-'+id);
  const fixed=catalog[id].fixed;
  const visible=(m.on||(!fixed&&!viewing))&&!(id==='invite'&&viewing&&inviteDismissed);
  setWidgetVisible(el,visible,fixed);
  if(id==='invite')el.querySelector('.invite-dismiss').hidden=!viewing||!visible;
  if(!visible)return;
  el.classList.toggle('inactive',!m.on);
  if(!fixed){
    const toggle=el.querySelector('.toggle-widget');
    const action=m.on?'Скрыть':'Показать';
    toggle.innerHTML=uiIcon(m.on?'close':'add');
    toggle.setAttribute('aria-label',action+' '+catalog[id].name);
    toggle.title=action;
  }
  const {scale,w,h}=geometry(id);
  el.style.transform='translate('+Math.round(m.x*w)+'px,'+Math.round(m.y*h)+'px) scale('+scale+')';
}
function redraw(){Object.keys(models).forEach(place);}
function mode(){
  stage.classList.toggle('free',!snap);stage.style.setProperty('--step',step+'px');
  $('snap').classList.toggle('on',snap);$('free').classList.toggle('on',!snap);
  $('snap').setAttribute('aria-pressed',snap);$('free').setAttribute('aria-pressed',!snap);
  $('step').value=step;
}
function updateCount(){
  $('library').title='Виджеты · '+Object.keys(models).filter(id=>models[id].on&&!catalog[id].fixed).length+' на экране';
}
function updateCoordinates(id){
  if(!$('pos-x'))return;
  const g=geometry(id),model=models[id];
  $('pos-x').value=Math.round(model.x*g.w);$('pos-y').value=Math.round(model.y*g.h);
}
function clear(){
  if(selected)$('widget-'+selected).classList.remove('selected');
  selected=null;panel.hidden=true;panel.classList.remove('panel-opening');$('library').setAttribute('aria-expanded','false');
}
function openPanel(title,content){
  $('layout').open=false;
  panel.innerHTML='<h2>'+title+'<button class="close" aria-label="Закрыть">'+uiIcon('close')+'</button></h2>'+content;
  panel.hidden=false;panel.classList.add('panel-opening');panel.querySelector('.close').onclick=clear;
}
function inspect(id){
  clear();selected=id;$('widget-'+id).classList.add('selected');
  const def=catalog[id],model=models[id];
  openPanel(def.name,'<label>Масштаб <output id="pct"></output><input id="scale" type="range" min="35" max="400" step="1"></label>'
    +'<div class="coords"><label>X, px <input id="pos-x" type="number" min="0" step="1"></label>'
    +'<label>Y, px <input id="pos-y" type="number" min="0" step="1"></label></div>'
    +(def.island?'<label><input id="track" type="checkbox"> Показать текущий трек</label>':'')
    +'<button class="remove">Скрыть виджет</button>');
  if(def.island){
    $('track').checked=model.track;
    $('track').onchange=e=>{
      model.track=e.target.checked;
      mountScene($('widget-'+id),model.track?{...def,h:57}:def,model.track);
      place(id);save();
    };
  }
  function scaleLabel(){const n=Math.round(models[id].scale*100);$('pct').value=n+'%';$('scale').value=n;}
  scaleLabel();updateCoordinates(id);
  $('scale').oninput=e=>{model.scale=e.target.value/100;place(id);scaleLabel();updateCoordinates(id);save();};
  for(const axis of ['x','y']){
    const field=$('pos-'+axis);
    field.oninput=e=>{
      if(!Number.isFinite(e.target.valueAsNumber))return;
      const g=geometry(id),limit=axis==='x'?g.w:g.h;
      model[axis]=clamp(e.target.valueAsNumber/(limit||1),0,1);
      place(id);save();
    };
    field.onblur=()=>updateCoordinates(id);
    field.onkeydown=e=>{if(e.key==='Enter')field.blur();};
  }
  panel.querySelector('.remove').onclick=()=>{model.on=false;clear();place(id);updateCount();save();};
}
function library(){
  const wasOpen=$('library').getAttribute('aria-expanded')==='true';clear();if(wasOpen)return;
  $('library').setAttribute('aria-expanded','true');
  const entries=Object.entries(catalog);
  const buttons=items=>items.map(([id,{name}])=>'<button class="option" data-id="'+id+'" aria-pressed="'+models[id].on+'">'
    +'<span class="tag-name">'+uiIcon(id)+name+'</span><span>'+uiIcon(models[id].on?'check':'add')+'</span></button>').join('');
  openPanel('Виджеты',buttons(entries.filter(([,def])=>!def.fixed))
    +'<h3>Экранные оверлеи</h3>'+buttons(entries.filter(([,def])=>def.fixed)));
  panel.querySelectorAll('.option').forEach(btn=>btn.onclick=()=>{
    const id=btn.dataset.id;models[id].on=!models[id].on;place(id);updateCount();save();
    btn.setAttribute('aria-pressed',models[id].on);btn.lastElementChild.innerHTML=uiIcon(models[id].on?'check':'add');
  });
}
const alignmentDistance=7;

function widgetBounds(id, origin){
  const rect=$('widget-'+id).getBoundingClientRect();
  return {x:rect.left-origin.left,y:rect.top-origin.top,width:rect.width,height:rect.height};
}

function alignmentPeers(id, origin){
  return Object.keys(models)
    .filter(other=>other!==id&&models[other].on)
    .map(other=>widgetBounds(other,origin));
}

function findAlignment(position, size, limit, peers, axis){
  const dimension=axis==='x'?'width':'height';
  const anchors=[0,.5,1];
  let best=null;
  let nearest=alignmentDistance;
  const extent=limit+size;
  for(const fraction of anchors){
    const snapped=fraction*limit,distance=Math.abs(snapped-position);
    if(distance<=nearest){
      nearest=distance;best={position:snapped,line:fraction*extent,peer:null,sameAnchor:true};
    }
  }
  // Prefer matching edges or centers when candidates are equally close.
  for(const fraction of anchors){
    for(const peer of peers){
      for(const other of anchors){
        if(other!==fraction&&(other===.5||fraction===.5))continue;
        const line=peer[axis]+peer[dimension]*other;
        const snapped=line-size*fraction;
        if(snapped<-.5||snapped>limit+.5)continue;
        const distance=Math.abs(snapped-position);
        const sameAnchor=fraction===other;
        if(distance>nearest||(best&&distance===nearest&&(!sameAnchor||best.sameAnchor)))continue;
        nearest=distance;
        best={position:clamp(snapped,0,limit),line,peer,sameAnchor};
      }
    }
  }
  return best;
}

function showAlignment(xMatch,yMatch,left,top,width,height){
  const guides=$('alignment'),vertical=$('guide-x'),horizontal=$('guide-y');
  guides.hidden=!xMatch&&!yMatch;
  vertical.hidden=!xMatch;
  horizontal.hidden=!yMatch;
  if(xMatch){
    const {peer,line}=xMatch;
    const start=peer?Math.max(0,Math.min(top,peer.y)-10):0,end=peer?Math.max(top+height,peer.y+peer.height)+10:stage.clientHeight;
    vertical.style.cssText='left:'+Math.round(line)+'px;top:'+start+'px;height:'+(end-start)+'px';
  }
  if(yMatch){
    const {peer,line}=yMatch;
    const start=peer?Math.max(0,Math.min(left,peer.x)-10):0,end=peer?Math.max(left+width,peer.x+peer.width)+10:stage.clientWidth;
    horizontal.style.cssText='top:'+Math.round(line)+'px;left:'+start+'px;width:'+(end-start)+'px';
  }
}

layers.onpointerdown=e=>{
  const el=e.target.closest('.widget:not(.fixed-widget):not(.inactive)');
  if(!el||viewing||e.button!==0||drag||e.target.closest('.toggle-widget'))return;
  const id=el.dataset.key,m=models[id],g=geometry(id);
  const resize=!!e.target.closest('.handle');
  if(selected!==id)clear();
  el.classList.add('selected');selected=id;
  const origin=stage.getBoundingClientRect();
  const bounds=widgetBounds(id,origin);
  drag={id,pointer:e.pointerId,x:e.clientX,y:e.clientY,left:bounds.x,top:bounds.y,width:bounds.width,height:bounds.height,
    peers:resize?[]:alignmentPeers(id,origin),base:g.base,scale:m.scale,resize,moved:false};
  stage.classList.toggle('moving',snap&&!e.altKey);
  el.setPointerCapture(e.pointerId);e.preventDefault();
};
layers.onpointermove=e=>{
  if(!drag||e.pointerId!==drag.pointer)return;
  const d=drag,id=d.id,m=models[id],el=$('widget-'+id);
  const dx=e.clientX-d.x,dy=e.clientY-d.y;
  if(!d.moved&&Math.hypot(dx,dy)<3)return;
  d.moved=true;
  if(d.resize){
    m.scale=clamp(d.scale+(dx*el.offsetWidth+dy*el.offsetHeight)/(d.base*(el.offsetWidth**2+el.offsetHeight**2)),.35,4);
    if($('scale')){$('scale').value=Math.round(m.scale*100);$('pct').value=$('scale').value+'%';}
  }
  const g=geometry(id),magnetic=snap&&!e.altKey&&!d.resize;
  stage.classList.toggle('moving',magnetic);
  const left=d.left+(d.resize?0:dx),top=d.top+(d.resize?0:dy);
  const xMatch=magnetic?findAlignment(left,d.width,g.w,d.peers,'x'):null;
  const yMatch=magnetic?findAlignment(top,d.height,g.h,d.peers,'y'):null;
  const x=xMatch?.position??(magnetic?Math.round(left/step)*step:left);
  const y=yMatch?.position??(magnetic?Math.round(top/step)*step:top);
  m.x=clamp(x/(g.w||1),0,1);
  m.y=clamp(y/(g.h||1),0,1);
  place(id);
  updateCoordinates(id);
  showAlignment(xMatch,yMatch,Math.round(m.x*g.w),Math.round(m.y*g.h),d.width,d.height);
};
function finish(e){
  if(!drag||e.pointerId!==drag.pointer)return;
  const d=drag;drag=null;stage.classList.remove('moving');$('alignment').hidden=true;
  if(d.moved)save();else if(e.type==='pointerup')inspect(d.id);
}
layers.onpointerup=finish;layers.onpointercancel=finish;layers.onlostpointercapture=finish;
layers.oncontextmenu=e=>{
  const widget=e.target.closest('.widget:not(.fixed-widget):not(.inactive)');
  if(!viewing&&widget){e.preventDefault();inspect(widget.dataset.key);}
};
layers.onkeydown=e=>{if(e.key==='Enter'&&!viewing&&e.target.matches('.widget:not(.inactive)'))inspect(e.target.dataset.key);};
stage.onpointerdown=e=>{if(!e.target.closest('.widget,aside'))clear();};
$('library').onclick=library;
$('snap').onclick=()=>{snap=true;mode();save();};$('free').onclick=()=>{snap=false;mode();save();};
$('step').onchange=e=>{step=Number(e.target.value);mode();save();};
$('arrange').onclick=()=>{
  clear();
  const movable=Object.keys(models).filter(id=>!catalog[id].fixed);
  if(previousPositions){
    for(const id of movable)Object.assign(models[id],previousPositions[id]);
    previousPositions=null;
  }else{
    previousPositions={};
    for(const id of movable){
      const model=models[id];
      previousPositions[id]={x:model.x,y:model.y};
      model.x=catalog[id].x;model.y=catalog[id].y;
    }
  }
  $('arrange').textContent=previousPositions?'Отменить':'Разложить';
  $('layout').open=false;redraw();save();
};
function preview(){
  stopInviteHold();
  clear();viewing=!viewing;inviteDismissed=false;document.body.classList.toggle('view',viewing);
  for(const widget of layers.children)widget.inert=viewing&&widget.dataset.key!=='invite';
  redraw();
  $('library').hidden=viewing;
  $('preview').innerHTML=uiIcon(viewing?'edit':'eye')+(viewing?'Вернуться к редактированию':'Просмотр');
  $('preview').setAttribute('aria-pressed',viewing);
}
$('preview').onclick=preview;
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(viewing)preview();else clear();}});
new ResizeObserver(()=>{
  redraw();
  if(selected)updateCoordinates(selected);
  if(drag&&!drag.resize){
    const origin=stage.getBoundingClientRect();
    const bounds=widgetBounds(drag.id,origin);
    drag.width=bounds.width;drag.height=bounds.height;
    drag.peers=alignmentPeers(drag.id,origin);
    $('alignment').hidden=true;
  }
}).observe(stage);
$('snap').insertAdjacentHTML('afterbegin',uiIcon('grid'));
$('free').insertAdjacentHTML('afterbegin',uiIcon('move'));
$('library').innerHTML=uiIcon('add');
$('layout').querySelector('summary').innerHTML=uiIcon('more');
$('preview').insertAdjacentHTML('afterbegin',uiIcon('eye'));
mode();redraw();updateCount();
