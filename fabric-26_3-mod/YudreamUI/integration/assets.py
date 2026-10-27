"""Loopback-only asset fixtures; colors are generated, fonts are copied from explicit local paths."""
import argparse,hashlib,json,shutil,struct,subprocess,zlib
from pathlib import Path
from env import ROOT,HTTP_PORT,free

def png(color):
 width,height=256,40
 rows=b''.join(b'\0'+b''.join(bytes(tuple(min(255,c+(12 if (x//16+y//8)%2 else 0)) for c in color)+(255,)) for x in range(width)) for y in range(height))
 def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b'')
def prepare(first,second):
 root=ROOT/'http';root.mkdir(exist_ok=True)
 for version,color,font in [('1',(62,118,73),first),('2',(179,100,37),second)]:
  (root/f'banner-{version}.png').write_bytes(png(color));shutil.copyfile(font,root/f'display-{version}.ttf')
 print('Prepared 2 image/font revisions')
def select(version,invalid=False):
 root=ROOT/'http';assets=[]
 for id,kind,file in [('banner','image',f'banner-{version}.png'),('display','font',f'display-{version}.ttf')]:
  sha=hashlib.sha256((root/file).read_bytes()).hexdigest()
  assets.append({'id':id,'kind':kind,'hash':('0'*64 if invalid and id=='banner' else sha),'url':f'http://127.0.0.1:{HTTP_PORT}/{file}'})
 config=ROOT/'server/plugins/YudreamUI/config.yml'
 config.write_text('assets: '+json.dumps(assets)+'\nfallback:\n  backend: inventory\n  trmenu-id: yudreamui\n')
 print('Selected asset revision',version,'(intentional bad hash)' if invalid else '')
def serve():
 free(HTTP_PORT)
 with (ROOT/'http.log').open('w') as log:
  p=subprocess.Popen(['python3','-m','http.server',str(HTTP_PORT),'--bind','127.0.0.1','--directory',str(ROOT/'http')],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 (ROOT/'http.pid').write_text(str(p.pid));print('Asset fixture PID',p.pid)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('action',choices=['prepare','select','serve']);p.add_argument('values',nargs='*');p.add_argument('--invalid',action='store_true');a=p.parse_args()
 if a.action=='prepare':prepare(*a.values)
 elif a.action=='select':select(a.values[0],a.invalid)
 else:serve()
