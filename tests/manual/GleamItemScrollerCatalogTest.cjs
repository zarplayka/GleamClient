const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const test = require('node:test');
const path = require('node:path');
const source = readFileSync(path.join(__dirname,'../../src/main/resources/assets/orion-client/html/menu/game.js'),'utf8');
// Исполняем реальную начальную часть bridge update, до остальных подсистем меню.
const start = source.indexOf('  update(data) {') + '  update(data) {'.length;
const end = source.indexOf('    if (state.wingsStyle',start);
assert.ok(start > 0 && end > start);
function menu() {
  const c = {catalog:[],ordered:[],byId:new Map(),presets:[],gameReady:false,uiLanguage:'ru',
    state:{page:'all'},applyGameLanguage(language) { c.uiLanguage = language; },
    setCategoriesExpanded() {},sendGame() {}};
  vm.createContext(c);
  c.update = vm.runInContext('(function(data) {' + source.slice(start,end) + 'return redraw;})',c);
  return c;
}
const data = (allowed,language='ru') => ({language,page:'all',presets:[],look:{},catalog:[
  {id:'HUD',description:language,settings:[]},
  ...(allowed ? [{id:'ITEM_SCROLLER',description:language,settings:[]}] : [])
]});
test('disallowed connection hides Item Scroller on initial menu load',() => {
  const c = menu(); c.update(data(false));
  assert.equal(c.byId.has('ITEM_SCROLLER'),false);
  assert.equal(c.catalog.length,1);
});
test('IP DNS result adds module to an already open menu in the same language',() => {
  const c = menu(); c.update(data(false));
  assert.equal(c.update(data(true)),true);
  assert.equal(c.byId.has('ITEM_SCROLLER'),true);
  assert.equal(c.catalog.length,2);
  assert.equal(c.ordered.length,2);
});
test('losing permission removes module, closes its settings and preserves other settings',() => {
  const c = menu(); c.update(data(true));
  const settings = [{label:'Example',value:true}];
  c.byId.get('HUD').settings = settings;
  c.state.page='module';c.state.module='ITEM_SCROLLER';
  assert.equal(c.update(data(false)),true);
  assert.equal(c.byId.has('ITEM_SCROLLER'),false);
  assert.equal(c.state.module,null);
  assert.equal(c.state.page,'all');
  assert.equal(c.byId.get('HUD').settings,settings);
});
test('language change and restored permission together create a valid map',() => {
  const c=menu();c.update(data(false));c.update(data(true,'en'));
  assert.equal(c.uiLanguage,'en');
  assert.equal(c.byId.get('ITEM_SCROLLER').description,'en');
  assert.equal(c.byId.get('HUD').description,'en');
  assert.equal(c.update(data(true,'en')),false);
});
