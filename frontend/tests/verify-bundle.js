import { readFileSync, readdirSync } from 'node:fs'
import assert from 'node:assert/strict'
const version = JSON.parse(readFileSync(new URL('../package.json',import.meta.url),'utf8')).dependencies.dompurify
const assets = new URL('../dist/assets/',import.meta.url)
const files = readdirSync(assets)
const scripts = files.filter(f=>f.endsWith('.js')).map(f=>readFileSync(new URL(f,assets),'utf8')).join('\n')
assert(scripts.includes(version),'Built sanitizer version must match the directly pinned DOMPurify version')
assert(!scripts.includes('3.4.15'),'Monaco bundled the old vendored sanitizer; check the Vite alias')
assert(files.some(f=>/^editor\.worker-.*\.js$/.test(f)),'Monaco worker bundle is missing')
console.log(JSON.stringify({bundleVerification:'PASS',dompurify:version,oldVendoredVersionAbsent:true,editorWorkerPresent:true}))

// Validate packaged sprite bytes, production references, and absence of source-relative lookup.
const rules=['EMPTY_CATCH','LONG_METHOD','DEEP_NESTING','TOO_MANY_PARAMETERS','EMPTY_IF']
assert(!scripts.includes('./assets/lab/'),'A source-relative sprite URL survived production bundling')
const spriteAssets=rules.map(rule=>{
 const matches=files.filter(f=>f.startsWith(rule+'-') && f.endsWith('.svg'))
 assert.equal(matches.length,1,'Expected one bundled SVG for '+rule)
 const name=matches[0],built=readFileSync(new URL(name,assets))
 assert.deepEqual(built,readFileSync(new URL('../src/assets/lab/'+rule+'.svg',import.meta.url)),'Bundled SVG differs from its rule source: '+rule)
 assert(scripts.includes('/assets/'+name),'Production JavaScript does not reference '+name)
 return {rule,file:name,bytes:built.length}
})
console.log(JSON.stringify({productionSprites:'PASS',spriteAssets}))
