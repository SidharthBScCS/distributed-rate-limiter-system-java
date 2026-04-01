export function apiUrl(path) {
  if (!path) return "";
  if (/^https?:\/\//i.test(path)) return path;

  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? "").trim().replace(/\/+$/, "");

  if (!configuredBaseUrl) {
    return normalizedPath;
  }

  return `${configuredBaseUrl}${normalizedPath}`;
}
