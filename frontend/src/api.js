const base = (import.meta.env?.VITE_API_BASE_URL || '').replace(/\/$/, '')
async function request(path, options = {}) {
  const response = await fetch(base + '/api' + path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  })
  const text = await response.text()
  let payload
  try { payload = text ? JSON.parse(text) : null } catch { throw new Error('服务响应格式错误，请检查后端或代理配置') }
  if (!response.ok) throw new Error(payload?.message || '请求失败（HTTP ' + response.status + '）')
  return payload
}
export const api = {
  aiRepair: (id, sourceCode, options = {}) => request('/projects/' + id + '/ai-repairs', { method: 'POST', body: JSON.stringify({ sourceCode }), signal: options.signal }),
  health: () => request('/health'),
  ast: id => request('/scans/' + id + '/ast'),
  deleteProject: id => request('/projects/' + id, { method: 'DELETE' }),
  catalog: () => request('/monsters/catalog'),
  stats: () => request('/users/me/stats'),
  monsters: () => request('/users/me/monsters'),
  projects: () => request('/projects'),
  createProject: (name, sourceCode) => request('/projects', { method: 'POST', body: JSON.stringify({ name, sourceCode }) }),
  saveProject: (id, name, sourceCode) => request('/projects/' + id, { method: 'PUT', body: JSON.stringify({ name, sourceCode }) }),
  scan: (id, sourceCode) => request('/projects/' + id + '/scans', { method: 'POST', body: JSON.stringify({ sourceCode }) }),
  history: (id) => request('/projects/' + id + '/scans'),
  detail: (id) => request('/scans/' + id),
}
