#!/usr/bin/env python3
"""Original, cube-based Minecraft vehicle. No downloaded geometry or textures.

Run from any directory. Pillow is only needed to rebuild the source assets.
The game and plugin use the generated vanilla JSON and PNG files directly.
"""
import base64
import copy
import io
import json
from pathlib import Path
import random
import uuid
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
PACK = ROOT / 'resourcepack'
ASSETS = PACK / 'assets/vehicles'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
PALETTE = {
    'mint': ('#518f82', '#34695f', '#80b4a4'),
    'clay': ('#c07857', '#8b4e3d', '#dda080'),
    'cream': ('#dfceb0', '#a89376', '#efe3ca'),
}
COMMON = {'roof': '#eee3c8', 'ivory': '#f5e9cf', 'brass': '#c7a663',
          'metal': '#535e5c', 'dark': '#293535', 'tyre': '#303b3c',
          'rubber': '#465251', 'seat': '#aa704c', 'seat_dark': '#77533c',
          'wood': '#b58b58', 'wood_light': '#d0ac72', 'headlight': '#ffdf9a',
          'tail': '#bf5946', 'glass': '#98c5ba', 'plate': '#e6ddc6'}


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def texture(name, colour):
    rng = random.Random(name)
    image = Image.new('RGBA', (16, 16), colour)
    px = image.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            noise = rng.choice((-5, -3, 0, 0, 0, 2, 3))
            if name in ('wood', 'wood_light'):
                noise += 7 if y in (1, 7, 13) else -3 if y in (3, 9) else 0
            if name == 'tyre' and (x + y * 2) % 7 < 2:
                noise -= 12
            px[x, y] = tuple(max(0, min(255, v + noise)) for v in (r, g, b)) + (a,)
    if name == 'plate':
        draw = ImageDraw.Draw(image)
        # Tiny hand-drawn leaf badge, entirely original.
        draw.line([(3, 11), (10, 4)], fill='#527b69', width=1)
        draw.polygon([(5, 7), (5, 3), (10, 3), (9, 7)], fill='#6d9b77')
        draw.polygon([(8, 10), (12, 9), (12, 5), (8, 6)], fill='#87a47a')
    path = ASSETS / 'textures/item' / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    return image


def box(name, start, end, material, *, top=None, sides=None, rotation=None):
    faces = {face: {'uv': [0, 0, 16, 16], 'texture': '#' + material} for face in FACES}
    if top:
        faces['up']['texture'] = '#' + top
    if sides:
        for face in ('north', 'south', 'east', 'west'):
            faces[face]['texture'] = '#' + sides
    result = {'name': name, 'from': start, 'to': end, 'faces': faces}
    if rotation:
        result['rotation'] = rotation
    return result


