/**
 * Utility functions for cryptographic token inspection and role management.
 */

export function getRoleFromToken(token) {
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
    const parsed = JSON.parse(jsonPayload);
    return parsed.role || null;
  } catch (err) {
    console.warn('[AEGIS-AUTH] Failed to decode JWT payload:', err);
    return null;
  }
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
