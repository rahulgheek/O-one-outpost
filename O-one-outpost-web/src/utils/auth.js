/**
 * Utility functions for cryptographic token inspection and role management.
 */

function decodePayload(token) {
  if (!token) return null;
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;

    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (err) {
    console.warn('[AEGIS-AUTH] Failed to decode JWT payload:', err);
    return null;
  }
}

export function getRoleFromToken(token) {
  const parsed = decodePayload(token);
  return parsed?.role || null;
}

/**
 * Returns the expiry timestamp (in ms) from the JWT, or null if unavailable.
 */
export function getTokenExpiry(token) {
  const parsed = decodePayload(token);
  if (parsed?.exp) return parsed.exp * 1000; // JWT exp is in seconds
  return null;
}

/**
 * Returns true if the stored token is expired or missing.
 */
export function isTokenExpired() {
  const token = localStorage.getItem('algo_arena_token');
  if (!token) return true;
  const expiry = getTokenExpiry(token);
  if (!expiry) return true;
  return Date.now() >= expiry;
}

export function getCurrentRole() {
  const token = localStorage.getItem('algo_arena_token');
  const tokenRole = getRoleFromToken(token);
  const storedRole = localStorage.getItem('algo_arena_role');

  if (tokenRole && tokenRole !== storedRole) {
    localStorage.setItem('algo_arena_role', tokenRole);
    return tokenRole;
  }

  return storedRole || tokenRole || 'USER';
}

export function syncAuthRole() {
  const token = localStorage.getItem('algo_arena_token');
  if (!token) return null;
  
  const tokenRole = getRoleFromToken(token);
  if (tokenRole) {
    const currentStored = localStorage.getItem('algo_arena_role');
    if (currentStored !== tokenRole) {
      console.log(`[AEGIS-SYNC] Re-synchronizing role from JWT claim: was '${currentStored}', now '${tokenRole}'`);
      localStorage.setItem('algo_arena_role', tokenRole);
    }
  }
  return tokenRole;
}

/**
 * Starts a proactive watcher that auto-signs the user out when the JWT expires,
 * even if they are idle and make no API calls.
 * Returns a cleanup function to cancel the watcher.
 */
export function startTokenExpiryWatcher() {
  const token = localStorage.getItem('algo_arena_token');
  if (!token) return () => {};

  const expiry = getTokenExpiry(token);
  if (!expiry) return () => {};

  const msUntilExpiry = expiry - Date.now();

  if (msUntilExpiry <= 0) {
    // Already expired — sign out immediately
    localStorage.clear();
    sessionStorage.setItem('sessionExpired', 'true');
    window.location.href = '/';
    return () => {};
  }

  const timerId = setTimeout(() => {
    console.log('[AEGIS-AUTH] JWT expired. Auto sign-off triggered.');
    localStorage.clear();
    sessionStorage.setItem('sessionExpired', 'true');
    window.location.href = '/';
  }, msUntilExpiry);

  return () => clearTimeout(timerId);
}