def body():
    parts = []
    def add(name, start, end, mat, **kwargs): parts.append(box(name, start, end, mat, **kwargs))
    add('underside', [-4, 4, -12], [20, 7, 28], 'dark')
    add('lower_body', [-5, 7, -12], [21, 11, 28], 'body', top='body_light')
    add('cream_waistline', [-5.2, 10.6, -12], [21.2, 11.6, 27], 'ivory')
    add('hood', [-4, 11.6, 16], [20, 15, 27], 'body', top='body_light')
    add('hood_centre_seam', [7.75, 15, 17], [8.25, 15.13, 26.5], 'body')
    for x in (-4.6, 18.2):
        add('front_fender', [x, 9, 16], [x + 2.4, 12, 28], 'body', top='body_light')
        add('back_fender', [x, 9, -11], [x + 2.4, 12, 0], 'body', top='body_light')
    for x in (-5.5, 20):
        add('running_board', [x, 6, -.5], [x + 1.5, 7.3, 16], 'metal')
        add('door_panel', [x + .5, 11.7, -4], [x + 1, 17, 13.5], 'body')
        add('door_sill', [x + .35, 16.9, -4], [x + 1.2, 17.8, 13.5], 'body_light')
        add('brass_door_handle', [x + .15, 16.3, -1], [x + 1.4, 16.9, 2], 'brass')
    add('front_bumper', [-6, 6.3, 28], [22, 8.7, 30], 'ivory', top='metal')
    add('rear_bumper', [-6, 6.3, -14], [22, 8.7, -12], 'ivory', top='metal')
    add('grille_surround', [1, 10, 27], [15, 14.6, 28], 'ivory')
    add('dark_grille', [2, 10.7, 28], [14, 14, 28.4], 'dark')
    for x in (3, 5, 7, 9, 11, 13):
        add('grille_slats', [x, 10.8, 28.4], [x + .5, 13.8, 28.65], 'metal')
    for x in (-3.5, 15.5):
        add('lamp_brass_ring', [x, 11, 27], [x + 4, 15, 28.4], 'brass')
        add('warm_headlight', [x + .5, 11.5, 28.4], [x + 3.5, 14.5, 28.6], 'headlight')
        add('lamp_highlight', [x + .9, 13.3, 28.61], [x + 1.6, 14.1, 28.7], 'ivory')
    add('front_leaf_badge', [5.8, 7, 30], [10.2, 9, 30.2], 'plate')
    for x in (-3.5, 16):
        add('rear_lights', [x, 10, -12.4], [x + 3.5, 12, -12], 'tail')
    add('cab_floor', [-3, 11, -5], [19, 11.5, 15.5], 'seat_dark')
    for x in (-2, 10):
        add('seat_cushion', [x, 11.5, -2.5], [x + 8, 13.5, 5.5], 'seat', top='seat')
        add('seat_back', [x, 13, -4.5], [x + 8, 23, -2], 'seat', sides='seat_dark')
        add('seat_back_panel', [x + .6, 14.3, -1.99], [x + 7.4, 21.8, -1.7], 'seat')
        add('seat_piping', [x + .5, 22.5, -2.1], [x + 7.5, 23.2, -1.9], 'wood_light')
    add('dashboard', [-3.5, 17, 12], [19.5, 19.5, 15.5], 'seat_dark', top='wood')
    add('dashboard_instruments', [-.8, 18, 11.8], [5.5, 19.2, 12], 'dark')
    for x in (0, 2.8): add('cream_dials', [x, 18.2, 11.65], [x + 1.5, 19, 11.8], 'ivory')
    add('steering_column', [1.5, 15, 8.5], [2.5, 18.8, 11.5], 'metal')
    for start, end in [([-1, 19, 7], [5, 20, 8]), ([-1, 19, 11], [5, 20, 12]),
                       ([-1, 19, 7], [0, 20, 12]), ([4, 19, 7], [5, 20, 12])]:
        add('steering_rim', start, end, 'seat_dark')
    add('steering_spoke', [1.7, 19, 7.7], [2.3, 20, 11.4], 'brass')
    add('gear_stick', [7.5, 12, 5], [8, 17, 5.5], 'metal')
    add('gear_knob', [7.1, 16.7, 4.6], [8.4, 18, 5.9], 'ivory')
    # An open windscreen gives an unobstructed first-person driving view.
    for x in (-3.8, 18.5):
        add('windscreen_pillar', [x, 17.5, 14.5], [x + 1.3, 29, 15.8], 'ivory')
        add('rear_canopy_pillar', [x, 15, -5.5], [x + 1.3, 29, -4.2], 'ivory')
    add('windscreen_lower_rail', [-3.8, 19.5, 14.7], [19.8, 20.5, 15.6], 'ivory')
    add('short_tinted_windscreen', [-2.5, 20.5, 15.05], [18.5, 22, 15.35], 'glass')
    add('canopy_under_edge', [-5, 28, -7], [21, 29.3, 17], 'body')
    add('cream_canvas_roof', [-5.5, 29.3, -7.5], [21.5, 30.7, 17.5], 'roof', top='ivory')
    add('roof_centre_panel', [-3.5, 30.7, -5.5], [19.5, 31.1, 15.5], 'roof')
    for x in (-7.1, 21.6):
        add('mirror_arm', [x, 20.2, 12.7], [x + 1.5, 20.8, 14], 'brass')
        add('wing_mirror', [x - .6, 19.8, 12.7], [x + 1.7, 22.8, 13.5], 'metal')
        add('mirror_face', [x - .2, 20.2, 12.45], [x + 1.3, 22.4, 12.7], 'glass')
    add('rear_wooden_deck', [-3.5, 11.6, -11.5], [19.5, 12.4, -5.5], 'wood')
    for y in (13, 15.8):
        for x in (-3.5, 18.5): add('cargo_side_slats', [x, y, -11.5], [x + 1, y + 2, -5.5], 'wood_light')
        add('cargo_tail_slats', [-3.5, y, -11.5], [19.5, y + 2, -10.5], 'wood')
    for x in (-3.6, 7.5, 18.6):
        add('cargo_brass_straps', [x, 12.3, -11.65], [x + .9, 18.3, -10.4], 'brass')
    add('rear_number_plate', [5.5, 8.9, -14.3], [10.5, 11.1, -14], 'plate')
    return parts


