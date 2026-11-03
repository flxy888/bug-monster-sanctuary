export const OPERATOR_KEY = 'sanctuary.operator.v1'
export const EASTER_EGG_NAME = '狼尊999'
export const DEFAULT_NICKNAME = '实验员'

export function normalizeNickname(value) {
  const name = String(value ?? '').trim()
  if (!name) throw new Error('显示名不能为空')
  if ([...name].length > 20) throw new Error('显示名最多20个字符')
  return name
}

export function createOperatorProfile(storage) {
  if (storage === undefined) {
    try { storage = window.localStorage } catch { storage = null }
  }
  let profile = { nickname: DEFAULT_NICKNAME, theme: 'default' }
  let busy = false, warning = ''
  try {
    const saved = JSON.parse(storage?.getItem(OPERATOR_KEY) || 'null')
    if (saved) profile = { nickname: normalizeNickname(saved.nickname), theme: saved.theme === 'silverwolf' ? 'silverwolf' : 'default' }
  } catch { /* Invalid/blocked local settings must never stop the workbench. */ }
  const persist = () => {
    try {
      if (!storage) throw new Error('storage unavailable')
      storage.setItem(OPERATOR_KEY, JSON.stringify(profile)); warning = ''
    } catch { warning = '本地保存不可用，本次会话仍可使用。' }
  }
  return {
    snapshot: () => ({ ...profile }),
    save(value) {
      if (busy) return { profile: { ...profile }, playTransition: false, warning }
      const nickname = normalizeNickname(value)
      const playTransition = nickname === EASTER_EGG_NAME && profile.theme === 'default'
      profile = { nickname, theme: playTransition ? 'silverwolf' : profile.theme }
      busy = playTransition
      // Persist before the animation: a refresh keeps the theme, without replaying.
      persist()
      return { profile: { ...profile }, playTransition, warning }
    },
    finishTransition() { busy = false },
    restoreDefault() { busy = false; profile = { ...profile, theme: 'default' }; persist(); return { profile: { ...profile }, warning } },
  }
}
