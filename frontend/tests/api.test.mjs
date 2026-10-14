import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import ts from 'typescript';
// 使用真实客户端模块验证会话与错误处理，网络由测试替身隔离。
const source=await fs.readFile(new URL('../src/saas/api.ts',import.meta.url),'utf8');
const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText;
const api=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'));
test('写操作携带 Cookie 与 CSRF，读取不带 CSRF',async()=>{
 const original=globalThis.fetch;const calls=[];
 try{globalThis.fetch=async(url,options)=>{calls.push({url,options});return {ok:true,json:async()=>({saved:true})}};api.setCsrf('test-csrf');await api.request('/projects/1','PATCH',{title:'测试'});await api.request('/me');assert.equal(calls[0].options.credentials,'same-origin');assert.equal(calls[0].options.headers['X-CSRF-Token'],'test-csrf');assert.equal(calls[0].url,'/api/v1/projects/1');assert.equal(JSON.parse(calls[0].options.body).title,'测试');assert.equal(calls[1].options.headers['X-CSRF-Token'],undefined);assert.equal(calls[0].options.headers.Authorization,undefined)}finally{globalThis.fetch=original}
});
test('版本冲突保留服务器错误代码',async()=>{const original=globalThis.fetch;try{globalThis.fetch=async()=>({ok:false,status:409,json:async()=>({code:'ENTITY_CONFLICT',message:'资料已有新版本'})});await assert.rejects(api.request('/projects/1','PATCH',{}),e=>e instanceof api.ApiError&&e.code==='ENTITY_CONFLICT'&&e.status===409&&e.message==='资料已有新版本')}finally{globalThis.fetch=original}});
test('断网返回可读错误',async()=>{const original=globalThis.fetch;try{globalThis.fetch=async()=>{throw new TypeError('network')};await assert.rejects(api.request('/me'),/无法连接服务/)}finally{globalThis.fetch=original}});
