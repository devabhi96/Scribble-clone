const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const STORAGE_KEY = 'scribble-auth'

let session = loadFromStorage()

function loadFromStorage() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function saveSession(next) {
  session = next
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
}

function clearSession() {
  session = null
  sessionStorage.removeItem(STORAGE_KEY)
}

function isExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

export async function ensureSession() {
  if (session && !isExpired(session.token)) return session

  const res = await fetch(`${API_BASE}/api/auth/guest`, { method: 'POST' })
  if (!res.ok) throw new Error('Could not start a guest session')
  const data = await res.json()
  saveSession(data)
  return data
}

export async function login(username, password) {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error || 'Login failed')
  const data = await res.json()
  saveSession(data)
  return data
}

export async function register(username, password) {
  const res = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  if (!res.ok) throw new Error((await res.json().catch(() => ({}))).error || 'Registration failed')
  const data = await res.json()
  saveSession(data)
  return data
}

export function logout() {
  clearSession()
}

export function currentSession() {
  return session
}

export async function authFetch(path, options = {}) {
  const { token } = await ensureSession()
  return fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Authorization: `Bearer ${token}`
    }
  })
}

export { API_BASE }