"""Dense presentation regression against the isolated TEST-wallet fixture only."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import yaml
import mcp
import verify as v
from env import ROOT, REPO, HTTP_PORT


def shot(command, name):
    v.send(command)
    v.wait(12)
    v.screen('UiScreen')
    v.diag(lambda d: d['density'] == 2 and not d['lastError'])
    mcp.call('ui.scroll', {'x': 1, 'y': 1, 'vertical': 0})
    v.snap(name)


def resize(width, height):
    subprocess.run([sys.executable, str(Path(__file__).with_name('resize.py')), str(width), str(height)], check=True)
    v.wait(8)


def visual():
    shot('/cd', 'atlas-mainmenu')
    v.click('02\u3000探索指南')
    v.click('01\u3000总览')
    v.click('系统商店')
    v.wait(12)
    v.snap('atlas-shop')
    before = v.diag()
    v.click('工具 · 6')
    filtered = v.diag(lambda d: not v.element(d, 'card-arc-0')['visible'] and v.element(d, 'card-arc-14')['visible'])
    assert filtered['revision'] == before['revision'], 'filter must stay local'
    v.snap('atlas-shop-tools')
    v.click('全部')
    v.click('挑选 钻石头盔')
    v.wait()
    v.snap('atlas-shop-quantity')
    v.click('查看报价')
    v.wait()
    v.snap('atlas-shop-confirm')
    v.click('取消并返回')
    shot('/yui dialogue', 'atlas-dialogue')
    v.click('我想了解这里的生活')
    v.wait()
    v.click('对话记录')
    v.snap('atlas-dialogue-history')
    shot('/town', 'atlas-town')
    shot('/town claimmap center', 'atlas-town-map')
    resize(854, 480)
    for command, name in [('/cd', 'atlas-mainmenu-narrow'), ('/yui shop', 'atlas-shop-narrow'), ('/yui dialogue', 'atlas-dialogue-narrow'), ('/town claimmap center', 'atlas-town-map-narrow')]:
        shot(command, name)
    resize(1280, 720)
    v.report('atlas-visual', {'passed': True, 'density': 2, 'localFilterSameRevision': True, 'windows': ['1280x720', '854x480'], 'nativePages': ['MainMenu', 'Wallet shop', 'RPG', 'Town', '7x7 territory'], 'externalRequests': before['assetRequests']})


def dialogue():
    bounds = []
    for width, height, suffix in [(1280, 720, ''), (854, 480, '-narrow')]:
        resize(width, height)
        shot('/yui dialogue', 'atlas-dialogue' + suffix)
        d = v.diag(lambda d: any(e['id'] == 'dialogue-panel' for e in d['elements']))
        frame = v.element(d, 'conversation')
        panel = v.element(d, 'dialogue-panel')
        assert frame['y'] >= height * .55 and frame['height'] <= height * .42 + 1
        for i in range(3):
            choice = v.element(d, 'choice' + str(i))
            assert choice['visible'] and choice['clipHeight'] >= choice['height'] - 1
        v.click('我想了解这里的生活')
        v.wait()
        v.click('对话记录')
        history = v.element(v.diag(lambda d: v.element(d, 'history')['visible']), 'history')
        assert history['y'] >= panel['y'] and history['y'] + history['height'] <= panel['y'] + panel['height'] + 1
        v.snap('atlas-dialogue-history' + suffix)
        v.click('对话记录')
        v.click('我还想问些别的')
        v.wait()
        v.click('我准备好出发了')
        v.wait()
        v.click('下次见')
        v.wait()
        assert v.inspect()['screen'] is None
        v.snap('atlas-dialogue-world' + suffix)
        bounds.append({'window': f'{width}x{height}', 'top': frame['y'], 'height': frame['height'], 'clearAboveFraction': round(frame['y'] / height, 3)})
    resize(1280, 720)
    v.send('/yui dialogue')
    v.click('看看你带来的补给')
    v.wait()
    assert any(w['label'] == '挑选 钻石头盔' for w in v.inspect()['widgets'])
    v.report('atlas-dialogue', {'passed': True, 'bounds': bounds, 'allThreeChoicesVisible': True, 'historyWithinPanel': True, 'branchAndClose': True, 'shopNavigation': True})


def terrain():
    resize(1280, 720)
    v.send('/town claimmap center')
    def view(d):
        e = v.element(d, 'claim-terrain')
        return next((m for m in d['terrain']['views'] if m['chunkX'] == e['chunkX'] and m['chunkZ'] == e['chunkZ']), {'loaded': 0, 'missing': 0, 'uniqueColors': 0})
    d = v.diag(lambda d: 'terrain' in d and bool(d['terrain']['views']) and view(d)['loaded'] > 0)
    start = view(d)
    assert start['chunkX'] == -1 and start['chunkZ'] == -1, 'fixture player must be in chunk -1,-1'
    # Small, reversible surface samples in the isolated flat verification world.
    for command in ['fill -24 -61 -24 -19 -61 -19 minecraft:sand', 'fill -10 -60 -24 -5 -58 -19 minecraft:stone', 'fill -30 -61 -12 -22 -61 -4 minecraft:water']:
        v.command(command)
    d = v.diag(lambda d: view(d)['uniqueColors'] >= 4)
    v.wait(50)
    d = v.diag()
    before = view(d)['colorHash']
    v.snap('atlas-terrain-before')
    from PIL import Image
    frame = v.element(d, 'claim-terrain')
    probe = (round(frame['x'] + 42.5 / 112 * frame['width']), round(frame['y'] + 42.5 / 112 * frame['height']))
    sand_color = Image.open(ROOT/'evidence/atlas-terrain-before.png').getpixel(probe)
    uploads = d['terrain']['uploads']
    revision = d['revision']
    v.wait(45)
    d = v.diag()
    assert d['terrain']['uploads'] == uploads, 'unchanged map should reuse GPU texture'
    v.command('fill -24 -61 -24 -19 -61 -19 minecraft:red_concrete')
    changed = v.diag(lambda d: view(d)['colorHash'] != before)
    assert changed['revision'] == revision, 'local terrain changes should not reopen the UI'
    v.snap('atlas-terrain-change')
    assert Image.open(ROOT/'evidence/atlas-terrain-change.png').getpixel(probe) != sand_color
    v.command('fill -24 -61 -24 -19 -61 -19 minecraft:sand')
    v.wait(55)
    v.snap('atlas-town-map')
    assert Image.open(ROOT/'evidence/atlas-town-map.png').getpixel(probe) == sand_color
    v.click('右移 ▶')
    moved = v.diag(lambda d: v.element(d, 'claim-terrain')['chunkX'] == start['chunkX'] + 3)
    v.click('右移 ▶')
    distant = v.diag(lambda d: v.element(d, 'claim-terrain')['chunkX'] == start['chunkX'] + 6 and view(d)['missing'] > 0)
    v.snap('atlas-terrain-unloaded')
    v.click('回到我的位置')
    v.diag(lambda d: v.element(d, 'claim-terrain')['chunkX'] == -1)
    v.click('购买区块 3,3')
    v.wait()
    confirmation = v.diag(lambda d: '购买区块 -1, -1' in v.element(d, 'legacy-body')['label'] and 'verification' in v.element(d, 'legacy-body')['label'])
    v.snap('atlas-terrain-confirm')
    v.send('/town claimmap center')
    resize(854, 480)
    d = v.diag(lambda d: v.element(d, 'claim-terrain')['width'] <= 300)
    e = v.element(d, 'claim-terrain')
    assert e['clipWidth'] == e['width'] and e['clipHeight'] == e['height']
    v.snap('atlas-town-map-narrow')
    resize(1280, 720)
    v.report('atlas-terrain', {'passed': True, 'center': [-1, -1], 'surfaceColors': start['uniqueColors'], 'localUpdateSameRevision': True, 'unchangedTextureReused': True, 'panChunks': 6, 'unloadedShown': view(distant)['missing'], 'confirmationCoordinates': True, 'narrowMapUnclipped': True, 'externalRequests': confirmation['assetRequests'], 'maxChunkSampleNanos': confirmation['terrain']['maxChunkSampleNanos']})


def purchase():
    v.command('clear UiTester')
    v.set_test_balance('1000')
    v.send('/yui shop')
    before = set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json'))
    v.click('挑选 钻石头盔')
    v.wait()
    v.click('查看报价')
    v.wait()
    v.click('确认付款')
    v.wait(30)
    v.diag(lambda d: '购买成功' in v.element(d, 'shop-status')['value'])
    end = time.monotonic() + 8
    while time.monotonic() < end and str(v.test_balance()) != '974.00':
        v.wait(2)
    assert str(v.test_balance()) == '974.00'
    added = set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json')) - before
    assert len(added) == 1 and json.loads(next(iter(added)).read_text())['status'] == 'DELIVERED'
    inventory = mcp.call('inventory.list', {'scope': 'player', 'includeEmpty': False})
    assert sum(s['item']['count'] for s in inventory['slots'] if s['item']['id'] == 'minecraft:diamond_helmet') == 1
    v.snap('atlas-purchase')
    v.report('atlas-purchase', {'passed': True, 'balance': '1000 -> 974 TEST', 'delivered': '1 diamond helmet', 'order': 'DELIVERED'})


def inputs():
    v.send('/arcmenu open mainmenu:input')
    v.click('输入内容')
    assert mcp.call('ui.type', {'text': '方块日常🐱'})['acceptedCodepoints'] == 5
    v.diag(lambda d: v.element(d, 'value')['value'] == '方块日常🐱')
    v.snap('atlas-input')
    v.click('取消并返回')
    v.wait()
    v.send('/yui shop')
    v.click('挑选 钻石头盔')
    v.wait()
    v.click('购买份数')
    mcp.call('input.key', {'key': 'right'})
    d = v.diag(lambda d: float(v.element(d, 'quantity')['value']) > 1)
    value = int(float(v.element(d, 'quantity')['value']))
    assert v.element(d, 'quantity-value')['value'] == str(value)
    v.snap('atlas-quantity-value')
    v.report('atlas-input', {'passed': True, 'denseChineseEmoji': '方块日常🐱', 'quantityValue': value, 'labelMatches': True})


def hot():
    import assets
    assets.serve()
    config = ROOT/'server/plugins/YudreamUI/config.yml'
    original = config.read_bytes()
    base = yaml.safe_load(original)
    log = ROOT/'client/logs/latest.log'
    reloads = log.read_text().count('Reloading ResourceManager')
    source = REPO/'fabric-26_3-mod/YudreamUI/runtime/src/main/resources/builtin/art/village.png'
    shutil.copyfile(source, ROOT/'http/atlas-village.png')
    try:
        v.send('/yui shop')
        v.click('工具 · 6')
        before = v.diag(lambda d: not v.element(d, 'card-arc-0')['visible'])
        def select(filename, invalid=False):
            digest = hashlib.sha256((ROOT/'http'/filename).read_bytes()).hexdigest()
            base['presentation-assets'] = [{'id': 'atlas-village', 'hash': '0'*64 if invalid else digest, 'url': f'http://127.0.0.1:{HTTP_PORT}/{filename}'}]
            config.write_text(yaml.safe_dump(base))
            v.send('/yui theme')
        select('atlas-village.png')
        first = v.diag(lambda d: d['revision'] > before['revision'] and not d['lastError'])
        assert not v.element(first, 'card-arc-0')['visible'] and v.element(first, 'card-arc-14')['visible']
        select('banner-2.png')
        second = v.diag(lambda d: d['revision'] > first['revision'] and not d['lastError'])
        assert not v.element(second, 'card-arc-0')['visible']
        v.snap('atlas-hot-replacement')
        select('banner-2.png', True)
        failed = v.diag(lambda d: bool(d['lastError']))
        assert failed['revision'] == second['revision']
        v.click('全部')
        select('atlas-village.png')
        recovered = v.diag(lambda d: d['revision'] > second['revision'] and not d['lastError'])
        assert v.element(recovered, 'card-arc-0')['visible']
        assert log.read_text().count('Reloading ResourceManager') == reloads
        v.report('atlas-hot', {'passed': True, 'filterRetained': True, 'badHashRetainsPage': True, 'cacheHits': recovered['cacheHits'], 'resourcePackReloads': 0})
    finally:
        config.write_bytes(original)
        v.send('/yui theme')
        import os, signal
        os.kill(int((ROOT/'http.pid').read_text()), signal.SIGTERM)


if __name__ == '__main__':
    globals()[sys.argv[1]]()
