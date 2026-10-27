"""Use the repository's real Fabric MCP HTTP transport; never log its bearer token."""
import base64,importlib.util,json,sys,time
from pathlib import Path
MOD=Path(__file__).resolve().parent.parent;REPO=MOD.parents[1];ROOT=MOD/'build/integration'
spec=importlib.util.spec_from_file_location('minecraft_smoke',REPO/'scripts/minecraft-mcp-smoke.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
def connect():
 config=json.loads((ROOT/'client/config/minecraft-mcp.json').read_text());client=smoke.Client(f'http://127.0.0.1:{config["port"]}/mcp',config['token']);hello=client.rpc('initialize',{'protocolVersion':client.protocol,'capabilities':{},'clientInfo':{'name':'yudreamui-verification','version':'0.1'}});client.protocol=hello['protocolVersion'];client.rpc('notifications/initialized',notification=True);return client
def call(name,args=None,screenshot=None):
 start=time.perf_counter_ns();result=connect().tool(name,**(args or {}));elapsed=time.perf_counter_ns()-start
 if result.get('isError'):raise RuntimeError(result)
 data=result.get('structuredContent')
 if screenshot:
  content=next(c for c in result['content'] if c['type']=='image');p=ROOT/'evidence'/screenshot;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(base64.b64decode(content['data']));data={'image':str(p.relative_to(ROOT)),'metadata':data}
 elif data is None:data=[c for c in result.get('content',[]) if c['type']!='image']
 with (ROOT/'evidence/mcp-transcript.jsonl').open('a') as out:out.write(json.dumps({'time':time.time(),'tool':name,'arguments':args or {},'elapsedNanos':elapsed,'result':data},ensure_ascii=False)+'\n')
 return data
if __name__=='__main__':print(json.dumps(call(sys.argv[1],json.loads(sys.argv[2]) if len(sys.argv)>2 else {},sys.argv[3] if len(sys.argv)>3 else None),ensure_ascii=False,indent=2))
