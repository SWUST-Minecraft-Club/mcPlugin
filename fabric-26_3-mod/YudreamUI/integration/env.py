"""Self-locating isolated Folia + Fabric/MinecraftMcp fixture. No shared world or credentials are changed."""
import hashlib, json, os, secrets, shutil, socket, subprocess, sys, uuid
from pathlib import Path
MOD=Path(__file__).resolve().parent.parent
REPO=MOD.parents[1]
ROOT=MOD/'build/integration'
ORIGINAL=REPO/'Minecraft-26_3-plugin/Town/build/integration/resource-client'
SERVER_PORT=25579
MCP_PORT=8875
HTTP_PORT=8877

def identity(name):
 b=bytearray(hashlib.md5(('OfflinePlayer:'+name).encode()).digest());b[6]=(b[6]&15)|48;b[8]=(b[8]&63)|128;return str(uuid.UUID(bytes=bytes(b)))
def arguments():return json.loads((ORIGINAL.parent/'resource-client-launch.json').read_text())
def java():return str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else arguments()[0]
def free(port):
 with socket.socket() as s:s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(('127.0.0.1',port))
def prepare():
 for port in [SERVER_PORT,MCP_PORT,HTTP_PORT]:free(port)
 ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'evidence').mkdir(exist_ok=True)
 dst=ROOT/'server';(dst/'plugins').mkdir(parents=True,exist_ok=True)
 for name in ['libraries','versions','cache']:
  target=dst/name
  if not target.exists():target.symlink_to(REPO/'run'/name,target_is_directory=True)
 shutil.copyfile(REPO/'run/folia-26.3-5.jar',dst/'folia.jar')
 shutil.copyfile(REPO/'run/eula.txt',dst/'eula.txt')
 shutil.copyfile(REPO/'release/YudreamUI/YudreamUI-mc26_3-plugin-Beta_0_1_0.jar',dst/'plugins/YudreamUI.jar')
 properties={'server-ip':'127.0.0.1','server-port':SERVER_PORT,'online-mode':'false','enforce-secure-profile':'false','level-name':'verification','level-type':'minecraft:flat','generator-settings':json.dumps({'layers':[{'block':'minecraft:bedrock','height':1},{'block':'minecraft:dirt','height':2},{'block':'minecraft:grass_block','height':1}],'biome':'minecraft:plains'}),'gamemode':'creative','difficulty':'peaceful','spawn-protection':0,'view-distance':3,'simulation-distance':2,'max-players':2,'enable-rcon':'false','motd':'YudreamUI verification'}
 (dst/'server.properties').write_text('\n'.join(f'{k}={v}' for k,v in properties.items())+'\n')
 (dst/'ops.json').write_text(json.dumps([{'uuid':identity('UiTester'),'name':'UiTester','level':4,'bypassesPlayerLimit':True}]))
 dst=ROOT/'client';(dst/'mods').mkdir(parents=True,exist_ok=True);(dst/'config').mkdir(exist_ok=True)
 for name in ['MinecraftMcp.jar','fabric-api.jar']:shutil.copyfile(ORIGINAL/'mods'/name,dst/'mods'/name)
 shutil.copyfile(REPO/'release/YudreamUI/YudreamUI-mc26_3-fabric-Beta_0_1_0.jar',dst/'mods/YudreamUI.jar')
 for name in ['libraries','assets']:
  if not (dst/name).exists():(dst/name).symlink_to(ORIGINAL/name,target_is_directory=True)
 overrides={'resourcePacks':'[]','incompatibleResourcePacks':'[]','maxFps':'120','renderDistance':'3','simulationDistance':'5','pauseOnLostFocus':'false','fullscreen':'false','autoJump':'false','guiScale':'2','enableVsync':'false','tutorialStep':'none'}
 lines=[key+':'+overrides.pop(key,value) for line in (ORIGINAL/'options.txt').read_text().splitlines() for key,value in [line.split(':',1)]];lines.extend(k+':'+v for k,v in overrides.items());(dst/'options.txt').write_text('\n'.join(lines)+'\n')
 config=dst/'config/minecraft-mcp.json'
 if not config.exists():config.write_text(json.dumps({'enabled':True,'port':MCP_PORT,'token':secrets.token_urlsafe(36),'requestTimeoutMs':15000,'allowCommands':True,'allowChat':True}));config.chmod(0o600)
 (ROOT/'http').mkdir(exist_ok=True)
 print('Prepared isolated fixture; server 25579, MCP 8875, UI assets 8877')
def launch(name):
 dst=ROOT/name
 if name=='server':
  free(SERVER_PORT);fifo=dst/'console.fifo'
  if not fifo.exists():os.mkfifo(fifo,0o600)
  fd=os.open(fifo,os.O_RDWR);args=[java(),'-Xms512m','-Xmx1536m','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','folia.jar','nogui'];env=os.environ.copy()
 elif name=='client':
  free(MCP_PORT);args=arguments();args[0]=java();args[1]='-Xmx1536m';args[1:1]=['-Dyudreamui.allowLocalHttp=true','-Dyudreamui.diagnostics=true']
  for key,value in {'--gameDir':str(dst),'--username':'UiTester','--uuid':identity('UiTester'),'--accessToken':'0','--quickPlayMultiplayer':f'127.0.0.1:{SERVER_PORT}','--width':'1280','--height':'720'}.items():
   if key in args:args[args.index(key)+1]=value
   else:args.extend([key,value])
  env=os.environ.copy();env.pop('WAYLAND_DISPLAY',None);env.update({'XDG_SESSION_TYPE':'x11','SDL_VIDEODRIVER':'x11','SDL_VIDEO_DRIVER':'x11','GLFW_PLATFORM':'x11'});fd=None
 else:raise ValueError(name)
 with (dst/'console.log').open('w') as log:p=subprocess.Popen(args,cwd=dst,stdin=fd,stdout=log,stderr=subprocess.STDOUT,env=env,start_new_session=True)
 if fd is not None:os.close(fd)
 (dst/'pid').write_text(str(p.pid));print(json.dumps({'instance':name,'pid':p.pid}))
def command(text):
 with (ROOT/'server/console.fifo').open('w') as out:out.write(text+'\n')
def stop(name):
 import signal
 pid=int((ROOT/name/'pid').read_text())
 if name=='server':command('stop')
 else:os.kill(pid,signal.SIGTERM)
if __name__=='__main__':
 action=sys.argv[1]
 if action=='prepare':prepare()
 elif action in ['server','client']:launch(action)
 elif action=='command':command(' '.join(sys.argv[2:]))
 elif action=='stop':stop(sys.argv[2])
