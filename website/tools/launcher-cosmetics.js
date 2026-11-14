(() => {
  const resources = '../../src/main/resources/assets/orion-client/';
  const radians = degrees => degrees * Math.PI / 180;
  const identity = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1];
  const translate = (x,y,z) => [1,0,0,0, 0,1,0,0, 0,0,1,0, x,y,z,1];
  const scale = (x,y,z) => [x,0,0,0, 0,y,0,0, 0,0,z,0, 0,0,0,1];
  function rotateX(angle) {
    const c = Math.cos(angle), s = Math.sin(angle);
    return [1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1];
  }
  function rotateY(angle) {
    const c = Math.cos(angle), s = Math.sin(angle);
    return [c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1];
  }
  function rotateZ(angle) {
    const c = Math.cos(angle), s = Math.sin(angle);
    return [c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1];
  }
  function multiply(a,b) {
    const result = new Array(16);
    for (let col = 0; col < 16; col += 4) {
      for (let row = 0; row < 4; row++) {
        result[col+row] = a[row]*b[col]+a[row+4]*b[col+1]+a[row+8]*b[col+2]+a[row+12]*b[col+3];
      }
    }
    return result;
  }
  const rotation = ([x,y,z]) => multiply(rotateZ(radians(z)),multiply(rotateY(radians(y)),rotateX(radians(x))));
  function transform(matrix,[x,y,z]) {
    return [
      matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12],
      matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13],
      matrix[2]*x+matrix[6]*y+matrix[10]*z+matrix[14]
    ];
  }
  function triangle(vertices,points,uv,color,shade) {
    for (let i = 0; i < 3; i++) vertices.push(...points[i],...uv[i],...color,shade);
  }
  function quad(vertices,points,uv,color = [1,1,1,1],shade = 1) {
    for (const indices of [[0,1,2],[0,2,3]]) {
      triangle(vertices,indices.map(i=>points[i]),indices.map(i=>uv[i]),color,shade);
    }
  }
  function cuboid(vertices,coords,matrix,tw,th,inflate = 0) {
    const [u,v,x,y,z,w,h,d] = coords;
    const x0=x-inflate, x1=x+w+inflate, y0=y-inflate, y1=y+h+inflate, z0=z-inflate, z1=z+d+inflate;
    const corners = [[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],
      [x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]].map(point=>transform(matrix,point));
    for (const [indices,s,t,width,height,light,flipY] of [
      [[0,1,2,3],u+d,v+d,w,h,1],
      [[5,4,7,6],u+2*d+w,v+d,w,h,.80],
      [[4,0,3,7],u,v+d,d,h,.82],
      [[1,5,6,2],u+d+w,v+d,d,h,.89],
      [[4,5,1,0],u+d,v,w,d,1.04],
      [[3,2,6,7],u+d+w,v,w,d,.68,true]
    ]) {
      const left = s/tw, right = (s+width)/tw, top = (t+(flipY ? height : 0))/th, bottom = (t+(flipY ? 0 : height))/th;
      quad(vertices,indices.map(i=>corners[i]),[[left,top],[right,top],[right,bottom],[left,bottom]],undefined,light);
    }
  }
  function assetUrl(file,source) {
    return window.__TAURI_INTERNALS__ ? file : source+file;
  }
  async function asset(file,source) {
    const response = await fetch(assetUrl(file,source));
    if (!response.ok) throw Error('Missing cosmetic asset: '+file);
    return response;
  }
  const emptyUV = [[0,0],[0,0],[0,0],[0,0]];
  function color(value,alpha = 1) {
    return [(value>>16&255)/255,(value>>8&255)/255,(value&255)/255,alpha];
  }
  function wingVertices(faces) {
    const vertices = [];
    for (const side of [-1,1]) {
      const pose = multiply(translate(0,-1.37,.16),
        multiply(scale(side,-1,1),rotateY(radians(-13))));
      for (const face of faces) {
        const points = [1,4,7,10].map(start=>transform(pose,face.slice(start,start+3)));
        quad(vertices,points,emptyUV,color(face[0]));
      }
    }
    return vertices;
  }
  function haloVertices() {
    const vertices = [];
    for (const [inner,outer,bright,alpha] of [[.31,.35,0xffc639,.40],[.35,.41,0xffdc79,.94],[.41,.46,0xffaf23,.34]]) {
      const point = (radius,angle) => [Math.cos(angle)*radius,-2.01,Math.sin(angle)*radius];
      for (let i = 0; i < 64; i++) {
        const a=i*Math.PI/32, b=(i+1)*Math.PI/32;
        quad(vertices,[point(inner,a),point(outer,a),point(outer,b),point(inner,b)],emptyUV,color(bright,alpha));
      }
    }
    return vertices;
  }
  function hatVertices() {
    const vertices = [];
    for (let i = 0; i < 64; i++) {
      const a=i*Math.PI/32, b=(i+1)*Math.PI/32;
      const edge = angle => [.55*Math.cos(angle),-1.90,.55*Math.sin(angle)];
      triangle(vertices,[[0,-2.32,0],edge(a),edge(b)],emptyUV.slice(0,3),color(0xff7c00,.73),.9+.15*Math.cos(a-.7));
    }
    return vertices;
  }
  function tankVertices(buffer) {
    const data = new DataView(buffer);
    const count = data.getInt32(4);
    if (data.getUint32(0)!==0x47504d31 || count<1 || count>10000 || buffer.byteLength!==8+count*72) {
      throw Error('Invalid pet mesh');
    }
    const vertices = [];
    for (let offset=8; offset<data.byteLength; offset+=72) {
      const light=.84+.16*Math.abs(data.getFloat32(offset+4));
      for (let i=offset+12; i<offset+72; i+=20) {
        vertices.push(data.getFloat32(i)*.68,data.getFloat32(i+4)*.68,data.getFloat32(i+8)*.68,
          data.getFloat32(i+12),data.getFloat32(i+16),1,1,1,1,light);
      }
    }
    return vertices;
  }
  function petBones(data) {
    function bone(source) {
      const vertices = [];
      for (const cube of source.cubes) cuboid(vertices,cube,identity,data.width,data.height);
      return {...source,vertices,children:source.children.map(bone)};
    }
    return data.bones.map(bone);
  }
  function idlePose(clip,time) {
    if (!clip) return new Map();
    const frames = new Map();
    for (const {bone,target,keys} of clip.tracks) {
      const start = keys[0], end = keys.at(-1);
      const t = Math.max(start[0],Math.min(end[0],time%clip.duration));
      const i = keys.findIndex(key=>key[0]>=t);
      const a=keys[Math.max(0,i-1)], b=keys[i], f=b[0]===a[0] ? 0 : (t-a[0])/(b[0]-a[0]);
      if (!frames.has(bone)) frames.set(bone,{});
      frames.get(bone)[target]=a.slice(1).map((value,index)=>value+(b[index+1]-value)*f);
    }
    return frames;
  }

  const vertexSource = 'attribute vec3 p; attribute vec2 uv; attribute vec4 rgba; attribute float shade; uniform mat4 mvp; varying vec2 tex; varying vec4 tint; void main(){gl_Position=mvp*vec4(p,1.);tex=uv;tint=vec4(rgba.rgb*shade,rgba.a);}';
  const fragmentSource = 'precision mediump float; uniform sampler2D atlas; uniform bool blended; varying vec2 tex; varying vec4 tint; void main(){vec4 texel=texture2D(atlas,tex);if(texel.a<(blended?.003:.5))discard;gl_FragColor=texel*tint;}';
  class CosmeticPreview {
    constructor(canvas, thumbnails = false) {
      this.canvas = canvas;
      const gl = canvas.getContext('webgl',{alpha:true, antialias:true, premultipliedAlpha:false, preserveDrawingBuffer:thumbnails});
      if (!gl) throw Error('WebGL is unavailable');
      this.gl = gl;
      const compile = (type,source) => {
        const shader = gl.createShader(type);
        gl.shaderSource(shader,source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader,gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(shader));
        return shader;
      };
      const program = gl.createProgram();
      const vertex = compile(gl.VERTEX_SHADER,vertexSource), fragment = compile(gl.FRAGMENT_SHADER,fragmentSource);
      gl.attachShader(program,vertex); gl.attachShader(program,fragment);
      gl.linkProgram(program);
      if (!gl.getProgramParameter(program,gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(program));
      gl.deleteShader(vertex); gl.deleteShader(fragment);
      gl.useProgram(program);
      this.matrix = gl.getUniformLocation(program,'mvp');
      this.blended = gl.getUniformLocation(program,'blended');
      this.attributes = [['p',3,0],['uv',2,12],['rgba',4,20],['shade',1,36]]
        .map(([name,size,offset])=>[gl.getAttribLocation(program,name),size,offset]);
      gl.enable(gl.DEPTH_TEST);
      gl.blendFuncSeparate(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA);
      this.white = this.texture();
      this.player = [];
      this.pets = new Map();
      this.cosmetics = {halo:this.mesh(haloVertices()),hat:this.mesh(hatVertices())};
      this.geometry = null;
    }

    mesh(vertices) {
      const gl = this.gl, buffer = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
      gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.STATIC_DRAW);
      return {buffer,count:vertices.length/10};
    }

    texture(image) {
      const gl = this.gl, texture = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D,texture);
      if (image) gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);
      else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([255,255,255,255]));
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      return texture;
    }

    setSkin(image,slim = false,headLayer = true) {
      for (const part of this.player) {
        this.gl.deleteBuffer(part.base.buffer);
        if (part.outer) this.gl.deleteBuffer(part.outer.buffer);
      }
      if (this.skin) this.gl.deleteTexture(this.skin);
      this.skin = this.texture(image);
      const height = image.height, legacy = height===32, arm = (slim&&!legacy) ? 3 : 4;
      this.player = [
        [[0,0,-4,-8,-4,8,8,8],[0,8,0],headLayer ? [32,0,.5] : null],
        [[16,16,-4,0,-2,8,12,4],[0,8,0],[16,32,.25]],
        [[40,16,-arm+1,-2,-2,arm,12,4],[-5,10,0],[40,32,.25]],
        [[legacy?40:32,legacy?16:48,-1,-2,-2,arm,12,4],[5,10,0],[48,48,.25]],
        [[0,16,-2,0,-2,4,12,4],[-1.9,20,0],[0,32,.25]],
        [[legacy?0:16,legacy?16:48,-2,0,-2,4,12,4],[1.9,20,0],[0,48,.25]]
      ].map(([box,pivot,outer],index)=>{
        const base=[], overlay=[];
        cuboid(base,box,identity,64,height);
        if (outer && (!legacy || index===0)) {
          cuboid(overlay,[...outer.slice(0,2),...box.slice(2)],identity,64,height,outer[2]);
        }
        const mirrored = legacy && (index===3 || index===5);
        return {base:this.mesh(base),outer:overlay.length ? this.mesh(overlay) : null,pivot,mirrored};
      });
    }

    loadGeometry() {
      if (!this.geometry) {
        this.geometry = asset('launcher-cosmetics.json','../../build/').then(response=>response.json()).then(data=>{
          this.cosmetics.angel = this.mesh(wingVertices(data.angel));
          this.cosmetics.dragon = this.mesh(wingVertices(data.dragon));
          this.dance = data.dance;
        }).catch(error=>{ this.geometry = null; throw error; });
      }
      return this.geometry;
    }

    loadPet(id) {
      if (!this.pets.has(id)) {
        const tank = id==='rhm_tarnkappe';
        const data = asset(id+(tank?'.mesh':'.json'),resources+'cosmetics/pets/')
          .then(response=>tank ? response.arrayBuffer() : response.json());
        const image = new Image();
        image.src = assetUrl(id+'.png',resources+'textures/cosmetics/pets/');
        this.pets.set(id,Promise.all([data,image.decode()]).then(([model])=>{
          const bake = node => {
            const {vertices,children,...pose} = node;
            return {...pose,mesh:vertices.length ? this.mesh(vertices) : null,children:children.map(bake)};
          };
          return {
            id,texture:this.texture(image),
            rigid:tank ? this.mesh(tankVertices(model)) : null,
            bones:tank ? [] : petBones(model).map(bake),idle:tank ? null : model.idle
          };
        }).catch(error=>{ this.pets.delete(id); throw error; }));
      }
      return this.pets.get(id);
    }

    drawMesh(mesh,texture,model,blended = false) {
      const gl = this.gl;
      if (blended) gl.enable(gl.BLEND);
      else gl.disable(gl.BLEND);
      gl.depthMask(!blended);
      gl.uniform1i(this.blended,blended ? 1 : 0);
      gl.uniformMatrix4fv(this.matrix,false,multiply(this.view,model));
      gl.bindTexture(gl.TEXTURE_2D,texture);
      gl.bindBuffer(gl.ARRAY_BUFFER,mesh.buffer);
      for (const [location,size,offset] of this.attributes) {
        gl.enableVertexAttribArray(location);
        gl.vertexAttribPointer(location,size,gl.FLOAT,false,40,offset);
      }
      gl.drawArrays(gl.TRIANGLES,0,mesh.count);
    }

    drawPet(pet,root,time,after) {
      if (pet.rigid) {
        this.drawMesh(pet.rigid,pet.texture,root);
        return;
      }
      const poses = idlePose(pet.idle,time);
      const walk = (node,parent) => {
        const pose = poses.get(node.name);
        const [x,y,z] = node.pivot, [dx,dy,dz] = pose?.position ?? [0,0,0];
        const angles = node.rotation.map((value,index)=>value+(pose?.rotation?.[index] ?? 0));
        const local = multiply(translate(x+dx,y-dy,z+dz),rotation(angles));
        const world = multiply(parent,pose?.scale ? multiply(local,scale(...pose.scale)) : local);
        if (node.mesh) {
          if (node.name==='visor') after.push(()=>this.drawMesh(node.mesh,pet.texture,world,true));
          else this.drawMesh(node.mesh,pet.texture,world);
        }
        for (const child of node.children) walk(child,world);
      };
      for (const bone of pet.bones) walk(bone,multiply(root,scale(.68/16,.68/16,.68/16)));
    }

    drawPlayer(dance,time) {
      const pose = dance && this.dance ? this.dance[Math.floor(time*50)%this.dance.length] : null;
      const [sway,rise] = pose ?? [0,0];
      const body = pose ? rotation(pose.slice(2,5)) : identity;
      for (const [i,part] of this.player.entries()) {
        const [x,y,z] = part.pivot;
        const offset = [x,y,z];
        let turn = i===2 ? rotateZ(.055) : i===3 ? rotateZ(-.055) : identity;
        if (pose) {
          if (i<4) {
            const moved = transform(body,[x,y-12,z]);
            offset.splice(0,3,moved[0]+sway,moved[1]+12-rise,moved[2]);
            turn = i===0 ? rotation(pose.slice(5,8)) : i===1 ? body
              : multiply(body,rotation(pose.slice(i===2?8:11,i===2?11:14)));
          } else {
            const channel = i===4 ? 14 : 16, angle = radians(pose[channel]);
            offset[0] += sway;
            offset[1] += 12*(1-Math.cos(angle))-pose[channel+1];
            turn = rotateX(angle);
          }
        }
        const local = multiply(translate(offset[0],offset[1]-32,offset[2]),turn);
        const model = multiply(scale(1.8/32,1.8/32,1.8/32),part.mirrored ? multiply(local,scale(-1,1,1)) : local);
        this.drawMesh(part.base,this.skin,model);
        if (part.outer) this.drawMesh(part.outer,this.skin,model);
      }
    }

    draw({yaw = -.55,pitch = .16,slots = {},pet,player = true,time = 0,icon} = {}) {
      const gl = this.gl, {width,height} = this.canvas;
      gl.viewport(0,0,width,height);
      gl.clearColor(0,0,0,0);
      gl.depthMask(true);
      gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
      const widthUnits = icon==='angel'||icon==='dragon' ? 5.55 : icon==='halo'||icon==='hat' ? 1.37
        : !player && pet ? (pet.rigid ? 1.15 : 1.30)
        : slots.wings ? 5.45 : pet ? 2.70 : 1.46;
      const heightUnits = !player ? (icon==='angel'||icon==='dragon' ? 2.5 : widthUnits*.82) : slots.hat ? 2.68 : 2.46;
      const ratio = Math.min(width/widthUnits,height/heightUnits);
      const centerY = icon==='angel'||icon==='dragon' ? -1.35 : icon==='halo' ? -2.01 : icon==='hat' ? -2.08
        : !player && pet ? (pet.rigid ? -.12 : -.28) : slots.hat ? -1.14 : -.96;
      const centerX = player && pet && !slots.wings ? -.25 : 0;
      this.view = multiply(scale(2*ratio/width,-2*ratio/height,.19),
        multiply(rotateX(pitch),multiply(rotateY(yaw),translate(-centerX,-centerY,0))));
      if (player && this.skin) this.drawPlayer(Boolean(slots.dance),time);
      const after = [];
      if (pet) {
        const position = player ? translate(-.89,pet.id==='moon_moth'?-.86:0,.10) : identity;
        this.drawPet(pet,position,time,after);
      }
      const wings = this.cosmetics[slots.wings ?? icon];
      if (wings && !['halo','hat'].includes(icon)) this.drawMesh(wings,this.white,identity);
      if (slots.halo || icon==='halo') after.push(()=>this.drawMesh(this.cosmetics.halo,this.white,identity,true));
      if (slots.hat || icon==='hat') after.push(()=>this.drawMesh(this.cosmetics.hat,this.white,identity,true));
      for (const draw of after) draw();
      gl.depthMask(true);
    }
  }
  window.GleamCosmeticPreview = CosmeticPreview;
})();
