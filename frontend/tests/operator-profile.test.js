import test from 'node:test'
import assert from 'node:assert/strict'
import { createOperatorProfile, normalizeNickname, OPERATOR_KEY } from '../src/operatorProfile.js'
const memory = () => {const data=new Map();return {data,getItem:k=>data.get(k),setItem:(k,v)=>data.set(k,v)}}
test('normal names are trimmed, limited by characters, and restored locally',()=>{
 const storage=memory(), profile=createOperatorProfile(storage)
 assert.equal(profile.snapshot().nickname,'实验员')
 assert.equal(profile.save('  导入验收员  ').playTransition,false)
 assert.equal(createOperatorProfile(storage).snapshot().nickname,'导入验收员')
 assert.throws(()=>normalizeNickname('   '),/不能为空/)
 assert.throws(()=>normalizeNickname('一'.repeat(21)),/20/)
 assert.equal([...normalizeNickname('🐺'.repeat(20))].length,20)
})
test('exact trigger is guarded against reentry, reload never replays, default can replay',()=>{
 const storage=memory(), profile=createOperatorProfile(storage)
 assert.equal(profile.save('狼尊 999').playTransition,false)
 assert.equal(profile.save('狼尊999x').playTransition,false)
 assert.equal(profile.save(' 狼尊999 ').playTransition,true)
 assert.equal(profile.save('狼尊999').playTransition,false)
 assert.equal(createOperatorProfile(storage).snapshot().theme,'silverwolf')
 assert.equal(createOperatorProfile(storage).save('狼尊999').playTransition,false)
 profile.finishTransition();assert.equal(profile.save('狼尊999').playTransition,false)
 profile.restoreDefault();assert.equal(profile.snapshot().nickname,'狼尊999')
 assert.equal(profile.save('狼尊999').playTransition,true)
})
test('restore changes only profile theme and retains unrelated local data',()=>{
 const storage=memory();storage.setItem('draft:1002','UNSAVED');const profile=createOperatorProfile(storage)
 profile.save('狼尊999');profile.finishTransition();profile.restoreDefault()
 assert.equal(storage.getItem('draft:1002'),'UNSAVED')
 assert.deepEqual(JSON.parse(storage.getItem(OPERATOR_KEY)),{nickname:'狼尊999',theme:'default'})
})
test('corrupt or blocked storage cannot break the workbench',()=>{
 const storage=memory();storage.setItem(OPERATOR_KEY,'{bad');assert.equal(createOperatorProfile(storage).snapshot().theme,'default')
 const profile=createOperatorProfile({getItem(){throw Error('denied')},setItem(){throw Error('denied')}})
 assert.match(profile.save('普通名字').warning,/本地保存不可用/)
 assert.equal(profile.snapshot().nickname,'普通名字')
})
