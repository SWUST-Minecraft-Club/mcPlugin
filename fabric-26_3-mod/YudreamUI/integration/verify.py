"""Integration assertions against a running isolated fixture and the real MinecraftMcp transport."""
import json,sys,time
from pathlib import Path
import mcp
from env import ROOT,command

def wait(ticks=6):mcp.call('test.wait',{'ticks':ticks})
def inspect():return mcp.call('ui.inspect')
def click(label):
 v=inspect();w=next(w for w in v['widgets'] if w['label']==label and w['active'] and w['visible']);return mcp.call('ui.widget_click',{'id':w['id'],'screenToken':v['screenToken']})
def screen(expected):
 actual=inspect()['screen'];assert actual and expected in actual['class'],actual;return actual
def diag(predicate=lambda d:True):
 end=time.monotonic()+5
 while time.monotonic()<end:
  try:
   value=json.loads((ROOT/'client/config/yudreamui/diagnostics.json').read_text())
   if predicate(value):return value
  except (OSError,ValueError):pass
  time.sleep(.05)
 raise AssertionError('Diagnostics predicate timed out')
def element(data,id):return next(e for e in data['elements'] if e['id']==id)
def snap(name):mcp.call('capture.screenshot',{},name+'.png')
def send(text):mcp.call('chat.send',{'message':text});wait()
def report(name,result):
 (ROOT/'evidence'/f'{name}.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(name,json.dumps(result,ensure_ascii=False))
def native():
 send('/yui');screen('UiScreen');d=diag(lambda d:d['revision']>0 and not d['lastError']);assert d['assetRequests']==0,d
 click('本地计数');d=diag(lambda d:element(d,'local-count')['value']=='1')
 click('今日小记');assert mcp.call('ui.type',{'text':'原型热更新🐱'})['acceptedCodepoints']==6
 mcp.call('ui.scroll',{'x':540,'y':270,'vertical':-4});wait(2);click('保存小记')
 d=diag(lambda d:'已保存：原型热更新🐱' in element(d,'status')['value'])
 click('设置');click('小镇通知');click('背景音乐');d=diag(lambda d:element(d,'notifications')['checked']);volume=element(d,'volume')['value']
 click('打开帮助');snap('native-modal');mcp.call('input.key',{'key':'tab'});mcp.call('input.key',{'key':'escape'});screen('UiScreen');click('总览')
 d=diag(lambda d:element(d,'tab-overview')['visible'] and element(d,'overview')['scrollTop']>0)
 snap('native-saved')
 # Resize only the fixture window, identified by its PID.
 import resize
 resize.resize(854,480) if hasattr(resize,'resize') else __import__('subprocess').run([sys.executable,str(Path(__file__).with_name('resize.py')),'854','480'],check=True)
 wait(8);v=inspect();assert v['guiWidth']==427 and any(w['active'] for w in v['widgets']),v
 d=diag(lambda d:element(d,'shell')['width']<450);assert element(d,'nickname')['value']=='原型热更新🐱';assert element(d,'local-count')['value']=='1'
 snap('native-narrow')
 __import__('subprocess').run([sys.executable,str(Path(__file__).with_name('resize.py')),'1280','720'],check=True);wait(8)
 # Focus a button, then activate it with the real key path.
 click('本地计数');mcp.call('input.key',{'key':'enter'});diag(lambda d:element(d,'local-count')['value']=='3')
 report('native-functional',{'passed':True,'chineseEmojiInput':'原型热更新🐱','localCount':3,'resizedGui':'427x240','sliderValue':volume,'builtinRequests':0})
def fallback():
 send('/yui inventory');screen('ContainerScreen');v=inspect();menu=v['menuId'];mcp.call('inventory.click',{'menuId':menu,'slot':9});wait();assert inspect()['menuId']==menu
 summary=mcp.call('item.inspect',{'scope':'container','slot':4})['item']['tooltip'];assert any('购买成功' in s for s in summary),summary
 for action in ['QUICK_MOVE','SWAP','THROW']:
  mcp.call('inventory.click',{'menuId':menu,'slot':9,'action':action,'button':0});wait(2)
 assert mcp.call('inventory.list',{'scope':'container','includeEmpty':False})['carried']['empty']
 assert not mcp.call('inventory.list',{'scope':'player','includeEmpty':False})['slots']
 mcp.call('inventory.click',{'menuId':menu,'slot':10});wait();screen('DialogScreen');click('今日小记');mcp.call('ui.type',{'text':'降级表单🐱'});click('确认');wait(10);screen('ContainerScreen')
 summary=mcp.call('item.inspect',{'scope':'container','slot':4})['item']['tooltip'];assert any('已保存：降级表单🐱' in s for s in summary),summary;snap('fallback-inventory-final')
 send('/yui dialog');screen('DialogScreen');click('购买物资');wait();v=inspect();assert any('购买成功' in w['label'] for w in v['widgets']),v;snap('fallback-dialog-final');mcp.call('input.key',{'key':'escape'});wait();assert inspect()['screen'] is None
 send('/yui trmenu');screen('ContainerScreen');send('/yui status');assert 'backend=inventory' in mcp.call('chat.history',{'limit':1})['messages'][0]['text']
 report('fallback-functional',{'passed':True,'inventoryPatchKeepsContainer':True,'transfersCancelled':True,'form':'降级表单🐱','dialogActions':True,'dialogEscapeCloses':True,'unavailableTrMenuRecovery':'inventory'})
def assets():
 import assets as fixture
 log=ROOT/'client/logs/latest.log'
 reloads=log.read_text().count('Reloading ResourceManager')
 before=diag()['revision'];fixture.select('1');send('/yui refresh');first=diag(lambda d:d['revision']>before and not d['lastError'])
 previous=element(first,'notifications')['checked'];click('设置');click('小镇通知');first=diag(lambda d:element(d,'notifications')['checked']!=previous);checked=element(first,'notifications')['checked'];volume=element(first,'volume')['value']
 click('仓库');mcp.call('ui.scroll',{'x':530,'y':220,'vertical':-5});wait();first=diag(lambda d:element(d,'storage')['scrollTop']>0)
 scroll=element(first,'storage')['scrollTop'];nickname=element(first,'nickname')['value'];count=element(first,'local-count')['value']
 revision=first['revision'];fixture.select('2');send('/yui refresh')
 second=diag(lambda d:d['revision']>revision and not d['lastError'])
 assert element(second,'nickname')['value']==nickname
 assert element(second,'notifications')['checked']==checked
 assert element(second,'volume')['value']==volume
 assert element(second,'local-count')['value']==count
 assert element(second,'storage')['scrollTop']==scroll
 click('总览');snap('assets-version-2');requests=second['assetRequests'];revision=second['revision']
 fixture.select('2',True);send('/yui refresh');bad=diag(lambda d:bool(d['lastError']))
 assert bad['revision']==revision,bad
 click('本地计数');diag(lambda d:element(d,'local-count')['value']==str(int(count)+1))
 fixture.select('2');send('/yui refresh');cached=diag(lambda d:d['revision']>revision and not d['lastError'])
 assert cached['assetRequests']==requests+1,cached
 assert log.read_text().count('Reloading ResourceManager')==reloads
 report('assets-functional',{'passed':True,'imageAndFontRevisions':2,'stateRetained':['input','checkbox','slider','tab','scroll','localCount'],'badHashKeepsPreviousScreen':True,'cacheHits':cached['cacheHits'],'resourcePackReloads':0})
def test_balance():
 import re,decimal
 source=(ROOT/'server/plugins/Wallet/test-ledger.yml').read_text()
 return decimal.Decimal(re.search(r"balances:\n\s+[a-f0-9-]+: '([^']+)'",source).group(1))
def set_test_balance(amount):
 import decimal
 delta=decimal.Decimal(amount)-test_balance()
 if delta:command(f"wallet {'add' if delta>0 else 'take'} UiTester TEST {abs(delta)}")
 for i in range(80):
  wait(2)
  if test_balance()==decimal.Decimal(amount):return
 raise AssertionError('Test wallet adjustment timed out')
def business():
 assert '林间原型镇' not in (ROOT/'server/plugins/Town/towns.yml').read_text(),'business requires a fresh isolated Town fixture'
 command('clear UiTester');set_test_balance('1000');wait(15)
 send('/yui dialogue');screen('UiScreen');click('我想了解这里的生活');wait()
 assert any(w['label']=='我还想问些别的' for w in inspect()['widgets'])
 click('对话记录');diag(lambda d:element(d,'history')['visible']);snap('dialogue-history-final')
 click('我还想问些别的');wait();click('看看你带来的补给');wait(12)
 assert inspect()['screen']['title']=='林间补给站'
 before=set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json'))
 click('挑选 旅途面包');wait();click('查看报价');wait();click('确认付款');wait(30)
 d=diag(lambda d:'购买成功' in element(d,'shop-status')['value'])

 for i in range(80):
  if str(test_balance())=='988.00':break
  wait(2)
 assert str(test_balance())=='988.00',test_balance()
 inventory=mcp.call('inventory.list',{'scope':'player','includeEmpty':False})
 assert sum(s['item']['count'] for s in inventory['slots'] if s['item']['id']=='minecraft:bread')==8,inventory
 added=set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json'))-before
 assert len(added)==1 and json.loads(next(iter(added)).read_text())['status']=='DELIVERED'
 snap('shop-purchase-final')
 # A full storage inventory must fail before an order or a debit is created.
 command('clear UiTester');command('give UiTester minecraft:stone 2304')
 wait(10);click('挑选 旅途面包');wait();click('查看报价');wait();click('确认付款');wait(12)
 diag(lambda d:'背包空间不足' in element(d,'shop-status')['value'])
 assert set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json'))==before|added
 command('clear UiTester');set_test_balance('0');wait(10)
 click('挑选 旅途面包');wait();click('查看报价');wait();click('确认付款');wait(20)
 diag(lambda d:'余额不足' in element(d,'shop-status')['value'])
 declined=set((ROOT/'server/plugins/YudreamUI/shop-orders').glob('*.json'))-before-added
 assert len(declined)==1 and json.loads(next(iter(declined)).read_text())['status']=='DECLINED'
 set_test_balance('1000');wait(10)
 send('/cd');screen('UiScreen');click('小镇');wait(10)
 assert inspect()['screen']['title']=='小镇系统'
 click('创建小镇\u3000→');wait();click('填写并保存镇名；再次点击可修改');wait();click('输入内容')
 mcp.call('ui.type',{'text':'林间原型镇'});click('保存输入');wait(12)
 click('填写并保存镇名；再次点击可修改');wait();assert next(w for w in inspect()['widgets'] if w['label']=='输入内容')['value']=='林间原型镇'
 mcp.call('input.key',{'key':'escape'});wait(12);assert inspect()['screen']['title']=='创建小镇'
 snap('town-draft-final');click('开始筹备');wait(15);snap('town-created-final')
 for i in range(80):
  if '林间原型镇' in (ROOT/'server/plugins/Town/towns.yml').read_text():break
  wait(2)
 assert '林间原型镇' in (ROOT/'server/plugins/Town/towns.yml').read_text()
 # Open claim from a pending town through the normal command; permissions remain Town-owned.
 send('/town claimmap center');wait(10);snap('town-map-final')
 report('business-functional',{'passed':True,'rpgBranchAndHistory':True,'dialogueToShop':True,'walletDebit':'1000 -> 988 TEST','delivery':'8 bread','durableOrder':'DELIVERED','fullInventoryNoOrderOrDebit':True,'insufficientFunds':'DECLINED','mainMenuToTown':True,'townNativeName':'林间原型镇','townDraftConfirmed':True})
def inventory_button(label):
 view=inspect();screen('ContainerScreen');items=mcp.call('inventory.list',{'scope':'container','includeEmpty':False})
 slot=next(s['slot'] for s in items['slots'] if s['slot']<36 and s['item']['name'].startswith(label))
 mcp.call('inventory.click',{'menuId':view['menuId'],'slot':slot});wait(10)
def no_mod():
 assert not (ROOT/'client/mods/YudreamUI.jar').exists()
 command('clear UiTester');set_test_balance('1000')
 for cmd,name in [('/cd','no-mod-mainmenu'),('/town','no-mod-town'),('/yui dialogue','no-mod-dialogue'),('/yui shop','no-mod-shop')]:
  send(cmd);screen('ContainerScreen');snap(name)
 send('/yui status');assert 'client=false backend=inventory' in mcp.call('chat.history',{'limit':1})['messages'][0]['text']
 inventory_button('挑选 钻石头盔');inventory_button('查看报价');screen('DialogScreen');click('确认');wait(12);screen('ContainerScreen')
 inventory_button('确认付款');wait(25);screen('ContainerScreen')
 for i in range(80):
  if str(test_balance())=='974.00':break
  wait(2)
 assert str(test_balance())=='974.00',test_balance()
 inv=mcp.call('inventory.list',{'scope':'player','includeEmpty':False})
 assert sum(s['item']['count'] for s in inv['slots'] if s['item']['id']=='minecraft:diamond_helmet')==1,inv
 snap('no-mod-purchase-final')
 report('no-mod-functional',{'passed':True,'yudreamUiModInstalled':False,'automaticInventory':['MainMenu','Town','RPG dialogue','Wallet shop'],'quantityForm':'vanilla Dialog','migratedCatalog':25,'walletDebit':'1000 -> 974 TEST','delivery':'1 diamond helmet'})
if __name__=='__main__':globals()[sys.argv[1]]()
