"""Orca 실제 UI + 탭 내부 fetch/session fake. 서버·공유 저장소 쓰기 없음."""
import base64, json, os, subprocess, time
from pathlib import Path
PAGE = os.environ.get('ORCA_BRICK_TEST_PAGE')
if not PAGE:
    raise SystemExit('별도로 만든 로그아웃 검증 탭 ID를 ORCA_BRICK_TEST_PAGE에 지정하세요.')
def call(*args):
    p = subprocess.run(['orca', *args, '--page', PAGE, '--json'], capture_output=True, text=True, check=True)
    d = json.loads(p.stdout)
    if not d.get('ok'): raise RuntimeError(d)
    return d['result']
def ev(js):
    v = call('eval', '--expression', js)['result']
    try: return json.loads(v)
    except (ValueError, TypeError): return v

def click(name):
    call('snapshot')
    assert ev('(()=>{const b=[...document.querySelectorAll("button")].find(b=>b.textContent==='+json.dumps(name)+');if(!b||b.disabled)return false;b.click();return true})()'), name

def wait(text): call('wait', '--text', text)
fixture = Path('/tmp/gole-web-brick-test.png')
fixture.write_bytes(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j2ioAAAAASUVORK5CYII='))
invalid = Path('/tmp/gole-web-brick-test.txt'); invalid.write_text('not an image')
results = {}
results['loggedOut'] = ev('({login:!!document.querySelector("a[href=\\"/login?returnTo=%2Fbrick-filter\\"]"),fileInputs:document.querySelectorAll("input[type=file]").length})')
assert results['loggedOut']['login'] and results['loggedOut']['fileInputs']==0
setup = r'''(()=>{
const originalFetch=window.fetch;const originalGet=Storage.prototype.getItem;
const m=window.__webBrickMock={jobs:[],posts:[],postStatus:503,storeJob:false,quotaError:false,lookupDelay:0,active:0,maxActive:0,lookups:0,unauthorized:false,remaining:3,retryAvailable:true,enabled:true};
m.session=JSON.stringify({accountId:'web-mock-owner',sessionToken:'',role:'USER',onboardingRequired:false,refreshAfter:Date.now()+3600000});
Storage.prototype.getItem=function(key){if(this===localStorage&&key==='gole.session')return m.session;return originalGet.call(this,key)};
window.fetch=async(input,init)=>{const url=String(input);if(!url.includes('/api/v1/brick-filter')){if(url.includes('/api/'))return Response.json({},{status:200});return originalFetch(input,init)};
if(m.unauthorized)return Response.json({code:'INVALID_SESSION',message:'expired'},{status:401});
if(url.endsWith('/quota'))return m.quotaError?new Response('unavailable',{status:503}):Response.json({day:'2026-09-13',remaining:m.remaining,limit:3,enabled:m.enabled,retryAvailable:m.retryAvailable,resetsAt:'2026-09-13T15:00:00Z'});
if(init?.method==='POST'){const id=init.headers['Idempotency-Key'];m.posts.push({id,mode:init.body.get('mode'),name:init.body.get('image').name});const job={id,mode:init.body.get('mode'),status:'RESERVED',leaseUntil:'2026-09-13T15:00:00Z',resultUntil:'2026-09-14T15:00:00Z'};if(m.storeJob&&!m.jobs.some(j=>j.id===id))m.jobs.push(job);if(m.postStatus!==200)return new Response('unavailable',{status:m.postStatus});return Response.json(job)}
if(url.endsWith('/jobs'))return Response.json(m.jobs);
if(url.endsWith('/result'))return new Response(Uint8Array.from(atob('PNG'),c=>c.charCodeAt(0)),{headers:{'Content-Type':'image/png'}});
m.lookups++;m.active++;m.maxActive=Math.max(m.maxActive,m.active);const found=m.jobs.find(j=>j.id===decodeURIComponent(url.split('/').at(-1)));const response=found?{...found}:null;await new Promise(r=>setTimeout(r,m.lookupDelay));m.active--;return response?Response.json(response):Response.json({code:'BRICK_NOT_FOUND',message:'not found'},{status:404});};
window.__restoreWebBrick=()=>{window.fetch=originalFetch;Storage.prototype.getItem=originalGet;window.dispatchEvent(new Event('gole:session-change'));};window.dispatchEvent(new Event('gole:session-change'));return true;})()'''.replace('PNG',base64.b64encode(fixture.read_bytes()).decode())
assert ev(setup)
def upload(p):
    refs=call('snapshot')['refs'];key=next(k for k,v in refs.items() if v.get('name','').startswith('PNG/JPEG/HEIF'))
    call('upload','--element','@'+key,'--files',str(p));call('snapshot')
try:
    wait('오늘 남은 횟수 3 / 3')
    upload(fixture);ev('document.querySelector("input[type=checkbox]").click()')
    upload(invalid);wait('PNG/JPEG/HEIF 4MB 이하 사진을 선택해 주세요.')
    results['invalidSelectionClearsPrevious']=ev('({preview:!!document.querySelector("img[alt=\\"선택한 원본 사진\\"]"),consent:document.querySelector("input[type=checkbox]").checked,disabled:[...document.querySelectorAll("button")].find(b=>b.textContent==="브릭 이미지 만들기").disabled})')
    assert results['invalidSelectionClearsPrevious']=={'preview':False,'consent':False,'disabled':True}
    upload(fixture);ev('document.querySelector("input[type=checkbox]").click()')
    click('브릭 이미지 만들기');wait('응답을 확인하지 못했습니다.')
    results['server503PreservesPending']=ev('({posts:__webBrickMock.posts.length,disabled:document.querySelector("input[type=file]").disabled})')
    assert results['server503PreservesPending']=={'posts':1,'disabled':True}
    click('요청 상태 다시 확인');wait('같은 요청으로 다시 전송할 수 있습니다.')
    ev('__webBrickMock.postStatus=200;__webBrickMock.storeJob=true;__webBrickMock.lookupDelay=3500;__webBrickMock.quotaError=true')
    click('같은 요청 다시 전송');wait('작업 상태를 확인했습니다. 이용 횟수는 다시 확인해 주세요.')
    results['sameRequestRetry']=ev('({same:__webBrickMock.posts[0].id===__webBrickMock.posts[1].id,samePayload:__webBrickMock.posts[0].mode===__webBrickMock.posts[1].mode&&__webBrickMock.posts[0].name===__webBrickMock.posts[1].name,posts:__webBrickMock.posts.length})')
    assert results['sameRequestRetry']=={'same':True,'samePayload':True,'posts':2}
    # 3.5초 조회가 2.5초 간격보다 느려도 자동 polling은 직렬이어야 한다.
    time.sleep(10)
    results['serialPolling']=ev('({maxActive:__webBrickMock.maxActive,lookups:__webBrickMock.lookups})')
    assert results['serialPolling']['maxActive']==1 and results['serialPolling']['lookups']>=3
    # 진행 중 자동 조회가 끝나면 수동 조회만 오래 걸리게 하고 다음 자동 조회를 먼저 완료시킨다.
    for _ in range(30):
        if ev('__webBrickMock.active') == 0: break
        time.sleep(0.2)
    assert ev('__webBrickMock.active') == 0
    click('요청 상태 다시 확인')
    ev('__webBrickMock.lookupDelay=0;__webBrickMock.jobs[0].status="SUCCEEDED";__webBrickMock.quotaError=false')
    wait('이미지 저장')
    time.sleep(4)
    results['staleManualDoesNotRegress']=ev('({result:!!document.querySelector("img[alt=\\"생성된 브릭 이미지\\"]"),pending:!![...document.querySelectorAll("button")].find(b=>b.textContent==="요청 상태 다시 확인")})')
    assert results['staleManualDoesNotRegress']=={'result':True,'pending':False}
    results['authenticatedResult']=ev('({blob:document.querySelector("img[alt=\\"생성된 브릭 이미지\\"]").getAttribute("src").startsWith("blob:"),posts:__webBrickMock.posts.length})')
    assert results['authenticatedResult']['blob'] and results['authenticatedResult']['posts']==2
    for flag,value in [('remaining',0),('retryAvailable',False),('enabled',False)]:
        ev('__webBrickMock.remaining=3;__webBrickMock.retryAvailable=true;__webBrickMock.enabled=true;__webBrickMock.'+flag+'='+json.dumps(value))
        click('횟수 다시 확인')
        time.sleep(0.3)
        results['quotaGate_'+flag]=ev('[...document.querySelectorAll("button")].find(b=>b.textContent==="브릭 이미지 만들기").disabled')
        assert results['quotaGate_'+flag]
    ev('__webBrickMock.unauthorized=true')
    click('횟수 다시 확인');wait('로그인이 만료되었습니다.')
    results['expiredSession']=ev('({disabled:[...document.querySelectorAll("button")].find(b=>b.textContent==="브릭 이미지 만들기").disabled,status:[...document.querySelectorAll("[role=status]")].some(e=>e.textContent.includes("로그인이 만료"))})')
    assert results['expiredSession']=={'disabled':True,'status':True}
    ev('__webBrickMock.unauthorized=false;__webBrickMock.enabled=true;__webBrickMock.jobs=[];__webBrickMock.session=JSON.stringify({...JSON.parse(__webBrickMock.session),accountId:"web-other-owner"});window.dispatchEvent(new Event("gole:session-change"))')
    wait('오늘 남은 횟수 3 / 3')
    results['ownerChangeResetsEditor']=ev('({file:document.querySelector("input[type=file]").value,result:!!document.querySelector("img[alt=\\"생성된 브릭 이미지\\"]"),consent:document.querySelector("input[type=checkbox]").checked})')
    assert results['ownerChangeResetsEditor']=={'file':'','result':False,'consent':False}
finally:
    ev('window.__restoreWebBrick();true')
    call('snapshot')
Path('.kiro/specs/brick-filter/web-browser-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(results,ensure_ascii=False,indent=2))