def wheel():
    parts = []
    for i, (a, b, c, d) in enumerate(((3, 13, 5, 11), (5, 11, 3, 13), (4, 12, 4, 12))):
        parts.append(box('octagonal_tyre_' + str(i), [6, a, c], [10, b, d], 'tyre'))
    for side in (5.8, 10):
        parts.append(box('cream_rim', [side, 5, 5], [side + .2, 11, 11], 'ivory'))
        parts.append(box('inset_rim', [side - .02, 6, 6], [side + .23, 10, 10], 'metal'))
        parts.append(box('brass_spoke', [side - .06, 7.5, 5.7], [side + .27, 8.5, 10.3], 'brass'))
        parts.append(box('brass_spoke', [side - .06, 5.7, 7.5], [side + .27, 10.3, 8.5], 'brass'))
        parts.append(box('hub_cap', [side - .1, 7, 7], [side + .3, 9, 9], 'ivory'))
    parts.append(box('tread_marker', [6.3, 12.9, 7], [9.7, 13.1, 8], 'rubber'))
    return parts


def item_model(name, elements, textures):
    textures = dict(textures)
    textures['particle'] = textures.get('body', 'tyre')
    model = {'credit': 'Original Town Rover by zhou-BZDDX / Codex, MIT',
             'textures': {k: 'vehicles:item/' + v for k, v in textures.items()},
             'elements': elements,
             'display': {'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]}}}
    save_json(ASSETS / 'models/item' / (name + '.json'), model)
    save_json(ASSETS / 'items' / (name + '.json'), {'model': {'type': 'minecraft:model', 'model': 'vehicles:item/' + name}})
    return model


def blockbench(body_elements, wheel_elements, images):
    keys = list(images)
    textures = []
    for i, key in enumerate(keys):
        buffer = io.BytesIO(); images[key].save(buffer, format='PNG')
        textures.append({'name': key + '.png', 'id': str(i), 'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'vehicles:texture/' + key)),
                         'width': 16, 'height': 16, 'uv_width': 16, 'uv_height': 16,
                         'source': 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()})
    elements, outliner = [], []
    for name, cubes, offset in [('body', body_elements, (0, 0, 0))] + [
            ('wheel_' + str(i), wheel_elements, (x * 16, -3, z * 16))
            for i, (x, z) in enumerate(((-.76, -.82), (.76, -.82), (-.76, .83), (.76, .83)))]:
        children = []
        for i, cube in enumerate(cubes):
            element = copy.deepcopy(cube)
            element['uuid'] = str(uuid.uuid5(uuid.NAMESPACE_URL, 'vehicles:' + name + '/' + str(i)))
            element['type'] = 'cube'
            for coord in ('from', 'to'):
                element[coord] = [value + offset[axis] for axis, value in enumerate(element[coord])]
            for face in element['faces'].values():
                key = face['texture'][1:]
                key = {'body': 'mint_body', 'body_light': 'mint_light', 'body_dark': 'mint_dark'}.get(key, key)
                face['texture'] = keys.index(key)
            children.append(element['uuid']); elements.append(element)
        outliner.append({'name': name, 'origin': [8 + offset[0], 8 + offset[1], 8 + offset[2]],
                         'uuid': str(uuid.uuid5(uuid.NAMESPACE_URL, 'vehicles:bone/' + name)), 'children': children})
    save_json(ROOT / 'source/town_rover.bbmodel', {
        'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
        'name': 'Town Rover · 小镇漫游车', 'resolution': {'width': 16, 'height': 16},
        'elements': elements, 'outliner': outliner, 'textures': textures})


def preview(models, wheel_elements, images):
    import math
    scale = 3
    image = Image.new('RGB', (1400 * scale, 900 * scale), '#efece4')
    draw = ImageDraw.Draw(image)
    def text(x, y, value, size, fill):
        try:
            font = ImageFont.truetype('DejaVuSans.ttf', size * scale)
        except OSError:
            font = ImageFont.load_default(size=size * scale)
        draw.text((x * scale, y * scale), value, font=font, fill=fill)
    text(72, 55, 'TOWN ROVER', 37, '#263f36')
    text(74, 109, '01 / TWO SEATS / THREE COLOURS', 13, '#7b8276')
    draw.line((74 * scale, 149 * scale, 1326 * scale, 149 * scale), fill='#d7d7ca', width=scale)

    def car(model, cx, cy, size, yaw=-35):
        cubes = copy.deepcopy(model['elements'])
        for wx, wz in ((-.76, -.82), (.76, -.82), (-.76, .83), (.76, .83)):
            for cube in wheel_elements:
                e = copy.deepcopy(cube)
                for key in ('from', 'to'): e[key] = [e[key][0] + wx * 16, e[key][1] - 3, e[key][2] + wz * 16]
                cubes.append(e)
        angle = math.radians(yaw)
        def project(p):
            x, y, z = p[0] - 8, p[1], p[2] - 8
            rx, rz = math.cos(angle) * x - math.sin(angle) * z, math.sin(angle) * x + math.cos(angle) * z
            return ((cx + rx * size) * scale, (cy + rz * size * .42 - y * size * .89) * scale, rz + y * .15)
        faces = []
        for cube in cubes:
            x0, y0, z0 = cube['from']; x1, y1, z1 = cube['to']
            for name, vertices, light in [
                ('up', [(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)], 1.12),
                ('down', [(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)], .7),
                ('north', [(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)], .72),
                ('south', [(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)], .9),
                ('east', [(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)], .85),
                ('west', [(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)], .76)]:
                points = [project(p) for p in vertices]
                area = sum(points[i][0]*points[(i+1)%4][1]-points[(i+1)%4][0]*points[i][1] for i in range(4))
                if area >= 0: continue
                key = cube['faces'][name]['texture'][1:]
                key = model['textures'][key].split('/')[-1]
                rgb = images[key].getpixel((8,8))[:3]
                colour = tuple(min(255, int(v * light)) for v in rgb)
                faces.append((sum(p[2] for p in points)/4, points, colour))
        shadow = (cx - size*26, cy-size*6, cx+size*26, cy+size*10)
        draw.ellipse(tuple(int(v*scale) for v in shadow), fill='#d9d9cc')
        for _, points, colour in sorted(faces, key=lambda f:f[0]):
            draw.polygon([(p[0],p[1]) for p in points], fill=colour)
    car(models['mint'], 405, 550, 10.2, -35)
    car(models['clay'], 1050, 386, 5.5, -32)
    car(models['cream'], 1050, 690, 5.5, -32)
    text(84, 771, 'MINT / 01', 18, '#426f60')
    text(84, 806, 'Cream canopy, warm brass, oak cargo bed.', 16, '#737c70')
    text(916, 442, 'CLAY / 02', 15, '#a96c4d')
    text(916, 744, 'CREAM / 03', 15, '#8a785f')
    text(84, 854, 'ORIGINAL CUBE MODEL  /  VANILLA RESOURCE PACK  /  MIT', 11, '#929789')
    image.resize((1400, 900), Image.Resampling.LANCZOS).save(ROOT / 'preview.png')


def main():
    images = {name: texture(name, colour) for name, colour in COMMON.items()}
    for colour, values in PALETTE.items():
        for suffix, value in zip(('body', 'dark', 'light'), values):
            images[colour + '_' + suffix] = texture(colour + '_' + suffix, value)
    save_json(PACK / 'pack.mcmeta', {'pack': {'pack_format': 97, 'min_format': [97, 1], 'max_format': [97, 1],
                                           'description': 'Town Rover · 小镇漫游车 / Vehicles Beta_0_1_0'}})
    body_elements, wheel_elements = body(), wheel()
    models = {}
    for colour in PALETTE:
        mapping = {name: name for name in COMMON}
        mapping.update(body=colour+'_body', body_dark=colour+'_dark', body_light=colour+'_light')
        models[colour] = item_model('town_car_' + colour, body_elements, mapping)
    item_model('town_wheel', wheel_elements, {name: name for name in COMMON})
    blockbench(body_elements, wheel_elements, images)
    preview(models, wheel_elements, images)
    print(f'Generated {len(body_elements)} body cubes + {len(wheel_elements)} cubes per wheel; 3 colours.')


if __name__ == '__main__': main()
