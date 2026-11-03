import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from '../src/api.js'
import { createRequestContext } from '../src/requestContext.js'

test('scan sends the full source snapshot and no owner identity', async () => {
  let captured
  const previous = globalThis.fetch
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return { ok: true, text: async () => '{"scanId":7,"status":"SUCCESS","score":85}' }
  }
  try {
    const code = 'class A { String s = "<script>"; }'
    const result = await api.scan(12, code)
    assert.equal(captured.url, '/api/projects/12/scans')
    assert.equal(captured.options.method, 'POST')
    assert.deepEqual(JSON.parse(captured.options.body), { sourceCode: code })
    assert.equal(result.score, 85)
  } finally { globalThis.fetch = previous }
})
test('structured backend failure is visible to the caller', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = async () => ({ ok: false, status: 503, text: async () => '{"message":"数据库暂不可用"}' })
  try { await assert.rejects(api.projects(), /数据库暂不可用/) }
  finally { globalThis.fetch = previous }
})
test('invalid proxy response is reported as configuration error', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = async () => ({ ok: true, text: async () => '<html>fallback</html>' })
  try { await assert.rejects(api.projects(), /响应格式错误/) }
  finally { globalThis.fetch = previous }
})
test('project switch invalidates an old response while a new response remains valid', () => {
  const context = createRequestContext()
  const old = context.capture()
  context.invalidate()
  const current = context.capture()
  assert.equal(old(), false)
  assert.equal(current(), true)
})
test('history/detail requests can be invalidated independently of project requests', () => {
  const project = createRequestContext(), detail = createRequestContext()
  const projectRequest = project.capture(), oldDetail = detail.capture()
  detail.invalidate()
  assert.equal(projectRequest(), true)
  assert.equal(oldDetail(), false)
})
