#!/usr/bin/env python3
"""Rebuild editable phone defaults; never run against a server's edited menus.

Design coordinates: 800 x 450, top-left origin. Rendering compensation follows
the arcmenu-designing skill; ppb remains 42.7. Requires PyYAML for readable YAML.
"""
from pathlib import Path
import math
import re
import yaml

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/arcmenu'
W, H, PPB = 800, 450, 42.7
G = PPB / 160.5 * 0.86
ZK = 0.00305 * 160.5 / PPB
# MC 26.3 / ArcMenu 3.2, touch mode, 1600 x 900 screenshot calibration.
RECT_WIDTH = 1.01
RECT_HEIGHT = .915
C = dict(bg='#F4F1E8', green='#183E35', sage='#E3ECDE', ink='#283F35',
         muted='#747B6B', brass='#AC8B4E', line='#DCDCCF', white='#FCFBF6',
         gold='#EEE4CC', pale='#EAE8DF', red='#985D48', shell='#222B2A',
         rim='#61675E', dock='#CDDCC4')


def p(key):
    return '{meta:mm-' + key + '}'


def width(text, size):
    text = re.sub(r'&#[0-9a-fA-F]{6}|&[0-9a-fk-or]', '', text)
    advances = {**dict.fromkeys("i!.,:;|'", 2), 'l': 3, 'I': 4, 't': 4,
                ' ': 4, 'f': 5, 'k': 5, '<': 5, '>': 5}
    return max(0, sum(9 if ord(ch) > 0x2e80 else advances.get(ch, 6)
                      for ch in text) - (2.5 if text and ord(text[-1]) > 0x2e80 else 1)) * .1106 * size


class Menu:
    def __init__(self, id, admin=False):
        self.id, self.admin, self.f, self.b = id, admin, {}, {}

    def off(self, x, y, layer):
        s = 1 + ZK * (layer - 3)
        return dict(x=round(G*x/s, 4), y=round(G*y/s, 4), z=layer-3)

    def rect(self, id, x, y, w, h, color, layer=1):
        s, hs = 1 + ZK*(layer-3), h/RECT_HEIGHT
        self.f[id] = dict(type='rectangle', width=round(G*w/s/RECT_WIDTH, 4),
                          height=round(G*hs/s, 4), color=C.get(color, color), opacity=255,
                          offset=self.off(x+w/2-W/2, H/2-(y+h)+hs/2, layer))

    def text(self, id, content, x, cy, size=12, color='ink', align='left', layer=5):
        dynamic = '{' in content or '%' in content
        assert not dynamic or align == 'center', (id, 'Dynamic text must be centered')
        cx = x + (width(content, size)/2 if align == 'left' else
                  -width(content, size)/2 if align == 'right' else 0)
        k = .5125 if any(ord(ch) > 0x2e80 for ch in content) or dynamic else .59
        self.f[id] = dict(type='text', content='&'+C.get(color, color)+content,
                          size=round(G*size/(1+ZK*(layer-3)), 4), alignment='center',
                          opacity=255, **{'line-width': 420, 'update': 20 if dynamic else -1},
                          offset=self.off(cx-W/2, H/2-(cy+k*size), layer))

    def rounded(self, id, x, y, w, h, color, layer=3, radius=10):
        # Non-overlapping bands approximate a pixel-friendly rounded surface.
        # A standard YAML color anchor keeps all bands editable in one place.
        self.rect(id, x, y+radius, w, h-2*radius, color, layer)
        band = radius/4
        for i in range(4):
            inset = radius-math.sqrt(radius**2-(radius-(i+.5)*band)**2)
            self.rect(id+'-top-'+str(i), x+inset, y+i*band, w-2*inset, band, color, layer)
            self.rect(id+'-bottom-'+str(i), x+inset, y+h-(i+1)*band, w-2*inset, band, color, layer)

    def item(self, id, material, x, y, size=28, context='FIXED', layer=4):
        value = round(G*size/(1+ZK*(layer-3)), 4)
        self.f[id] = dict(type='item', material=material, context=context,
                          offset=self.off(x-W/2, H/2-y, layer),
                          scale=dict(x=value, y=value, z=.1 if context == 'GUI' else value))
        # UltimateShop-ArcMenu 1.0.0 OreScene.item: GUI block icons need Y=180.
        # A fixed positive depth also keeps the inventory projection intact.
        if context == 'GUI':
            self.f[id]['rotation'] = dict(x=0, y=180, z=0)

    def region(self, id, x, y, w, h, actions=None, tooltip=None, condition=None, deny=None):
        assert id not in self.f and id not in self.b
        self.b[id] = dict(x=round(G*(x+w/2-W/2), 4), y=round(G*(H/2-y-h/2), 4),
                          width=round(G*w, 4), height=round(G*h, 4), priority=10)
        if actions:
            self.b[id]['actions'] = actions
        if tooltip:
            self.b[id]['tooltip'] = tooltip
            self.b[id]['update'] = 20
        if condition:
            self.b[id]['condition'] = condition
        if deny:
            self.b[id]['deny'] = deny

    def button(self, id, label, x, y, w, h, action, fill='green', text='white', size=9):
        self.rounded(id+'-bg', x, y, w, h, fill, layer=4, radius=min(6, h/4))
        self.text(id+'-label', label, x+w/2, y+h/2, size, text, 'center')
        self.region(id, x, y, w, h, {'all': [action]})

    def save(self):
        doc = {'schema-version': 1, 'id': self.id,
               'permission': 'mainmenu.admin' if self.admin else 'mainmenu.use',
               'open-commands': [], 'main-menu': False,
               'canvas': dict(width=800, height=450, **{'pixels-per-block': PPB}, distance=2),
               'frontend': self.f, 'backend': self.b, 'events': {'open': [], 'close': []}}
        OUT.mkdir(parents=True, exist_ok=True)
        header = ('# Editable native ArcMenu phone UI. Existing server files are never overwritten.\n'
                  '# Change layout, labels, models, tooltips and actions here; use /cd reload to apply.\n'
                  '# Palette: edit the first &phone-<color> anchor; its *phone-<color> aliases follow.\n'
                  '# META values are supplied by MainMenu. See the MainMenu README for the data contract.\n'
                  '# Frontend offsets include measured depth/rectangle compensation; backend uses root coordinates.\n')
        body = yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=120)
        for name, color in C.items():
            token = "color: '"+color+"'"
            if token in body:
                body = body.replace(token, 'color: &phone-'+name+" '"+color+"'", 1)
                body = body.replace(token, 'color: *phone-'+name)
        (OUT/(self.id+'.yml')).write_text(header+body, encoding='utf8')


