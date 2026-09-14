// 실행: node .kiro/specs/brick-filter/web-core-tests.cjs (설치·외부 호출 없음)
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('../../../apps/web/node_modules/typescript');
const root = path.resolve(__dirname, '../../..');
const source = fs.readFileSync(path.join(root, 'packages/core/src/brick-filter/index.ts'), 'utf8');
const compiled = ts.transpileModule(source, {compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
class ApiError extends Error { constructor(status, body) {super(body.message);this.status=status;this.code=body.code;} }
let clearCount=0, headers={}, handler, calls=[];
const originalFetch=global.fetch;
global.fetch=async (url, init)=>{calls.push({url,init});return handler(url,init);};
const moduleMock={exports:{}};
new Function('require','module','exports',compiled)(()=>({ApiError,requireConfig:()=>({apiBaseUrl:'https://mock.invalid'}),getSessionStore:()=>({readAuthorizationHeader:()=>headers,clear:()=>clearCount++})}),moduleMock,moduleMock.exports);
const api=moduleMock.exports;
const passed=[];
async function test(name,fn){calls=[];headers={};await fn();passed.push(name);}
(async()=>{try {
 await test('쿠키·멱등키·multipart 유지 및 POST deadline',async()=>{
  handler=async()=>Response.json({id:'day_id',status:'RESERVED'});
  const file=new File(['bytes'],'photo.png',{type:'image/png'});
  await api.createBrickJob('day_id','MINIFIGURE',file);
  const {init}=calls[0];assert.equal(init.credentials,'include');assert.equal(init.cache,'no-store');assert.equal(init.headers['Idempotency-Key'],'day_id');assert.equal(init.headers['Content-Type'],undefined);assert.equal(init.body.get('mode'),'MINIFIGURE');assert.equal(init.body.get('image').name,'photo.png');assert.ok(init.signal instanceof AbortSignal);
 });
 await test('모든 GET 인증·no-store·deadline 및 ID 인코딩',async()=>{
  headers={Authorization:'Bearer fake-test-only'};handler=async()=>Response.json({});
  await api.fetchBrickQuota();await api.fetchBrickJobs();await api.fetchBrickJob('a/b');
  assert.ok(calls[2].url.endsWith('/jobs/a%2Fb'));
  for(const {init} of calls){assert.equal(init.headers.Authorization,headers.Authorization);assert.equal(init.credentials,'include');assert.equal(init.cache,'no-store');assert.ok(init.signal instanceof AbortSignal);}
 });
 await test('HTML·null·빈 오류 응답은 상태 보존한 actionable ApiError',async()=>{
  for(const body of ['<html>unavailable</html>','null','{}','{"message":""}']){handler=async()=>new Response(body,{status:503});await assert.rejects(api.fetchBrickQuota(),e=>e instanceof ApiError&&e.status===503&&e.message.includes('요청 상태'));}
 });
 await test('늦은 401은 현재 세션을 지우지 않음',async()=>{
  handler=async()=>Response.json({code:'INVALID_SESSION',message:'expired'},{status:401});
  await assert.rejects(api.fetchBrickJobs(),e=>e.status===401);assert.equal(clearCount,0);
 });
 await test('조회 15초·생성 180초 deadline을 실제 abort로 전달',async()=>{
  const originalTimeout=AbortSignal.timeout;const deadlines=[];
  AbortSignal.timeout=ms=>{deadlines.push(ms);return originalTimeout(1)};
  handler=async(_url,init)=>new Promise((_resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('abort 미전달')),500);init.signal.addEventListener('abort',()=>{clearTimeout(timer);reject(init.signal.reason)},{once:true})});
  try{await assert.rejects(api.fetchBrickQuota(),e=>e.name==='TimeoutError');await assert.rejects(api.createBrickJob('id','MINIFIGURE',new File(['x'],'x.png')),e=>e.name==='TimeoutError');assert.deepEqual(deadlines,[15000,180000]);}finally{AbortSignal.timeout=originalTimeout;}
 });
 await test('정상 서버 오류 code/message 보존',async()=>{
  handler=async()=>Response.json({code:'BRICK_QUOTA',message:'횟수 소진'},{status:429});
  await assert.rejects(api.createBrickJob('id','BRICK_OBJECT',new File(['x'],'x.png')),e=>e.status===429&&e.code==='BRICK_QUOTA'&&e.message==='횟수 소진');
 });
 await test('결과는 인증 이미지 바이트만 허용',async()=>{
  handler=async()=>new Response('html',{headers:{'Content-Type':'text/html'}});await assert.rejects(api.fetchBrickResult('id'),e=>e.code==='BRICK_INVALID_RESULT');
  handler=async()=>new Response(new Uint8Array([137,80,78,71,13,10,26,10]),{headers:{'Content-Type':'image/png'}});assert.equal((await api.fetchBrickResult('id')).size,8);
 });
 await test('SVG·위조 PNG·빈 결과·선언 및 스트림 크기 초과를 거절',async()=>{
  for(const response of [new Response('<svg/>',{headers:{'Content-Type':'image/svg+xml'}}),new Response('not png',{headers:{'Content-Type':'image/png'}}),new Response(null,{headers:{'Content-Type':'image/png'}}),new Response('x',{headers:{'Content-Type':'image/png','Content-Length':String(8*1024*1024+1)}}),new Response(new Uint8Array(8*1024*1024+1),{headers:{'Content-Type':'image/png'}})]){
   handler=async()=>response;await assert.rejects(api.fetchBrickResult('id'),e=>e.code==='BRICK_INVALID_RESULT');
  }
 });
 console.log(JSON.stringify({passed:passed.length,tests:passed,network:'모든 fetch를 메모리 fake로 대체'},null,2));
}finally{global.fetch=originalFetch;}})().catch(e=>{console.error(e);process.exitCode=1;});
