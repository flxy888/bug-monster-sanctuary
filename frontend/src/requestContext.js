// Invalidates in-flight responses when the selected project or request changes.
export function createRequestContext() {
  let version = 0
  return {
    invalidate() { version += 1 },
    capture() { const captured = version; return () => captured === version },
  }
}
