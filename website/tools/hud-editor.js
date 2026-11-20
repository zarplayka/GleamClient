'use strict';
const $ = id => document.getElementById(id);
const stage = $('stage'), panel = $('panel'), layers = $('layers');
const clamp = (n,min,max) => Math.max(min,Math.min(max,n));
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
const health='#a6dfaa', muted='var(--muted)';
function targetScene(){
  return [panelPart(166,68,5),well(7,7,30,30,4),label('Quartz',43,5,81,16,11,'var(--text)','left',true),
    label('24 ms',124,5,34,16,7,muted,'right'),label('18 / 20',43,21,34,12,8,health,'left',true),
    label('+2',79,21,18,12,7,'#efc887'),label('3.8 m',116,21,42,12,7.5,muted,'right'),
    box(8,42,150,3,1,'#ffffff24'),box(8,42,135,3,1,health),box(143,42,15,3,1,'#efc887'),
    ...Array.from({length:6},(_,i)=>box(9+i*25,49,23,15,2,'#ffffff0a'))];
}
const effects=[panelPart(110,53,5),label('Эффекты',23,2,73,17,8,'var(--text)','left',true),rule(7,19,96)];
for(const [index,[name,time]] of [['Скорость 2','1:42'],['Сила 1','0:38']].entries()){
  const y=21+14*index;
  effects.push(label(name,19,y,63,13,7),part('timer',79,y+1,23,11,{text:time}));
}
const near=[panelPart(127,61,5),icon('M2 10h16M10 2v16M10 5a5 5 0 1 0 .01 0Z',8,6,10),label('Игроки рядом',23,3,81,17,8,'var(--text)','left',true),
  rule(8,22,111),label('2',105,4,14,15,7,'var(--accent)','right',true)];
for(const [i,[name,distance]] of [['Quartz','8'],['Nova','21']].entries()){
  const y=25+i*16;
  if(i)near.push(rule(7,y-1,113));
  near.push(icon(i?'M10 2l6 14-6-3-6 3Z':'M16 4l-4 14-2-6-6-2Z',11,y+3,9,true),label(name,24,y,65,15,8),
    label(distance,80,y,28,15,8,'var(--text)','right',true),label('m',110,y,7,15,6.5,muted));
}
const inventory=[panelPart(178,80,5),label('Инвентарь',6,1,120,15,8,'var(--text)','left',true),
  rule(5,16,168),...Array.from({length:27},(_,i)=>box(4+i%9*19,20+Math.floor(i/9)*19,18,18,2,'#ffffff10'))];
