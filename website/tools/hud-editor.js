'use strict';
const $ = id => document.getElementById(id);
const stage = $('stage'), panel = $('panel'), layers = $('layers');
const clamp = (n,min,max) => Math.max(min,Math.min(max,n));
// Preview values use the same scene coordinates as the client HUD renderers.
const part=(kind,x,y,w,h,props={})=>({kind,x,y,w,h,...props});
const panelPart=(w,h,radius=7)=>part('panel',0,0,w,h,{radius});
const label=(text,x,y,w,h,size,color='var(--text)',align='left',bold=false)=>part('label',x,y,w,h,{text,size,color,align,bold});
const rule=(x,y,w,h=1)=>part('divider',x,y,w,h);
const box=(x,y,w,h,radius,color)=>part('box',x,y,w,h,{radius,color});
const well=(x,y,w,h,radius)=>part('well',x,y,w,h,{radius});
const keyPart=(text,x,y,w=17,mouse='')=>part('key',x,y,w,17,{text,mouse,radius:6,pressed:text==='W'});
const icon=(path,x,y,size,fill=false)=>part('icon',x,y,size,size,{path,fill,color:'var(--accent)'});
const art=(image,x,y,size)=>({image,x,y,size});
const health='#80d5a4', muted='var(--muted)';
function targetScene(){
  return [panelPart(154,63),well(7,8,26,26,4),label('Quartz',39,4,76,15,9,'var(--text)','left',true),
    label('24 ms',115,4,31,15,7,muted,'right'),label('18 / 20 +2',39,17,67,12,8,health,'left',true),
    label('3.8 m',109,17,37,12,7.5,muted,'right'),well(39,31,107,4,2),box(39,31,96,4,2,health),
    box(39,36,11,2,1,'#e8b967'),rule(7,41,140)];
}
const effects=[panelPart(94,47,9),label('Effects',22,0,66,17,6.8,'var(--text)','left',true),rule(6,16,82),rule(17,4,1,9)];
for(const [index,[name,time]] of [['Скорость 2','1:42'],['Сила 1','0:38']].entries()){
  const y=17+13*index;
  effects.push(label(name,15,y,51,13,5.8),part('timer',67,y+2,21,9,{text:time}));
}
const near=[panelPart(127,61),icon('M2 10h16M10 2v16M10 5a5 5 0 1 0 .01 0Z',7,5,11),label('Рядом',21,2,78,17,8.5,'var(--text)','left',true),
  rule(7,20,113),label('2',105,4,15,12,7,muted,'right')];
