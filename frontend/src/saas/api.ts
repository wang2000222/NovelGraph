let csrf = '';
export function setCsrf(value: string) {
  csrf = value;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code: string,
  ) {
    super(message);
  }
}
/** 身份只使用HttpOnly Cookie；本地缓存不保存访问令牌。 */
export async function request<T = Record<string, unknown>>(
  path: string,
  method = 'GET',
  body?: unknown,
  timeoutMs = 30000,
): Promise<T> {
  const controller = new AbortController(),
    timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch('/api/v1' + path, {
      method,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        ...(method !== 'GET' ? { 'X-CSRF-Token': csrf } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const value = await response.json();
    if (!response.ok)
      throw new ApiError(value.message || '请求失败', response.status, value.code || 'ERROR');
    return value as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof Error && error.name === 'AbortError')
      throw new Error('请求超时，草稿已保留，请重试');
    throw new Error('无法连接服务，请检查后端是否启动');
  } finally {
    clearTimeout(timer);
  }
}
export async function download(path: string, body: unknown, file: string) {
  const r = await fetch('/api/v1' + path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
    body: JSON.stringify(body),
  });
  if (!r.ok) {
    const e = await r.json();
    throw new Error(e.message);
  }
  const url = URL.createObjectURL(await r.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = file;
  a.click();
  URL.revokeObjectURL(url);
}
