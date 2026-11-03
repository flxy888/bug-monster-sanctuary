// Check the actual image URLs observed in the running production DOM, not mocked paths.
// Usage: node frontend/tests/verify-catalog-resources.js deliverables/week4-fix/browser-evidence.json
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
const evidence=JSON.parse(readFileSync(process.argv[2],'utf8'))
const expected={EMPTY_CATCH:'吞异常兽',LONG_METHOD:'长舌章鱼',DEEP_NESTING:'套娃怪',TOO_MANY_PARAMETERS:'多手触手怪',EMPTY_IF:'空壳潜伏者'}
assert.equal(new URL(evidence.url).origin,'http://127.0.0.1:5173')
assert.deepEqual(evidence.catalog.map(i=>i.ruleCode).sort(),Object.keys(expected).sort())
const results=[]
for(const item of evidence.catalog){
 assert.equal(item.name,expected[item.ruleCode]);assert.equal(item.complete,true);assert(item.naturalWidth>0 && item.naturalHeight>0)
 const url=new URL(item.src);assert.equal(url.origin,new URL(evidence.url).origin);assert(!url.pathname.includes('/assets/assets/'))
 const response=await fetch(url);assert.equal(response.status,200);assert.match(response.headers.get('content-type'),/^image\/svg\+xml/)
 const bytes=Buffer.from(await response.arrayBuffer()),source=readFileSync(new URL('../src/assets/lab/'+item.ruleCode+'.svg',import.meta.url))
 assert.deepEqual(bytes,source,'Production image content does not match '+item.ruleCode)
 const chamber=[...evidence.chambersFirst,...evidence.chambersRest].find(c=>c.ruleCode===item.ruleCode)
 assert(chamber && chamber.complete && chamber.naturalWidth>0);assert.equal(chamber.src,item.src)
 results.push({ruleCode:item.ruleCode,name:item.name,url:item.src,status:response.status,contentType:response.headers.get('content-type'),bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),catalogNaturalWidth:item.naturalWidth,chamberNaturalWidth:chamber.naturalWidth})
}
console.log(JSON.stringify({status:'PASS',origin:new URL(evidence.url).origin,images:results},null,2))
