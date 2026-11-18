"""Export the five Blockbench meshes and their bone tracks for both client renderers."""
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/orion-client'
for file in sorted(Path(__file__).parent.glob('*.bbmodel')):
    model = json.loads(file.read_text(encoding='utf8'))
    elements = {item['uuid']: item for item in model['elements']}
    groups = {item['uuid']: item for item in model['groups']}
    animation = model['animations'][0]['animators']
    width, height = (model['resolution'][key] for key in ('width', 'height'))

    def bone(node):
        group = groups[node['uuid']]
        children, faces = [], []
        for item in node['children']:
            if isinstance(item, dict):
                children.append(bone(item))
                continue
            mesh = elements[item]
            for face in mesh['faces'].values():
                ids = face['vertices']
                a, b, c = (mesh['vertices'][i] for i in ids[:3])
                u = [j-i for i, j in zip(a,b)]
                v = [j-i for i, j in zip(a,c)]
                normal = [u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
                length = math.hypot(*normal)
                if length < 1e-10:
                    continue
                faces.extend(round(n/length,4) for n in normal)
                for i in (*ids, *([ids[-1]] if len(ids)==3 else [])):
                    faces.extend(mesh['vertices'][i])
                    x,y=face['uv'][i]
                    faces.extend((round(x/width,7),round(y/height,7)))
        tracks={}
        for key in animation.get(node['uuid'],{}).get('keyframes',[]):
            point=key['data_points'][0]
            tracks.setdefault(key['channel'],[]).extend([key['time'],*(point[k] for k in 'xyz')])
        return dict(pivot=group['origin'],faces=faces,children=children,**tracks)

    target = ASSETS/'cosmetics/wings'/('wing_'+file.stem+'.json')
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps([bone(n) for n in model['outliner']],separators=(',',':')),encoding='utf8')
    texture=ASSETS/'textures/cosmetics/wings'/('wing_'+file.stem+'.png')
    texture.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(file.with_suffix('.png'),texture)
