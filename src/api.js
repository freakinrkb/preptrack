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
    if (body instanceof FormData) {
      options.body = body;
    } else {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
  }
  const response = await fetch(`${API}${path}`, options);
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) { const error = new Error(data?.detail || data?.message || `Request failed (${response.status}).`); error.status = response.status; throw error; }
  return data;
}

export async function uploadRequest(path, formData, onProgress = () => {}) {
  const token = await csrf();
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API}${path}`);
    xhr.withCredentials = true;
    xhr.setRequestHeader(token.headerName, token.token);
    xhr.upload.addEventListener('progress', event => {
      if (event.lengthComputable) onProgress(Math.round(event.loaded / event.total * 100));
    });
    xhr.addEventListener('load', () => {
      let data = null;
      try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch { }
      if (xhr.status < 200 || xhr.status >= 300) {
        const error = new Error(data?.detail || data?.message || `Request failed (${xhr.status}).`);
        error.status = xhr.status;
        reject(error);
        return;
      }
      resolve(data);
    });
    xhr.addEventListener('error', () => reject(new Error('The upload could not reach PrepTrack. Check the connection and retry.')));
    xhr.addEventListener('abort', () => reject(new Error('The upload was cancelled.')));
    xhr.send(formData);
  });
}
