const API = import.meta.env.VITE_API_BASE ?? '';

async function csrf() {
  const response = await fetch(`${API}/api/auth/csrf`, { credentials: 'include' });
  if (!response.ok) throw new Error('Could not start a secure session.');
  return response.json();
}

export async function request(path, { method = 'GET', body, headers = {} } = {}) {
  const options = { method, credentials: 'include', headers: { ...headers } };
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())) {
    const token = await csrf();
    options.headers[token.headerName] = token.token;
  }
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(`${API}${path}`, options);
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) { const error = new Error(data?.detail || data?.message || `Request failed (${response.status}).`); error.status = response.status; throw error; }
  return data;
}
