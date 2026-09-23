// 실제 TSX 핸들러를 메모리 hook/JSX harness로 실행한다. DOM·서버 통합 테스트는 아니다.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const ts=require('../../../apps/web/node_modules/typescript');
const root=path.resolve(__dirname,'../../..');
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject};};
function harness(kind){
 const states=[],refs=[];let stateIndex=0,refIndex=0,upload=deferred(),submit=deferred();let uploads=0,submits=0,created=0;
 const jsx=(type,props)=>({type,props});
 const hooks={useState:initial=>{const i=stateIndex++;if(!(i in states))states[i]=initial;return [states[i],value=>{states[i]=typeof value==='function'?value(states[i]):value}]},useRef:initial=>{const i=refIndex++;return refs[i]??(refs[i]={current:initial})},useEffect:()=>{}};
 const deps={react:hooks,'react/jsx-runtime':{jsx,jsxs:jsx},'@shared/api':{ApiError:class extends Error{},uploadImages:()=>{uploads++;return upload.promise}},'@shared/ui':{Field:'Field',Button:'Button',Select:'Select',Input:'Input',Textarea:'Textarea'},'@shared/lib':{formatKrw:String},'@entities/order':{},'@entities/listing':{conditionLabel:String,completenessLabel:String,ITEM_CONDITIONS:[],LISTING_CATEGORIES:[],LISTING_INTEREST_TAGS:[],createListing:()=>{submits++;return submit.promise}},'@entities/community':{POST_TOPICS:[],publishPost:()=>{submits++;return submit.promise}}};
 const component=kind==='listing'?'CreateListingForm':'CreatePostForm';
 const file=`apps/web/src/features/create-${kind}/ui/create-${kind}-form.tsx`;
 const code=ts.transpileModule(fs.readFileSync(path.join(root,file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
 const mod={exports:{}};new Function('require','module','exports',code)(id=>{assert.ok(id in deps,id);return deps[id]},mod,mod.exports);
 function flatten(node){if(!node)return [];if(Array.isArray(node))return node.flatMap(flatten);if(typeof node!=='object')return [];let children=node.props?.children;if(typeof children==='function')children=children({inputId:'mock',describedBy:'mock-hint'});return [node,...flatten(children)]}
 function render(){stateIndex=refIndex=0;return flatten(mod.exports[component]({sellerId:'owner',authorId:'owner',paymentsOpen:false,onCreated:()=>created++}));}
 return {render,get uploads(){return uploads},get submits(){return submits},get created(){return created},get upload(){return upload},get submit(){return submit},nextUpload(){upload=deferred()},nextSubmit(){submit=deferred()}};
}
(async()=>{
 const passed=[];
 for(const kind of ['listing','post']){
  const h=harness(kind);let nodes=h.render();const file=()=>nodes.find(n=>n.type==='input'&&n.props.type==='file');const form=()=>nodes.find(n=>n.type==='form');const change=()=>({target:{files:[new File(['x'],'x.png')],value:'x'}});const event=()=>({preventDefault(){}});
  const first=file().props.onChange(change());const duplicate=file().props.onChange(change());await form().props.onSubmit(event());assert.equal(h.uploads,1);assert.equal(h.submits,0);await duplicate;
  h.upload.resolve([{key:'image-1',url:'blob:fake'}]);await first;nodes=h.render();passed.push(`${kind}: 업로드 중 Enter·중복 파일 이벤트 차단`);
  const sent=form().props.onSubmit(event());await form().props.onSubmit(event());assert.equal(h.submits,1);await file().props.onChange(change());assert.equal(h.uploads,1);
  h.submit.reject(new Error('fake failure'));await sent;nodes=h.render();h.nextSubmit();const retry=form().props.onSubmit(event());assert.equal(h.submits,2);h.submit.resolve({id:'created'});await retry;assert.equal(h.created,1);nodes=h.render();await form().props.onSubmit(event());assert.equal(h.submits,2);passed.push(`${kind}: 중복 등록 차단·실패 후 재시도·성공 후 잠금 유지`);
 }
 console.log(JSON.stringify({passed:passed.length,tests:passed,boundary:'실제 TSX 핸들러 + 메모리 hooks, DOM 미검증'},null,2));
})().catch(e=>{console.error(e);process.exitCode=1});