for(const [i,[name,distance]] of [['Quartz','8'],['Nova','21']].entries()){
  const y=22+i*17;
  if(i)near.push(rule(7,y-1,113));
  near.push(icon(i?'M10 2l6 14-6-3-6 3Z':'M16 4l-4 14-2-6-6-2Z',11,y+3,9,true),label(name,24,y,65,15,8),
    label(distance,80,y,28,15,8,'var(--text)','right',true),label('m',110,y,7,15,6.5,muted));
}
const inventory=[panelPart(178,64,6),...Array.from({length:27},(_,i)=>box(4+i%9*19,4+Math.floor(i/9)*19,18,18,2,'#80809a18'))];
function score(text){return [panelPart(67,15,6),label(text,4,0,59,15,8,'var(--text)','center',true)];}
const catalog={
  status:{name:'HUD / MediaPlayer',w:366,h:34,x:.5,y:0,on:true,island:true},
  keys:{name:'Keystrokes',w:55,h:55,x:.025,y:.84,on:true,parts:[keyPart('W',19,0),keyPart('A',0,19),keyPart('S',19,19),keyPart('D',38,19),keyPart('',19,38,36),keyPart('',0,38,17,'L')]},
  potions:{name:'Potions',w:94,h:47,x:.97,y:.19,on:true,parts:effects,art:[art('potion',6,4,8),art(0,6,20,7),art(1,6,33,7)]},
  target:{name:'TargetHud',w:154,h:63,x:.58,y:.61,on:true,parts:targetScene(),art:[art('face',8,9,24),...[7,8,9,10,3,5].map((id,i)=>art(id,8+20*i,43,16))]},
  nearby:{name:'BetterNear',w:127,h:61,x:.035,y:.2,on:false,parts:near},
  inventory:{name:'InventoryHUD',w:178,h:64,x:.96,y:.45,on:false,parts:inventory,art:[3,4,5,6].map((id,i)=>art(id,5+i*19,5,16))},
  football:{name:'Football · счёт',w:67,h:15,x:.5,y:.32,on:false,parts:score('Голы: 2')},
  basketball:{name:'Basketball · счёт',w:67,h:15,x:.5,y:.4,on:false,parts:score('Счёт: 4')}
};
function mountScene(el,def){
  const unit=def.island?1:1.6, surface=document.createElement('div');
  el.style.width=def.w*unit+'px';el.style.height=def.h*unit+'px';
  surface.className='scene';surface.style.cssText='width:'+def.w+'px;height:'+def.h+'px;transform:scale('+unit+')';
  const frame=document.createElement('iframe');
  frame.title=def.name;frame.tabIndex=-1;
  frame.width=def.island?392:def.w;frame.height=def.island?222:def.h;
  if(def.island)frame.style.left='-13px';
  frame.onload=()=>{
    frame.contentDocument.documentElement.style.colorScheme='dark';
    const state=def.island?{hud:true,version:'0.2.39',username:'Quartz',fps:144,ping:24,scale:1,animated:false,placement:'TOP_CENTER'}:{parts:def.parts};
    frame.contentWindow.paradox.update(state);
  };
  frame.src=def.island?'/editor-live/island/':'/editor-live/widgets.html';
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
let selected=null, viewing=false, drag=null;
const models={};
for(const [id,def] of Object.entries(catalog)){
  const {name,x,y,on}=def;
  const s=saved.widgets?.[id] || {};
  const model=models[id]={x:num(s.x,x,0,1),y:def.island?0:num(s.y,y,0,1),scale:num(s.scale,1,.35,4),on:typeof s.on==='boolean'?s.on:on};
  const el=document.createElement('div');
  el.className='widget';el.id='widget-'+id;el.dataset.key=id;
  el.tabIndex=0;el.setAttribute('aria-label',name);
  el.innerHTML='<button class="handle" title="Изменить размер" aria-label="Изменить размер"></button>';
  mountScene(el,def);
  layers.append(el);el.hidden=!model.on;
}
function save(){
  try {localStorage.setItem(key,JSON.stringify({widgets:models,step,snap}));$('saved').textContent='●　Сохранено локально';}
  catch {$('saved').textContent='Сохранение недоступно';}
}
function geometry(id){
  const el=$('widget-'+id), m=models[id];
  const scale=Math.min(m.scale,stage.clientWidth/el.offsetWidth,stage.clientHeight/el.offsetHeight);
  return {scale,w:Math.max(0,stage.clientWidth-el.offsetWidth*scale),h:Math.max(0,stage.clientHeight-el.offsetHeight*scale)};
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
  $('step').value=step;
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
  openPanel(catalog[id].name,'<label>Масштаб <output id="pct"></output><input id="scale" type="range" min="35" max="400" step="1"></label><button class="remove">Скрыть виджет</button>');
  function scaleLabel(){const n=Math.round(models[id].scale*100);$('pct').value=n+'%';$('scale').value=n;}
  scaleLabel();$('scale').oninput=e=>{models[id].scale=e.target.value/100;place(id);scaleLabel();save();};
  panel.querySelector('.remove').onclick=()=>{models[id].on=false;clear();place(id);save();};
}
function library(){
  const wasOpen=$('library').getAttribute('aria-expanded')==='true';clear();if(wasOpen)return;
  $('library').setAttribute('aria-expanded','true');
  openPanel('Виджеты',Object.entries(catalog).map(([id,{name}])=>'<button class="option" data-id="'+id+'" aria-pressed="'+models[id].on+'">'+name+'<span>'+(models[id].on?'✓':'＋')+'</span></button>').join(''));
  panel.querySelectorAll('.option').forEach(btn=>btn.onclick=()=>{
    const id=btn.dataset.id;models[id].on=!models[id].on;place(id);save();
    btn.setAttribute('aria-pressed',models[id].on);btn.lastElementChild.textContent=models[id].on?'✓':'＋';
  });
}
layers.onpointerdown=e=>{
  const el=e.target.closest('.widget');if(!el||viewing||e.button!==0||drag)return;
  const id=el.dataset.key,m=models[id],g=geometry(id);
  const resize=!!e.target.closest('.handle');
  if(selected!==id)clear();
  el.classList.add('selected');selected=id;
  drag={id,pointer:e.pointerId,x:e.clientX,y:e.clientY,left:m.x*g.w,top:m.y*g.h,scale:m.scale,resize,moved:false};
  el.setPointerCapture(e.pointerId);e.preventDefault();
};
layers.onpointermove=e=>{
  if(!drag||e.pointerId!==drag.pointer)return;
  const d=drag,id=d.id,m=models[id],el=$('widget-'+id);
  const dx=e.clientX-d.x,dy=e.clientY-d.y;
  if(!d.moved&&Math.hypot(dx,dy)<3)return;
  d.moved=true;
  if(d.resize){
    m.scale=clamp(d.scale+(dx*el.offsetWidth+dy*el.offsetHeight)/(el.offsetWidth**2+el.offsetHeight**2),.35,4);
    if($('scale')){$('scale').value=Math.round(m.scale*100);$('pct').value=$('scale').value+'%';}
  }
  const g=geometry(id),pos=v=>snap&&!e.altKey&&!d.resize?Math.round(v/step)*step:v;
  m.x=clamp(pos(d.left+(d.resize?0:dx))/(g.w||1),0,1);
  m.y=catalog[id].island?0:clamp(pos(d.top+(d.resize?0:dy))/(g.h||1),0,1);place(id);
};
function finish(e){
  if(!drag||e.pointerId!==drag.pointer)return;
  const d=drag;drag=null;
  if(d.moved)save();else if(e.type==='pointerup')inspect(d.id);
}
layers.onpointerup=finish;layers.onpointercancel=finish;
layers.onkeydown=e=>{if(e.key==='Enter'&&!viewing)inspect(e.target.closest('.widget').dataset.key);};
stage.onpointerdown=e=>{if(!e.target.closest('.widget,aside'))clear();};
$('library').onclick=library;
$('snap').onclick=()=>{snap=true;mode();save();};$('free').onclick=()=>{snap=false;mode();save();};
$('step').onchange=e=>{step=Number(e.target.value);mode();save();};
function preview(){
  clear();viewing=!viewing;document.body.classList.toggle('view',viewing);layers.inert=viewing;
  $('library').hidden=viewing;$('preview').textContent=viewing?'✎　Редактировать':'▷　Просмотр';
}
$('preview').onclick=preview;
document.addEventListener('keydown',e=>{if(e.key==='Escape'){if(viewing)preview();else clear();}});
new ResizeObserver(redraw).observe(stage);mode();redraw();