def feature_actions(target, slot=False):
    route = 'slot' if slot else 'feature'
    return {click: [f'open: mainmenu:{route} {target} {click}']
            for click in ('left', 'right', 'shift-left', 'shift-right', 'middle')}


def base(m, title=None, subtitle=None, back='home'):
    m.rounded('phone-shell', 272, 8, 256, 434, 'shell', 0, 22)
    m.rounded('phone-rim', 274, 10, 252, 430, 'rim', 1, 21)
    m.rounded('window-bg', 279, 15, 242, 420, 'bg', 2, 18)
    m.rounded('camera-island', 385, 23, 30, 8, 'shell', 6, 3)
    m.text('status-brand', 'ARC · 在线', 295, 30, 7.5, 'green')
    for i in range(3):
        m.rect('signal-'+str(i), 472+i*3, 31-i*2, 2, 3+i*2, 'green', 3)
    m.rect('battery-bg', 486, 25, 15, 8, 'green', 3)
    m.rect('battery-cap', 501, 27.5, 2, 3, 'green', 3)
    m.rect('battery-fill', 488, 27, 11, 4, 'bg', 4)
    if title:
        m.text('page-title', title, 400, 64, 16, 'ink', 'center')
        m.text('page-subtitle', subtitle, 400, 87, 8, 'muted', 'center')
        m.text('back-label', '‹', 296, 63, 19, 'green', 'center')
        m.region('header-back', 283, 46, 26, 32, {'all': ['open: mainmenu:'+back]})
    m.rounded('dock-bg', 294, 379, 212, 42, 'dock', 3, 12)
    for id, material, x, label, action in [
        ('apps', 'minecraft:compass', 328, '应用', 'open: mainmenu:apps'),
        ('profile', '<head:%player_name%>', 400, '我的', 'open: mainmenu:profile'),
        ('close', 'minecraft:barrier', 472, '关闭', 'close')]:
        m.item('dock-'+id+'-icon', material, x, 394, 24 if id == 'profile' else 20,
               'GUI' if id == 'profile' else 'FIXED')
        m.text('dock-'+id+'-label', label, x, 411, 7, 'green', 'center')
        m.region('dock-'+id, x-27, 380, 54, 40, {'all': [action]})
    m.rounded('home-indicator', 371, 427, 58, 2.5, 'green', 3, .6)
    m.region('home-gesture', 353, 423, 94, 11, {'all': ['open: mainmenu:home']}, ['&7返回桌面'])


