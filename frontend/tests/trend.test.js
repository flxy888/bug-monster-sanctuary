import test from 'node:test'
import assert from 'node:assert/strict'
import {trendPoints,trendSegments} from '../src/trend.js'
test('saved trend failure gaps and incompatible/unknown configurations are never connected',()=>{
 const history=[{scanId:5,status:'SUCCESS',score:100,scoringConfigId:'b'},{scanId:4,status:'SUCCESS',score:95,scoringConfigId:'a'},{scanId:3,status:'PARSE_ERROR',score:null,scoringConfigId:'a'},{scanId:2,status:'SUCCESS',score:85,scoringConfigId:'a'},{scanId:1,status:'SUCCESS',score:80,scoringConfigId:'a'}]
 const before=JSON.stringify(history),p=trendPoints(history),s=trendSegments(p)
 assert.equal(p.find(x=>x.scanId===3).y,null);assert.deepEqual(s.map(x=>[x.a.scanId,x.b.scanId]),[[1,2]]);assert.equal(JSON.stringify(history),before)
 assert.deepEqual(trendSegments(trendPoints([{scanId:1,status:'SUCCESS',score:85},{scanId:2,status:'SUCCESS',score:100} ])),[])
 assert.equal(trendPoints(Array.from({length:60},(_,i)=>({scanId:i,status:'SUCCESS',score:49}))).length,50)
})
