export function buildAuthHeaders(headers = {}) {
  return { ...headers };
}

export function withAuth(options = {}) {
  return {
    ...options,
    credentials: "include",
    headers: options.headers ?? {},
  };
}
