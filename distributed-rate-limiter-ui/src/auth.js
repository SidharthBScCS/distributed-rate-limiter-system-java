const FRONTEND_AUTH_KEY = "frontend-authenticated";
const ACCESS_TOKEN_KEY = "admin-access-token";

function readStorageValue(key) {
  if (typeof window === "undefined") {
    return "";
  }

  return window.localStorage.getItem(key) ?? "";
}

export function getAccessToken() {
  return readStorageValue(ACCESS_TOKEN_KEY).trim();
}

export function isFrontendAuthenticated() {
  if (typeof window === "undefined") {
    return false;
  }

  return readStorageValue(FRONTEND_AUTH_KEY) === "true" && getAccessToken() !== "";
}

export function setFrontendAuthenticated(isAuthenticated, token = "") {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(FRONTEND_AUTH_KEY, isAuthenticated ? "true" : "false");
  if (isAuthenticated && token) {
    window.localStorage.setItem(ACCESS_TOKEN_KEY, token);
    return;
  }

  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export function buildAuthHeaders(headers = {}) {
  const token = getAccessToken();
  if (!token) {
    return headers;
  }

  return {
    ...headers,
    Authorization: `Bearer ${token}`,
  };
}
