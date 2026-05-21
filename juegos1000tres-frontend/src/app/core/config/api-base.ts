export function obtenerApiBaseUrl(): string {
  const override = obtenerOverrideDesdeQuery() || obtenerOverrideDesdeStorage() || obtenerOverrideGlobal();
  return normalizarBaseUrl(override || 'http://localhost:8083');
}

function obtenerOverrideDesdeQuery(): string {
  if (typeof window === 'undefined') {
    return '';
  }

  try {
    return new URL(window.location.href).searchParams.get('apiBase') || '';
  } catch {
    return '';
  }
}

function obtenerOverrideDesdeStorage(): string {
  if (typeof window === 'undefined' || !window.localStorage) {
    return '';
  }

  return window.localStorage.getItem('juegos1000tres.apiBase') || '';
}

function obtenerOverrideGlobal(): string {
  const globalWindow = globalThis as typeof globalThis & {
    __JUEGOS1000TRES_API_BASE__?: string;
  };

  return globalWindow.__JUEGOS1000TRES_API_BASE__ || '';
}

function normalizarBaseUrl(valor: string): string {
  const limpio = valor.trim().replace(/\/$/, '');
  return limpio || 'http://localhost:8083';
}