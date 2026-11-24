const {createServer} = require('node:http');
const {createReadStream} = require('node:fs');
const {stat} = require('node:fs/promises');
const path = require('node:path');

const root = path.resolve(__dirname, '../..');
const site = path.join(root, 'website');
const assets = path.join(root, 'src/main/resources/assets/orion-client');
const port = Number(process.env.GLEAM_HOME_PREVIEW_PORT || 4189);
const types = {
  '.html':'text/html; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.css':'text/css; charset=utf-8',
  '.json':'application/json', '.ttf':'font/ttf', '.woff2':'font/woff2', '.webp':'image/webp',
  '.png':'image/png', '.jpg':'image/jpeg', '.svg':'image/svg+xml', '.mesh':'application/octet-stream'
};

createServer(async (request,response) => {
  if (!['GET','HEAD'].includes(request.method)) return response.writeHead(405).end();
  try {
    const route = decodeURIComponent(new URL(request.url,'http://127.0.0.1').pathname);
    if (route === '/') return response.writeHead(302,{Location:'/tools/launcher-home-preview.html'}).end();
    if (route === '/favicon.ico') return response.writeHead(204).end();
    const cosmetic = /^\/src\/main\/resources\/assets\/orion-client\/((?:textures\/)?cosmetics\/(?:wings|pets|hats)\/[a-z_]+\.(?:json|png|mesh))$/.exec(route);
    const extension = path.extname(route);
    const file = cosmetic ? path.join(assets,cosmetic[1])
      : route === '/build/launcher-cosmetics.json' ? path.join(root,'build/launcher-cosmetics.json')
      : /^(?:\/tools\/|\/assets\/)[a-zA-Z0-9_/-]+\.[a-z0-9]+$/.test(route) && types[extension]
        ? path.join(site,route) : null;
    if (!file) return response.writeHead(404).end();
    const info = await stat(file);
    if (!info.isFile()) return response.writeHead(404).end();
    response.writeHead(200,{'Content-Type':types[extension],'Content-Length':info.size,'Cache-Control':'no-store'});
    if (request.method === 'HEAD') return response.end();
    createReadStream(file).on('error',() => response.destroy()).pipe(response);
  } catch (error) {
    return response.writeHead(error instanceof URIError ? 400 : error.code === 'ENOENT' ? 404 : 500).end();
  }
}).listen(port,'127.0.0.1',() => console.log(`Gleam Launcher preview · http://127.0.0.1:${port}/tools/launcher-home-preview.html`));
