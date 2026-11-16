const { readFile } = require('node:fs/promises');
const { watch } = require('node:fs');
const { createServer } = require('node:http');
const path = require('node:path');

const resources = path.resolve(__dirname, '../../src/main/resources/assets/orion-client');
const port = Number(process.env.GLEAM_CLIENT_PREVIEW_PORT ?? 4176);
const tools = name => path.join(__dirname, name);
const game = name => path.join(resources, name);
const routes = new Map([
  ['/', tools('client-preview.html')],
  ['/client-preview.js', tools('client-preview.js')],
  ['/catalog.json', tools('client-preview-catalog.json')],
  ['/clickgui/', game('html/menu/index.html')],
  ['/title/', game('html/title-menu.html')],
  ['/hud/', game('html/hud-island/index.html')],
  ['/widgets/', game('html/hud-widgets.html')],
  ['/hud-theme.css', game('html/hud-theme.css')],
  ['/widgets/hud-theme.css', game('html/hud-theme.css')],
  ['/title/assets/gleam-title-panorama.png', game('textures/gui/gleam_title_panorama.png')],
  ['/clickgui/module-atlas.png', game('textures/gui/click_gui_modules.png')],
  ['/clickgui/cosmetic-atlas.png', game('textures/gui/click_gui_cosmetics.png')],
  ['/clickgui/assets/world.webp', tools('launcher-background-26-3.webp')]
]);
const badge = game('textures/cosmetics/orion_badge.png');
for (const [screen, name] of [['clickgui', 'orion-badge'], ['title', 'gleam-title-badge'], ['hud', 'gleam-hud-badge']]) {
  routes.set(`/${screen}/assets/${name}.png`, badge);
}
for (const name of ['style.css', 'lumen.css', 'preview.js', 'game.js']) {
  routes.set(`/clickgui/${name}`, game(`html/menu/${name}`));
}
for (const name of ['style.css', 'island.js']) routes.set(`/hud/${name}`, game(`html/hud-island/${name}`));
for (const [name, font] of Object.entries({ regular: 'Regular', medium: 'Medium', semibold: 'SemiBold', bold: 'Bold' })) {
  routes.set(`/fonts/paradox/ParadoxSans-${font}.ttf`, game(`fonts/paradox/ParadoxSans-${font}.ttf`));
  for (const screen of ['clickgui', 'hud', 'widgets']) {
    routes.set(`/${screen}/assets/orion-${name}.ttf`, game(`fonts/paradox/ParadoxSans-${font}.ttf`));
  }
}
for (const language of ['en', 'de', 'es']) routes.set(`/clickgui/lang/${language}.json`, game(`lang/click_gui_${language}.json`));

const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.png': 'image/png', '.webp': 'image/webp', '.ttf': 'font/ttf' };
const transparentPixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');
const subscribers = new Set();
const adapter = '<script defer src="/client-preview.js"></script>';

function browserDocument(route, html) {
  if (route === '/clickgui/') {
    return html.replace('<script defer src="catalog.js"></script>', '<link rel="stylesheet" href="lumen.css">')
      .replace('</head>', `<script defer src="game.js"></script>${adapter}</head>`);
  }
  return html.replace('</body>', `${adapter}</body>`);
}

const server = createServer(async (request, response) => {
  const route = request.url.split('?', 1)[0];
  response.setHeader('Cache-Control', 'no-store');
  if (request.method !== 'GET') return response.writeHead(405).end();
  if (route === '/changes') {
    response.writeHead(200, { 'Content-Type': 'text/event-stream' }).write(': connected\n\n');
    subscribers.add(response);
    response.on('close', () => subscribers.delete(response));
    return;
  }
  if (route === '/favicon.ico') return response.writeHead(204).end();
  const file = routes.get(route);
  const item = /^\/clickgui\/assets\/(cosmetic-[a-z_]+-(?:item|preview)\.png)$/.exec(route)?.[1];
  try {
    if (file) {
      const type = path.extname(file);
      const body = await readFile(file, type === '.html' ? 'utf8' : null);
      response.writeHead(200, { 'Content-Type': types[type] });
      return response.end(type === '.html' && route !== '/' ? browserDocument(route, body) : body);
    }
    if (item?.endsWith('-item.png')) {
      const body = await readFile(game(`html/menu/assets/${item}`)).catch(error => {
        if (error.code !== 'ENOENT') throw error;
        return transparentPixel;
      });
      return response.writeHead(200, { 'Content-Type': 'image/png' }).end(body);
    }
    if (/^\/clickgui\/assets\/[a-z_0-9-]+\.png$/.test(route)) {
      return response.writeHead(200, { 'Content-Type': 'image/png' }).end(transparentPixel);
    }
    response.writeHead(404).end('Not found');
  } catch (error) {
    if (error.code !== 'ENOENT') console.error(error);
    response.writeHead(error.code === 'ENOENT' ? 404 : 500).end();
  }
});

let pending;
function reload() {
  clearTimeout(pending);
  pending = setTimeout(() => {
    for (const response of subscribers) response.write('data: reload\n\n');
  }, 120);
}
watch(resources, { recursive: true }, reload);
watch(__dirname, (_, name) => {
  if (name === 'client-preview-catalog.json' || name === 'client-preview.js') reload();
});

server.listen(port, '127.0.0.1', () => console.log(`GleamClient · http://127.0.0.1:${port}/`));
