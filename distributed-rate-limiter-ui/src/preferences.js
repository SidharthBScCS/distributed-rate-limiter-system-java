const APP_PREFERENCES_KEY = "app-preferences";
const APP_PREFERENCES_VERSION = 2;

const DEFAULT_PREFERENCES = {
  liveUpdates: true,
  defaultPageSize: 15,
  confirmLogout: true,
};

function normalizePageSize(value) {
  const allowedPageSizes = [5, 10, 15, 20, 50];
  const numericValue = Number(value);
  return allowedPageSizes.includes(numericValue) ? numericValue : DEFAULT_PREFERENCES.defaultPageSize;
}

function normalizePreferences(value) {
  return {
    liveUpdates: value?.liveUpdates !== false,
    defaultPageSize: normalizePageSize(value?.defaultPageSize),
    confirmLogout: value?.confirmLogout !== false,
  };
}

export function getDefaultPreferences() {
  return { ...DEFAULT_PREFERENCES };
}

export function readAppPreferences() {
  if (typeof window === "undefined") {
    return getDefaultPreferences();
  }

  try {
    const raw = window.localStorage.getItem(APP_PREFERENCES_KEY);
    if (!raw) {
      return getDefaultPreferences();
    }
    const parsed = JSON.parse(raw);
    const normalized = normalizePreferences(parsed);

    // Migrate legacy saved preferences that still carried the old page-size default.
    if ((parsed?.version ?? 0) < APP_PREFERENCES_VERSION) {
      if (parsed?.defaultPageSize === 10) {
        normalized.defaultPageSize = DEFAULT_PREFERENCES.defaultPageSize;
      }
      window.localStorage.setItem(
        APP_PREFERENCES_KEY,
        JSON.stringify({ ...normalized, version: APP_PREFERENCES_VERSION })
      );
    }

    return normalized;
  } catch {
    return getDefaultPreferences();
  }
}

export function writeAppPreferences(preferences) {
  const normalized = normalizePreferences(preferences);

  if (typeof window !== "undefined") {
    try {
      window.localStorage.setItem(
        APP_PREFERENCES_KEY,
        JSON.stringify({ ...normalized, version: APP_PREFERENCES_VERSION })
      );
    } catch {
      // Keep the UI usable even if storage is unavailable.
    }
  }

  return normalized;
}