function score(text){return [panelPart(67,18,4),box(2,4,2,10,1,'var(--accent)'),label(text,7,0,55,18,8,'var(--text)','center',true)];}
const catalog={
  status:{name:'HUD / MediaPlayer',w:366,h:34,x:.5,y:0,on:true,island:true},
  keys:{name:'Keystrokes',w:55,h:55,x:.025,y:.84,on:true,parts:[keyPart('W',19,0),keyPart('A',0,19),keyPart('S',19,19),keyPart('D',38,19),keyPart('',19,38,36),keyPart('',0,38,17,'L')]},
  potions:{name:'Potions',w:110,h:53,x:.97,y:.19,on:true,parts:effects,art:[art('potion',8,6,9),art(0,8,24,8),art(1,8,38,8)]},
  target:{name:'TargetHud',w:166,h:68,x:.58,y:.61,on:true,parts:targetScene(),art:[art('face',8,8,28),...[7,8,9,10,3,5].map((id,i)=>art(id,14+25*i,50,13))]},
  nearby:{name:'BetterNear',w:127,h:61,x:.035,y:.2,on:true,parts:near},
  inventory:{name:'InventoryHUD',w:178,h:80,x:.96,y:.81,on:true,parts:inventory,art:[3,4,5,6].map((id,i)=>art(id,5+i*19,21,16))},
  football:{name:'Football · счёт',w:67,h:18,x:.5,y:.32,on:false,parts:score('Голы: 2')},
  basketball:{name:'Basketball · счёт',w:67,h:18,x:.5,y:.4,on:false,parts:score('Счёт: 4')},
  scoreboard:{name:'Scoreboard · контекст',w:84,h:27,x:1,y:.5,fixed:true,parts:[
    box(0,0,84,9,0,'#00000066'),box(0,9,84,18,0,'#0000004d'),
    label('Выживание',2,0,80,9,7,'white','center'),
    label('Онлайн',2,9,56,9,7,'white'),label('37',59,9,23,9,7,'#ff5555','right'),
    label('Сезон',2,18,56,9,7,'white'),label('2',59,18,23,9,7,'#ff5555','right')]},
  hotbar:{name:'Хотбар Minecraft · контекст',w:182,h:22,x:.5,y:1,fixed:true,on:true,parts:[
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
    --accent: #efc887 !important;
    --text: #f4f1eb;
    --muted: #bbc4c3;
    --line: #eff9f526;
    --top: #192120;
    --surface: #192120;
    --hud-panel: #141c1bed;
    --hud-glass: #141c1bdc;
    --hud-selected: #efc88732;
  }
  .panel, .surface, .toast, .invite { box-shadow: inset 0 0 0 1px #d6e6dd29; }
  .key { border-radius: 3px !important; }
  .key[data-pressed] { border-color: #efc887; color: #fff4df; }
  .timer { background: #efc88720; color: #ffe2b2; font-size: 6.4px; }
  .timer > i { width: 3px; height: 3px; }
  #island .surface { border-radius: 6px; }
  #island .shoulder { display: none; }
  #island .metric > span:first-child { font-weight: 700; color: var(--accent); }
`;
function mountScene(el,def,track=false){
  const unit=def.island||def.template?1:1.6, surface=document.createElement('div');
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
    }else if(image==='potion'){overlay.style.backgroundImage="url('/editor-live/potions.png')";overlay.style.backgroundSize='contain';}
    else{
      const original=image<3?18:16, zoom=size/original;
      overlay.style.backgroundImage="url('hud-editor-items.png')";
      overlay.style.backgroundSize=(220*zoom)+'px '+(20*zoom)+'px';
      overlay.style.backgroundPosition=(-(image*20+1)*zoom)+'px '+(-zoom)+'px';
    }
    surface.append(overlay);
  }
  el.prepend(surface);
}
const key='gleam-hud-editor-v1';
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
    y:def.fixed||def.island?y:num(s.y,y,0,1),
    scale:def.fixed?1:num(s.scale,1,.35,4),
    on:typeof s.on==='boolean'?s.on:on
  };
  const el=document.createElement('div');
  el.className='widget';el.id='widget-'+id;el.dataset.key=id;
  el.setAttribute('aria-label',name);
  if(def.fixed){el.classList.add('fixed-widget');}
  else{
    el.tabIndex=0;
    el.innerHTML='<button class="handle" title="Изменить размер" aria-label="Изменить размер"></button>';
  }
  if(def.island)model.track=s.track===true;
  mountScene(el,model.track?{...def,h:57}:def,model.track);
  layers.append(el);el.hidden=!model.on;
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
  el.hidden=!m.on;if(!m.on)return;
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
  $('visible-count').textContent=Object.keys(models).filter(id=>models[id].on&&!catalog[id].fixed).length;
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
  panel.innerHTML='<h2>'+title+'<button class="close" aria-label="Закрыть">×</button></h2>'+content;
  panel.hidden=false;panel.querySelector('.close').onclick=clear;
}
function inspect(id){
  clear();selected=id;$('widget-'+id).classList.add('selected');
  const def=catalog[id],model=models[id];
  openPanel(def.name,'<label>Масштаб <output id="pct"></output><input id="scale" type="range" min="35" max="400" step="1"></label>'
    +'<div class="coords"><label>X, px <input id="pos-x" type="number" min="0" step="1"></label>'
    +'<label>Y, px <input id="pos-y" type="number" min="0" step="1" '+(def.island?'disabled':'')+'></label></div>'
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
  const buttons=items=>items.map(([id,{name}])=>'<button class="option" data-id="'+id+'" aria-pressed="'+models[id].on+'">'+name+'<span>'+(models[id].on?'✓':'＋')+'</span></button>').join('');
  openPanel('Виджеты',buttons(entries.filter(([,def])=>!def.fixed))
    +'<h3>Экранные оверлеи</h3>'+buttons(entries.filter(([,def])=>def.fixed)));
  panel.querySelectorAll('.option').forEach(btn=>btn.onclick=()=>{
    const id=btn.dataset.id;models[id].on=!models[id].on;place(id);updateCount();save();
    btn.setAttribute('aria-pressed',models[id].on);btn.lastElementChild.textContent=models[id].on?'✓':'＋';
  });
}
layers.onpointerdown=e=>{
  const el=e.target.closest('.widget:not(.fixed-widget)');if(!el||viewing||e.button!==0||drag)return;
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
  m.y=catalog[id].island?0:clamp(pos(d.top+(d.resize?0:dy))/(g.h||1),0,1);place(id);
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
layers.onkeydown=e=>{if(e.key==='Enter'&&!viewing)inspect(e.target.closest('.widget').dataset.key);};
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
  redraw();save();
};
function preview(){
  clear();viewing=!viewing;document.body.classList.toggle('view',viewing);layers.inert=viewing;
  $('library').hidden=viewing;$('preview').textContent=viewing?'Вернуться к редактированию':'Просмотр';
  $('preview').setAttribute('aria-pressed',viewing);
}
$('preview').onclick=preview;
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(viewing)preview();else clear();}});
new ResizeObserver(()=>{redraw();if(selected)updateCoordinates(selected);}).observe(stage);
mode();redraw();updateCount();