def home():
    m = Menu('mainmenu-home')
    base(m)
    m.item('player-head', '<head:%player_name%>', 309, 67, 40, 'GUI')
    m.text('page-title', p('greeting'), 421, 58, 13, 'green', 'center')
    m.text('player-name', p('player-short'), 421, 79, 10, 'muted', 'center')
    m.region('player-profile', 294, 46, 212, 44, {'all': ['open: mainmenu:profile']})
    m.rounded('clock-widget', 294, 101, 212, 77, 'sage', radius=12)
    m.text('time-value', p('clock'), 400, 124, 34, 'green', 'center')
    m.text('date-value', p('date'), 400, 151, 8.5, 'muted', 'center')
    m.text('online-value', '在线 '+p('online')+' 人', 400, 167, 7.5, 'green', 'center')
    m.region('clock-profile', 294, 101, 212, 77, {'all': ['open: mainmenu:profile']},
             [p('bind-time-tooltip')])
    apps = [
        ('town', '我的小镇', 'minecraft:emerald', 'sage'),
        ('wallet', '我的钱包', 'minecraft:gold_ingot', 'gold'),
        ('music', '音乐电台', 'minecraft:jukebox', 'sage'),
        ('notice', '服务器公告', 'minecraft:bell', 'gold'),
        ('nav-features', '全部应用', 'minecraft:compass', 'sage'),
        ('nav-admin', p('tools-label'), p('tools-material'), 'sage')]
    for i, (id, label, material, fill) in enumerate(apps):
        x, y = 302+(i % 3)*72, 197+(i // 3)*81
        m.rounded(id+'-tile', x, y, 52, 51, fill, radius=10)
        size = {'town': 50, 'wallet': 42, 'music': 46, 'notice': 40}.get(id, 36)
        m.item(id+'-icon', material, x+26, y+25, size,
               'GUI' if id == 'music' else 'FIXED')
        m.text(id+'-label', label, x+26, y+65, 8.5, 'ink', 'center')
        if id.startswith('nav-'):
            route = 'apps' if id == 'nav-features' else 'tools'
            m.region(id, x-5, y-3, 62, 77, {'all': ['open: mainmenu:'+route]},
                     [p('tools-tooltip')] if id == 'nav-admin' else ['&f全部应用', '&7查看所有已接入的功能'])
        else:
            m.region(id+'-open', x-5, y-3, 62, 77, feature_actions(p('bind-'+id+'-id')),
                     [p('bind-'+id+'-tooltip')], p('bind-'+id+'-available')+' == true',
                     ['tell: &7该功能暂不可用。'])
    m.text('desktop-page', '●  ○', 400, 357, 7.5, 'green', 'center')
    m.text('desktop-hint', '点击物品，打开应用', 400, 370, 6.5, 'muted', 'center')
    m.save()


def features():
    m = Menu('mainmenu-features')
    base(m, '应用抽屉', '已接入的功能，都在这里。')
    for index in range(12):
        x, y = 302+(index % 3)*72, 108+(index // 3)*60
        key = 'app-'+str(index)
        m.rounded(key+'-tile', x+3, y, 46, 39, 'white', radius=8)
        m.item(key+'-icon', p(key+'-material'), x+26, y+19, 38, 'GUI')
        m.text(key+'-name', p(key+'-short-name'), x+26, y+48, 7.5, 'ink', 'center')
        m.region(key+'-open', x-3, y, 58, 58, feature_actions(p(key+'-id')),
                 [p(key+'-tooltip')], p(key+'-available')+' == true')
    m.button('apps-previous', '‹', 299, 351, 32, 20, 'open: mainmenu:apps -1', 'pale', 'green', 14)
    m.text('page-count', p('apps-page')+' / '+p('apps-pages'), 400, 359, 9, 'green', 'center')
    m.button('apps-next', '›', 469, 351, 32, 20, 'open: mainmenu:apps 1', 'pale', 'green', 14)
    m.save()


def manage():
    m = Menu('mainmenu-manage', True)
    base(m, '桌面管理', '点击应用，调整显示与位置。')
    for side, key, x in [('left', 'function', 296), ('right', 'fixed', 404)]:
        m.rounded(key+'-tab-bg', x, 103, 100, 24, 'pale', radius=6)
        m.text(key+'-tab-label', p(key+'-tab'), x+50, 115, 9, 'ink', 'center')
        m.region(key+'-tab', x, 103, 100, 24, {'all': ['open: mainmenu:manage '+side]})
    for index in range(16):
        x, y = 297+(index % 4)*53, 137+(index // 4)*47
        key = 'manager-'+str(index)
        m.item(key+'-icon', p(key+'-material'), x+23, y+12, 24, 'GUI')
        m.text(key+'-label', p(key+'-short-name'), x+23, y+29, 6.8, 'ink', 'center')
        m.text(key+'-state', p(key+'-state'), x+23, y+39, 5.5, 'muted', 'center')
        m.region(key+'-edit', x, y-3, 47, 46, {'all': ['open: mainmenu:edit '+p(key+'-id')]},
                 [p(key+'-tooltip')], p(key+'-available')+' == true')
    m.button('manager-previous', '‹', 298, 328, 29, 18,
             'open: mainmenu:manage '+p('manager-side')+' -1', 'pale', 'green', 13)
    m.text('manager-count', p('manager-page')+' / '+p('manager-pages')+' · '+p('manager-count')+' 项',
           400, 337, 8, 'muted', 'center')
    m.button('manager-next', '›', 473, 328, 29, 18,
             'open: mainmenu:manage '+p('manager-side')+' 1', 'pale', 'green', 13)
    m.button('add-placeholder', '+ 占位符', 296, 352, 100, 20, 'open: mainmenu:placeholder-add', 'pale', 'green', 8)
    m.button('add-custom', '+ 新应用', 404, 352, 100, 20, 'open: mainmenu:custom', size=8)
    m.save()


def attributes(mode):
    m = Menu('mainmenu-attribute-'+mode, True)
    base(m, '占位符设置' if mode == 'placeholder' else '应用设置', '每次调整都会自动保存。', 'manage')
    m.rounded('selected-panel', 294, 102, 212, 74, 'sage', radius=10)
    m.item('selected-icon', p('selected-material'), 326, 138, 48, 'GUI')
    m.text('selected-name', p('selected-short-name'), 431, 127, 13, 'ink', 'center')
    m.text('selected-kind', '排序占位' if mode == 'placeholder' else '固定应用' if mode == 'fixed' else '功能应用',
           431, 150, 8, 'muted', 'center')
    m.region('selected-info', 294, 102, 212, 74, tooltip=[p('selected-tooltip')])
    m.rounded('visibility-panel', 294, 187, 212, 49, 'white', radius=8)
    if mode != 'placeholder':
        m.text('visibility-title', '显示状态', 308, 201, 8, 'muted')
        m.text('visibility-value', p('selected-visibility'), 341, 221, 11, 'green', 'center')
        m.button('visibility-toggle', '切换显示', 409, 200, 83, 24, 'open: mainmenu:toggle', size=8)
    else:
        m.text('placeholder-title', '为下一个应用留位', 400, 203, 10, 'ink', 'center')
        m.text('placeholder-description', '占位符只在管理中显示', 400, 222, 8, 'muted', 'center')
    m.rounded('middle-panel', 294, 246, 212, 71, 'white', radius=8)
    if mode == 'fixed':
        m.text('position-title', '当前逻辑位置', 308, 260, 9, 'muted')
        m.text('position-value', p('selected-position'), 400, 279, 8, 'ink', 'center')
        m.button('position-pick', '选择新位置', 308, 291, 184, 19, 'open: mainmenu:position', 'pale', 'green', 8)
    else:
        m.text('order-title', '排序值', 308, 261, 9, 'muted')
        m.text('order-value', p('selected-order'), 474, 260, 14, 'green', 'center')
        for label, x, delta in [('−4', 304, -4), ('−1', 353, -1), ('+1', 402, 1), ('+4', 451, 4)]:
            m.button('order-'+str(delta).replace('-', 'minus'), label, x, 282, 43, 24,
                     f'open: mainmenu:order {delta}', 'pale', 'green', 9)
    m.rounded('layout-panel', 294, 327, 212, 44, 'gold', radius=8)
    m.text('layout-title', '删除占位' if mode == 'placeholder' else '布局方式', 308, 341, 8, 'muted')
    m.text('layout-description', '删除立即生效' if mode == 'placeholder' else
           '固定位置' if mode == 'fixed' else '按顺序排列', 308, 358, 8, 'ink')
    m.button('right-action', '删除' if mode == 'placeholder' else
             '转为固定' if mode == 'function' else '转为功能区', 403, 338, 89, 23,
             'open: mainmenu:placeholder-delete' if mode == 'placeholder' else 'open: mainmenu:fixed',
             'red' if mode == 'placeholder' else 'green', size=8)
    m.save()


FUNCTION = {r*9+c for r in range(2, 5) for c in range(1, 8)}
RESERVED = {27, 35, 49}


def position():
    m = Menu('mainmenu-position', True)
    base(m, '固定位置', '选择应用的逻辑位置。', 'attribute')
    positions = [slot for slot in range(54) if slot not in FUNCTION | RESERVED]
    for index, slot in enumerate(positions):
        x, y = 297+(index % 3)*71, 111+(index // 3)*23
        m.rounded('position-'+str(slot)+'-bg', x, y, 64, 19, 'sage', radius=4)
        m.text('position-'+str(slot)+'-label', f'{slot:02}', x+32, y+9.5, 9, 'green', 'center')
        m.region('position-'+str(slot), x, y, 64, 19, {'all': [f'open: mainmenu:pick {slot}']},
                 [f'&f第 {slot//9+1} 行第 {slot%9+1} 格 · 位置 {slot}',
                  '&7当前功能：'+p('position-'+str(slot)+'-occupant'), '&e点击选择此位置'])
    m.text('position-note', '冲突应用会自动回到功能区', 400, 351, 8, 'muted', 'center')
    m.text('position-note2', '已保留功能区与系统位置', 400, 365, 7, 'muted', 'center')
    m.save()


def custom():
    m = Menu('mainmenu-custom', True)
    base(m, '新建应用', '给新的入口取个名字。', 'manage')
    m.item('create-book', 'minecraft:writable_book', 400, 151, 54)
    m.text('create-heading', '下一段冒险，从这里开始', 400, 204, 12, 'ink', 'center')
    m.text('create-description', '支持中文，最多 50 个字符', 400, 231, 8.5, 'muted', 'center')
    m.button('create-input', '输入名称并创建  →', 304, 262, 192, 31, 'open: mainmenu:input', size=10)
    m.text('create-note', '创建后可继续调整显示与位置', 400, 321, 8, 'muted', 'center')
    m.save()


def profile():
    m = Menu('mainmenu-profile')
    base(m, '我的', '记录你与这个世界的时光。')
    m.item('profile-head', '<head:%player_name%>', 400, 134, 64, 'GUI')
    m.text('profile-name', p('player-short'), 400, 177, 17, 'green', 'center')
    m.text('profile-greeting', p('greeting'), 400, 200, 9, 'muted', 'center')
    m.rounded('playtime-panel', 294, 220, 212, 78, 'sage', radius=10)
    m.item('playtime-icon', 'minecraft:clock', 318, 240, 22)
    m.text('playtime-title', '我的时光', 340, 240, 11, 'green')
    m.text('playtime-value', p('bind-playtime-plain-line-1'), 400, 266, 8, 'ink', 'center')
    m.text('afk-value', p('bind-playtime-plain-line-2'), 400, 284, 8, 'muted', 'center')
    m.region('playtime-info', 294, 220, 212, 78, tooltip=[p('bind-playtime-tooltip')])
    m.rounded('online-panel', 294, 309, 212, 35, 'white', radius=8)
    m.text('server-online', '在线 '+p('online')+' 人 · '+p('apps-count')+' 项应用', 400, 326, 9, 'green', 'center')
    m.button('profile-home', '回到桌面', 294, 353, 212, 20, 'open: mainmenu:home', 'pale', 'green', 8)
    m.save()


if __name__ == '__main__':
    home()
    features()
    manage()
    for mode in ('function', 'fixed', 'placeholder'):
        attributes(mode)
    position()
    custom()
    profile()
    print('Generated 9 editable native YAML phone pages.')
