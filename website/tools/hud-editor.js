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
  check:'m4 10 4 4 8-8'
};
const widgetGlyphs={
  status:'M2 10h4l2-6 4 12 2-6h4',
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
const art=(image,x,y,size)=>({image,x,y,size});
const health='#a892ff', muted='var(--muted)';
function targetScene(){
  return [panelPart(164,59,8),well(7,7,24,24,5),
    label('Quartz',37,6,61,14,10,'var(--text)','left',true),
    label('18 / 20',103,6,38,14,9,health,'right',true),label('+2',142,6,15,14,7,'#e5ba76','right'),
    label('3.8 m',37,20,40,10,7,muted),label('24 ms',112,20,45,10,7,muted,'right'),
    box(7,36,150,4,2,'#ffffff20'),box(7,36,135,4,2,'#8967ef'),box(142,36,15,4,2,'#e5ba76')];
}
const effects=[panelPart(110,53,5),
  label('Эффекты',8,2,94,17,8,'var(--text)','left',true),rule(7,19,96)];
for(const [index,[name,time]] of [['Скорость 2','1:42'],['Сила 1','0:38']].entries()){
  const y=21+14*index;
  effects.push(label(name,19,y,63,13,7),part('timer',79,y+1,23,11,{text:time}));
}
const near=[panelPart(127,61,5),label('Игроки рядом',8,3,96,17,8,'var(--text)','left',true),
  rule(8,22,111),label('2',105,4,14,15,7,'var(--accent)','right',true)];
for(const [i,[name,distance]] of [['Quartz','8'],['Nova','21']].entries()){
  const y=25+i*16;
  if(i)near.push(rule(7,y-1,113));
  near.push(icon(i?'m5 11 5-5 5 5M10 6v9':'m7 6 7 0v7M14 6l-8 8',9,y+2,11),label(name,24,y,65,15,8),
    label(distance,80,y,28,15,8,'var(--text)','right',true),label('m',110,y,7,15,6.5,muted));
}
const inventory=[panelPart(178,80,5),label('Инвентарь',6,1,120,15,8,'var(--text)','left',true),
  rule(5,16,168),...Array.from({length:27},(_,i)=>box(4+i%9*19,20+Math.floor(i/9)*19,18,18,2,'#ffffff10'))];
function score(text){return [panelPart(67,18,4),box(2,4,2,10,1,'var(--accent)'),label(text,7,0,55,18,8,'var(--text)','center',true)];}
const catalog={
  status:{name:'HUD / MediaPlayer',w:366,h:34,x:.5,y:.055,on:true,island:true},
  keys:{name:'Keystrokes',w:55,h:55,x:.18,y:.31,on:true,parts:[keyPart('W',19,0),keyPart('A',0,19),keyPart('S',19,19),keyPart('D',38,19),keyPart('',19,38,36),keyPart('',0,38,17,'L')]},
  potions:{name:'Potions',w:110,h:53,x:.96,y:.32,on:true,parts:effects,art:[art(0,8,24,8),art(1,8,38,8)]},
  target:{name:'TargetHud',w:164,h:59,x:.24,y:.64,on:true,parts:targetScene(),art:[art('face',8,8,22),...[7,8,9,10,3,5].map((id,i)=>art(id,9+25*i,43,13))]},
  nearby:{name:'BetterNear',w:127,h:61,x:.035,y:.43,on:true,parts:near},
  inventory:{name:'InventoryHUD',w:178,h:80,x:.94,y:.83,on:true,parts:inventory,art:[3,4,5,6].map((id,i)=>art(id,5+i*19,21,16))},
  football:{name:'Football · счёт',w:67,h:18,x:.5,y:.32,on:false,parts:score('Голы: 2')},
  basketball:{name:'Basketball · счёт',w:67,h:18,x:.5,y:.4,on:false,parts:score('Счёт: 4')},
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
  invite:{name:'Приглашение в мир',w:328,h:79,x:.99,y:.01,fixed:true,template:'world-invite.html',
    state:{title:'Quartz приглашает в мир',world:'Летний мир · Minecraft 26.3',hint:'Удерживай клавишу, чтобы принять',
      key:true,keyboard:true,keyLabel:'Y',initial:'Q',remaining:.75,hold:0,opacity:1,offset:0}}
};
const previewTheme=`
  html:root {
    --accent: #ab95ff !important;
    --text: #f4f1ff;
    --muted: #bbb3cb;
    --line: #e9ddff20;
    --top: #191826;
    --surface: #191826;
    --hud-panel: #171624de;
    --hud-glass: #171624b8;
    --hud-selected: #8e69ee42;
  }
  .panel, .surface, .toast, .invite { box-shadow: inset 0 0 0 1px #dfd1ff1b; }
  .key { border-radius: 3px !important; }
  .key[data-pressed] { border-color: #a184f9; color: #f4edff; }
  .timer { background: #ac8bff29; color: #eee5ff; font-size: 6.4px; }
  .timer > i { width: 3px; height: 3px; }
  #island .surface { border-radius: 21px !important; }
  #island .shoulder { display: none; }
  #island .brand img { filter: drop-shadow(0 0 4px #8967ef); }
  #island .metric > span:first-child { font-weight: 700; color: var(--accent); }
`;
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
    const style=doc.createElement('style');
    style.textContent=el.dataset.key==='scoreboard'?'.label{font-family:Consolas,monospace;text-shadow:1px 1px #222}':previewTheme;
    doc.head.append(style);
    const state=def.island?{hud:true,version:'0.2.39',username:'Quartz',fps:144,ping:24,scale:1,animated:false,placement:'TOP_CENTER',
      media:track,active:track,playing:track,title:'Midnight City',artist:'M83',source:'Spotify',position:83,duration:244}
      :def.state||{parts:def.parts};
    const view=frame.contentWindow.paradox;
    view.update(state);
    if(def.template==='chat-notification.html')view.opening(1);
  };
  frame.src='/editor-live/'+(def.island?'island/':def.template||'widgets.html');
  surface.append(frame);
  for(const {image,x,y,size} of def.art||[]){
    const overlay=document.createElement('span');
    overlay.className='art';overlay.style.cssText='left:'+x+'px;top:'+y+'px;width:'+size+'px;height:'+size+'px';
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
  if(def.fixed){el.classList.add('fixed-widget');}
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
  const base=Math.min(1,Math.max(.55,stage.clientWidth/640));
  const scale=Math.min(m.scale*base,stage.clientWidth/el.offsetWidth,stage.clientHeight/el.offsetHeight);
  return {base,scale,w:Math.max(0,stage.clientWidth-el.offsetWidth*scale),h:Math.max(0,stage.clientHeight-el.offsetHeight*scale)};
}
function place(id){
  const m=models[id],el=$('widget-'+id);
  const fixed=catalog[id].fixed;
  el.hidden=!m.on&&(fixed||viewing);
  if(el.hidden)return;
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
  selected=null;panel.hidden=true;$('library').setAttribute('aria-expanded','false');
}
function openPanel(title,content){
  $('layout').open=false;
  panel.innerHTML='<h2>'+title+'<button class="close" aria-label="Закрыть">'+uiIcon('close')+'</button></h2>'+content;
  panel.hidden=false;panel.querySelector('.close').onclick=clear;
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
layers.onpointerdown=e=>{
  const el=e.target.closest('.widget:not(.fixed-widget):not(.inactive)');
  if(!el||viewing||e.button!==0||drag||e.target.closest('.toggle-widget'))return;
  const id=el.dataset.key,m=models[id],g=geometry(id);
  const resize=!!e.target.closest('.handle');
  if(selected!==id)clear();
  el.classList.add('selected');selected=id;
  drag={id,pointer:e.pointerId,x:e.clientX,y:e.clientY,left:m.x*g.w,top:m.y*g.h,base:g.base,scale:m.scale,resize,moved:false};
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
  const g=geometry(id),pos=v=>snap&&!e.altKey&&!d.resize?Math.round(v/step)*step:v;
  m.x=clamp(pos(d.left+(d.resize?0:dx))/(g.w||1),0,1);
  m.y=clamp(pos(d.top+(d.resize?0:dy))/(g.h||1),0,1);place(id);
  updateCoordinates(id);
  const guides=$('alignment');
  guides.hidden=d.resize||!snap||e.altKey;
  guides.style.setProperty('--x',Math.round(m.x*g.w)+'px');
  guides.style.setProperty('--y',Math.round(m.y*g.h)+'px');
};
function finish(e){
  if(!drag||e.pointerId!==drag.pointer)return;
  const d=drag;drag=null;$('alignment').hidden=true;
  if(d.moved)save();else if(e.type==='pointerup')inspect(d.id);
}
layers.onpointerup=finish;layers.onpointercancel=finish;layers.onlostpointercapture=finish;
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
  clear();viewing=!viewing;document.body.classList.toggle('view',viewing);layers.inert=viewing;
  redraw();
  $('library').hidden=viewing;
  $('preview').innerHTML=uiIcon(viewing?'edit':'eye')+(viewing?'Вернуться к редактированию':'Просмотр');
  $('preview').setAttribute('aria-pressed',viewing);
}
$('preview').onclick=preview;
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(viewing)preview();else clear();}});
new ResizeObserver(()=>{redraw();if(selected)updateCoordinates(selected);}).observe(stage);
$('snap').insertAdjacentHTML('afterbegin',uiIcon('grid'));
$('free').insertAdjacentHTML('afterbegin',uiIcon('move'));
$('library').innerHTML=uiIcon('add');
$('layout').querySelector('summary').innerHTML=uiIcon('more');
$('preview').insertAdjacentHTML('afterbegin',uiIcon('eye'));
mode();redraw();updateCount();
