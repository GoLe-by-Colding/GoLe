import base64,json,subprocess
from pathlib import Path
PAGE='6e9496cd-bd2e-468b-b068-d90d37296029'
def call(*args):
 r=subprocess.run(['orca',*args,'--page',PAGE,'--json'],capture_output=True,text=True)
 d=json.loads(r.stdout)
 if not d.get('ok'):raise RuntimeError(d)
 return d['result']
def ev(js):
 v=call('eval','--expression',js)['result']
 try:return json.loads(v)
 except:return v
fixture=Path('/tmp/gole-brick-filter-fixture.png')
subprocess.run(['/tmp/gole-brick-filter-venv/bin/python','-c','from PIL import Image; Image.new("RGB",(64,64),"blue").save("/tmp/gole-brick-filter-fixture.png")'],check=True)
encoded=base64.b64encode(fixture.read_bytes()).decode()
js='''(()=>{const original=window.fetch.bind(window);const bytes=Uint8Array.from(atob("BASE64"),c=>c.charCodeAt(0));window.__brickMock={jobs:[],posts:[],fail:false,loseReceipt:false};window.fetch=async(input,init)=>{const url=String(input);if(!url.includes('/api/v1/brick-filter'))return original(input,init);const m=window.__brickMock;if(url.endsWith('/quota'))return Response.json({day:'2026-09-08',remaining:3-m.jobs.filter(j=>j.status==='SUCCEEDED').length,limit:3,enabled:true,retryAvailable:true,resetsAt:'2026-09-08T15:00:00Z'});if(init?.method==='POST'){const id=init.headers['Idempotency-Key'];m.posts.push(id);let job=m.jobs.find(j=>j.id===id);if(!job){job={id,mode:init.body.get('mode'),status:m.fail?'FAILED':'SUCCEEDED',leaseUntil:'2026-09-08T15:00:00Z',resultUntil:'2026-09-09T15:00:00Z'};m.jobs.unshift(job);}await new Promise(r=>setTimeout(r,300));if(m.loseReceipt){m.loseReceipt=false;throw new TypeError('mock lost receipt');}return Response.json(job);}if(url.endsWith('/jobs'))return Response.json(m.jobs);if(url.endsWith('/result'))return new Response(bytes,{headers:{'Content-Type':'image/png'}});const id=decodeURIComponent(url.split('/').at(-1));const job=m.jobs.find(j=>j.id===id);return job?Response.json(job):Response.json({code:'BRICK_NOT_FOUND',message:'not found'},{status:404});};return true;})()'''.replace('BASE64',encoded)
assert ev(js)
def click(name):
 call('snapshot')
 ev('([...document.querySelectorAll("button")].find(b=>b.textContent==='+json.dumps(name)+')).click()')
click('횟수 다시 확인')
call('wait','--text','오늘 남은 횟수 3 / 3')
refs=call('snapshot')['refs'];key=next(k for k,v in refs.items() if v['name'].startswith('PNG/JPEG/HEIF'))
call('upload','--element','@'+key,'--files',str(fixture))
call('snapshot')
ev('document.querySelector("input[type=checkbox]").click()')
ev('window.__brickMock.loseReceipt=true')
click('브릭 이미지 만들기')
call('wait','--text','응답을 확인하지 못했습니다.')
click('요청 상태 다시 확인')
call('wait','--text','오늘 남은 횟수 2 / 3')
call('wait','--selector','img[alt="생성된 브릭 이미지"]')
results={'successAndLostReceipt':ev('({posts:window.__brickMock.posts.length,result:!!document.querySelector("img[alt=\\"생성된 브릭 이미지\\"]"),remaining:document.body.innerText.includes("오늘 남은 횟수 2 / 3")})')}
assert results['successAndLostReceipt']['posts']==1 and results['successAndLostReceipt']['result']
ev('window.__brickMock.fail=true;document.querySelector("input[value=BRICK_OBJECT]").click()')
click('브릭 이미지 만들기')
call('wait','--text','예약 횟수를 돌려드렸어요.')
results['failure']=ev('({mode:window.__brickMock.jobs[0].mode,status:window.__brickMock.jobs[0].status,remaining:document.body.innerText.includes("오늘 남은 횟수 2 / 3")})')
assert results['failure']['mode']=='BRICK_OBJECT' and results['failure']['remaining']
for name,w,h in [('mobile',226,488),('desktop',834,579)]:
 call('exec','--command',f'set viewport {w} {h}')
 results[name]=ev('({width:innerWidth,height:innerHeight,overflow:document.documentElement.scrollWidth>innerWidth,mainCount:document.querySelectorAll("main").length})')
Path('.kiro/specs/brick-filter/browser-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print(json.dumps(results,ensure_ascii=False))
