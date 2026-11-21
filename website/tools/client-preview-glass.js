const vertex = `#version 300 es
uniform vec2 resolution;
uniform vec2 crop;
uniform vec4 surface;
uniform vec4 radii;
out vec4 tint;
out vec2 texCoord;
out vec2 localPosition;
flat out vec2 halfSize;
flat out float radius;
flat out ivec2 primitive;
out vec2 guiPosition;
flat out vec3 themeCircle;
void main() {
    vec2 corner = vec2((gl_VertexID & 1) * 2, (gl_VertexID >> 1) * 2);
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
    guiPosition = vec2(corner.x, 1.0 - corner.y) * resolution;
    halfSize = surface.zw * 0.5;
    localPosition = guiPosition - surface.xy - halfSize;
    texCoord = guiPosition * crop / resolution + vec2(0.0, (1.0 - crop.y) * 0.5);
    radius = radii.x;
    tint = vec4(1.0, 1.0, 1.0, 253.0 / 255.0);
    primitive = ivec2(1, 0);
    themeCircle = vec3(0.0);
}`;

async function shaderSource(path) {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`Cannot load ${path}`);
  return response.text();
}

function webLumenGlass(shader, optics) {
  return shader
    .replace(/^#version .*$/m, '#version 300 es\n#define LUMEN_GLASS\nprecision highp float;\nprecision highp int;')
    .replace(/^#extension .*$/m, '')
    .replace('#include <minecraft:dynamictransforms.glsl>', '#define ColorModulator vec4(1.0)')
    .replace('#include <orion-client:liquid_glass.glsl>', optics)
    .replace(/layout\(location = \d+\) (?=(?:flat )?in )/g, '');
}

export async function previewGlass(selector, backdrop, title) {
  const scene = new Image();
  scene.src = backdrop;
  const [optics, paint] = await Promise.all([
    shaderSource('/liquid-glass.glsl'),
    title ? shaderSource('/lumen-paint.fsh') : '',
    scene.decode()
  ]);
  const canvas = document.createElement('canvas');
  const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: false });
  if (!gl) return;

  const fragment = title ? webLumenGlass(paint, optics) : `#version 300 es
precision highp float;
uniform sampler2D scene;
uniform vec2 resolution;
uniform vec2 crop;
uniform vec4 surface;
uniform vec4 radii;
out vec4 color;
${optics}
void main() {
    vec2 point = vec2(gl_FragCoord.x, resolution.y - gl_FragCoord.y);
    vec2 halfSize = surface.zw * 0.5;
    vec2 local = point - surface.xy - halfSize;
    vec2 pair = local.y < 0.0 ? radii.xy : radii.wz;
    float radius = min(local.x < 0.0 ? pair.x : pair.y, min(halfSize.x, halfSize.y));
    vec2 q = abs(local) - halfSize + radius;
    vec2 corner = max(q, 0.0);
    float distance = length(corner) + min(max(q.x, q.y), 0.0) - radius;
    float coverage = clamp(0.5 - distance, 0.0, 1.0);
    if (coverage <= 0.0) discard;
    vec2 normal = (length(corner) > 0.0 ? normalize(corner) : (q.x > q.y ? vec2(1, 0) : vec2(0, 1))) * sign(local);
    float depth = clamp(min(halfSize.x, halfSize.y) * 0.24, 4.0, 17.0);
    vec2 pixel = crop / resolution;
    vec2 uv = point * pixel + (1.0 - crop) * 0.5;
    color = vec4(liquidGlass(scene, uv, pixel, normal, normal, clamp(-distance / depth, 0.0, 1.0)), coverage);
}`;
  const program = gl.createProgram();
  for (const [type, source] of [[gl.VERTEX_SHADER, vertex], [gl.FRAGMENT_SHADER, fragment]]) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
    gl.attachShader(program, shader);
    gl.deleteShader(shader);
  }
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);

  const resolution = gl.getUniformLocation(program, 'resolution');
  const crop = gl.getUniformLocation(program, 'crop');
  const surface = gl.getUniformLocation(program, 'surface');
  const radii = gl.getUniformLocation(program, 'radii');
  const texture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, scene);

  const root = document.documentElement;
  canvas.className = 'preview-glass-layer';
  canvas.setAttribute('aria-hidden', 'true');
  document.body.prepend(canvas);
  const observed = new Set();
  const resize = new ResizeObserver(queue);
  resize.observe(root);
  let frame = 0;

  function draw() {
    frame = 0;
    const enabled = root.dataset.theme === 'glass' || root.hasAttribute('data-glass');
    canvas.hidden = !enabled;
    if (!enabled) return;
    const scale = devicePixelRatio;
    const width = Math.round(innerWidth * scale), height = Math.round(innerHeight * scale);
    if (width < 1 || height < 1) return;
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const cover = Math.max(width / scene.width, height / scene.height) * (title ? 1.38 : 1);
    gl.viewport(0, 0, width, height);
    gl.disable(gl.SCISSOR_TEST);
    gl.clear(gl.COLOR_BUFFER_BIT);
    gl.enable(gl.SCISSOR_TEST);
    gl.uniform2f(resolution, width, height);
    gl.uniform2f(crop, width / (cover * scene.width), height / (cover * scene.height));
    const surfaces = new Set(document.querySelectorAll(selector));
    for (const element of observed) {
      if (surfaces.has(element)) continue;
      resize.unobserve(element);
      observed.delete(element);
    }
    for (const element of surfaces) {
      if (!observed.has(element)) {
        resize.observe(element);
        observed.add(element);
      }
      const rect = element.getBoundingClientRect();
      if (!rect.width || !rect.height) continue;
      const css = getComputedStyle(element);
      const zoom = rect.width / parseFloat(css.width);
      const corners = [css.borderTopLeftRadius, css.borderTopRightRadius, css.borderBottomRightRadius, css.borderBottomLeftRadius]
        .map(value => (parseFloat(value) || 0) * zoom * scale);
      gl.uniform4fv(radii, corners);
      gl.uniform4f(surface, rect.x * scale, rect.y * scale, rect.width * scale, rect.height * scale);
      const left = Math.floor(rect.left * scale), right = Math.ceil(rect.right * scale);
      const top = Math.floor(rect.top * scale), bottom = Math.ceil(rect.bottom * scale);
      gl.scissor(left, height - bottom, right - left, bottom - top);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    }
  }

  function queue() {
    if (!frame) frame = requestAnimationFrame(draw);
  }
  const mutations = new MutationObserver(records => {
    if (records.some(({ target }) => target === root || target.matches?.('#island, #hud, ' + selector))) queue();
  });
  mutations.observe(root, { attributes: true, attributeFilter: ['data-glass', 'data-theme', 'hidden', 'style'], childList: true, subtree: true });
  window.addEventListener('resize', queue);
  canvas.addEventListener('webglcontextlost', () => {
    cancelAnimationFrame(frame);
    resize.disconnect();
    mutations.disconnect();
    window.removeEventListener('resize', queue);
    delete root.dataset.previewGlass;
    canvas.remove();
  });
  draw();
  root.dataset.previewGlass = '';
}
