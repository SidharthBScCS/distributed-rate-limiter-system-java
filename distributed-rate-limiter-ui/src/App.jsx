import { useEffect, useRef, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import Sidebar from "./Components/Sidebar.jsx";
import StatsCards from "./Components/Card.jsx";
import ApiTable from "./Components/Table_Box.jsx";
import Analytics from "./Components/Analytics.jsx";
import LoginPage from "./Components/LoginPage.jsx";
import Settings from "./Components/Settings.jsx";
import { apiUrl } from "./apiBase.js";
import { isFrontendAuthenticated, setFrontendAuthenticated } from "./auth.js";
import { readAppPreferences } from "./preferences.js";
import "./App.css";

const UI_CONFIG_CACHE_KEY = "ui-config-cache";
const DASHBOARD_CACHE_KEY = "dashboard-cache";

const DEFAULT_PAGINATION = {
  page: 1,
  size: 10,
  totalItems: 0,
  totalPages: 1,
  filtered: false,
  search: "",
};

const DEFAULT_DASHBOARD_STATS = {
  totalRequests: 0,
  totalRequestsLabel: "0",
  allowedRequests: 0,
  allowedRequestsLabel: "0",
  blockedRequests: 0,
  blockedRequestsLabel: "0",
  totalPercent: 0,
  allowedPercent: 0,
  blockedPercent: 0,
  cards: [],
};

const DEFAULT_DASHBOARD_RESPONSE = {
  stats: DEFAULT_DASHBOARD_STATS,
  apiKeys: [],
  pagination: DEFAULT_PAGINATION,
  sources: {
    postgres: "",
    redis: "",
  },
  generatedAt: "",
};

function normalizeTableQuery(dashboardData) {
  const pagination = dashboardData?.pagination ?? DEFAULT_PAGINATION;
  return {
    search: pagination.search ?? "",
    page: pagination.page ?? 1,
    size: pagination.size ?? 10,
  };
}

function resolveInitialTableQuery(dashboardData, preferences) {
  const normalized = normalizeTableQuery(dashboardData);
  return {
    ...normalized,
    size: preferences.defaultPageSize ?? normalized.size,
  };
}

function readCachedJson(key, fallback) {
  if (typeof window === "undefined") {
    return fallback;
  }

  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeCachedJson(key, value) {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Ignore storage write failures and keep the in-memory state working.
  }
}

function App() {
  const location = useLocation();
  const initialDashboardData = readCachedJson(DASHBOARD_CACHE_KEY, DEFAULT_DASHBOARD_RESPONSE);
  const initialPreferences = readAppPreferences();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(() => isFrontendAuthenticated());
  const [authChecking, setAuthChecking] = useState(false);
  const [uiConfig, setUiConfig] = useState(() => readCachedJson(UI_CONFIG_CACHE_KEY, null));
  const [configError, setConfigError] = useState("");
  const [dashboardData, setDashboardData] = useState(initialDashboardData);
  const [dashboardLoading, setDashboardLoading] = useState(() => !isFrontendAuthenticated());
  const [dashboardRefreshing, setDashboardRefreshing] = useState(false);
  const [preferences, setPreferences] = useState(initialPreferences);
  const [tableQuery, setTableQuery] = useState(() => resolveInitialTableQuery(initialDashboardData, initialPreferences));
  const dashboardRequestInFlightRef = useRef(false);
  const lastDashboardLoadAtRef = useRef(0);
  const tableQueryRef = useRef(tableQuery);
  const pendingDashboardLoadRef = useRef(null);
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage;

  useEffect(() => {
    tableQueryRef.current = tableQuery;
  }, [tableQuery]);

  useEffect(() => {
    const onPreferencesChanged = () => {
      const nextPreferences = readAppPreferences();
      setPreferences(nextPreferences);
      setTableQuery((current) => {
        if (current.size === nextPreferences.defaultPageSize) {
          return current;
        }
        return {
          ...current,
          page: 1,
          size: nextPreferences.defaultPageSize,
        };
      });
    };

    window.addEventListener("app-preferences-changed", onPreferencesChanged);
    return () => window.removeEventListener("app-preferences-changed", onPreferencesChanged);
  }, []);

  const syncAuthState = async () => {
    try {
      const response = await fetch(apiUrl("/api/auth/me"), {
        credentials: "include",
      });
      const authenticated = response.ok;
      setFrontendAuthenticated(authenticated);
      setIsAuthenticated(authenticated);
      return authenticated;
    } catch {
      setFrontendAuthenticated(false);
      setIsAuthenticated(false);
      return false;
    } finally {
      setAuthChecking(false);
    }
  };

  const loadDashboardData = async (force = false, queryOverride = null) => {
    if (isFullWidthPage) {
      return false;
    }
    const refreshIntervalMs = Math.max(500, Number(uiConfig?.refreshIntervalMs) || 1000);
    const now = Date.now();
    if (!force && now - lastDashboardLoadAtRef.current < refreshIntervalMs) {
      return false;
    }
    const query = queryOverride ?? tableQueryRef.current;
    if (dashboardRequestInFlightRef.current) {
      pendingDashboardLoadRef.current = { force, query };
      return false;
    }
    dashboardRequestInFlightRef.current = true;
    setDashboardRefreshing(true);
    try {
      const params = new URLSearchParams({
        page: String(query.page ?? 1),
        size: String(query.size ?? 10),
      });
      if (query.search.trim()) {
        params.set("search", query.search.trim());
      }

      const response = await fetch(apiUrl(`/api/view/dashboard?${params.toString()}`), {
        credentials: "include",
      });
      if (!response.ok) {
        if (response.status === 401) {
          setFrontendAuthenticated(false);
          setIsAuthenticated(false);
          setAuthChecking(false);
          window.location.assign("/login");
          return false;
        }
        return false;
      }
      const data = await response.json();
      const nextPagination = data?.pagination ?? DEFAULT_PAGINATION;
      const pendingLoad = pendingDashboardLoadRef.current;
      const hasNewerPendingQuery = pendingLoad
        ? (pendingLoad.query?.search ?? "") !== (query.search ?? "") ||
          (pendingLoad.query?.page ?? 1) !== (query.page ?? 1) ||
          (pendingLoad.query?.size ?? 10) !== (query.size ?? 10)
        : false;

      if (hasNewerPendingQuery) {
        return false;
      }

      setDashboardData({
        stats: data?.stats ?? DEFAULT_DASHBOARD_STATS,
        apiKeys: data?.apiKeys ?? [],
        pagination: nextPagination,
        sources: data?.sources ?? DEFAULT_DASHBOARD_RESPONSE.sources,
        generatedAt: data?.generatedAt ?? "",
      });
      writeCachedJson(DASHBOARD_CACHE_KEY, {
        stats: data?.stats ?? DEFAULT_DASHBOARD_STATS,
        apiKeys: data?.apiKeys ?? [],
        pagination: nextPagination,
        sources: data?.sources ?? DEFAULT_DASHBOARD_RESPONSE.sources,
        generatedAt: data?.generatedAt ?? "",
      });
      setTableQuery((current) => {
        const nextQuery = {
          search: nextPagination.search ?? "",
          page: nextPagination.page ?? 1,
          size: nextPagination.size ?? current.size ?? 10,
        };
        if (
          current.search === nextQuery.search &&
          current.page === nextQuery.page &&
          current.size === nextQuery.size
        ) {
          return current;
        }
        return nextQuery;
      });
      lastDashboardLoadAtRef.current = Date.now();
      return true;
    } catch {
      return false;
    } finally {
      dashboardRequestInFlightRef.current = false;
      setDashboardLoading(false);
      setDashboardRefreshing(false);
      const pendingLoad = pendingDashboardLoadRef.current;
      if (pendingLoad) {
        pendingDashboardLoadRef.current = null;
        window.setTimeout(() => {
          void loadDashboardData(pendingLoad.force, pendingLoad.query);
        }, 0);
      }
    }
  };

  const loadUiConfig = async () => {
    try {
      const response = await fetch(apiUrl("/api/config"), {
        credentials: "include",
      });
      if (!response.ok) {
        if (response.status === 401) {
          setFrontendAuthenticated(false);
          setIsAuthenticated(false);
          setAuthChecking(false);
          window.location.assign("/login");
          return false;
        }
        setConfigError(`Failed to load backend config (HTTP ${response.status}).`);
        return false;
      }
      const data = await response.json();
      setUiConfig(data);
      writeCachedJson(UI_CONFIG_CACHE_KEY, data);
      setConfigError("");
      return true;
    } catch {
      setConfigError("Cannot reach backend. Make sure Spring Boot is running.");
      return false;
    }
  };

  useEffect(() => {
    if (isFullWidthPage) {
      return undefined;
    }
    const configTimeoutId = window.setTimeout(() => {
      void loadUiConfig();
    }, 0);
    return () => {
      window.clearTimeout(configTimeoutId);
    };
  }, [isFullWidthPage]);

  useEffect(() => {
    void syncAuthState();
  }, []);

  useEffect(() => {
    const onAuthChanged = () => {
      setIsAuthenticated(isFrontendAuthenticated());
      setAuthChecking(false);
      void loadUiConfig();
      void loadDashboardData(true);
    };
    window.addEventListener("auth-changed", onAuthChanged);
    return () => window.removeEventListener("auth-changed", onAuthChanged);
  }, []);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }
    void loadDashboardData(true);
    return undefined;
  }, [isFullWidthPage, uiConfig, tableQuery]);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig || !preferences.liveUpdates) {
      return undefined;
    }

    const source = new EventSource(apiUrl("/api/stream/dashboard"), { withCredentials: true });

    const onTick = () => {
      void loadDashboardData();
    };

    source.addEventListener("tick", onTick);
    source.onerror = () => {
      source.close();
    };

    return () => {
      source.removeEventListener("tick", onTick);
      source.close();
    };
  }, [isFullWidthPage, preferences.liveUpdates, uiConfig]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setMobileNavOpen(false);
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [location.pathname]);

  return (
    <>
      {showSidebar ? (
        <>
          <button
            type="button"
            className="mobile-nav-toggle"
            aria-label={mobileNavOpen ? "Close menu" : "Open menu"}
            onClick={() => setMobileNavOpen((value) => !value)}
          >
            {mobileNavOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
          <div
            className={`mobile-nav-overlay ${mobileNavOpen ? "open" : ""}`}
            onClick={() => setMobileNavOpen(false)}
          />
          <Sidebar isMobileOpen={mobileNavOpen} />
        </>
      ) : null}

      <div className={`right-content ${isFullWidthPage ? "full-width" : ""} ${isAnalyticsPage ? "analytics-layout" : ""}`}>
        {!isFullWidthPage && !uiConfig ? (
          <div className="page-container">
            <div className="analytics-empty-state">
              <p>{configError || "Loading configuration..."}</p>
            </div>
          </div>
        ) : (
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />

            <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />} />

            <Route
              path="/dashboard"
              element={
                !isAuthenticated ? (
                  <Navigate to="/login" replace />
                ) : !uiConfig ? (
                  <div className="page-container">
                    <div className="analytics-empty-state">
                      <p>{configError || "Loading configuration..."}</p>
                    </div>
                  </div>
                ) : (
                  <div className="dashboard-page">
                    <div className="dashboard-content">
                      <StatsCards stats={dashboardData.stats} loading={dashboardLoading} />
                      <ApiTable
                        dashboardData={dashboardData}
                        loading={dashboardLoading}
                        refreshing={dashboardRefreshing}
                        defaults={uiConfig.defaults}
                        onDashboardRefresh={loadDashboardData}
                        tableQuery={tableQuery}
                        onTableQueryChange={setTableQuery}
                      />
                    </div>
                  </div>
                )
              }
            />

            <Route
              path="/analytics"
              element={
                !isAuthenticated ? (
                  <Navigate to="/login" replace />
                ) : !uiConfig ? (
                  <div className="page-container">
                    <div className="analytics-empty-state">
                      <p>{configError || "Loading configuration..."}</p>
                    </div>
                  </div>
                ) : (
                  <div className="page-container analytics-page-container">
                    <Analytics grafanaDashboardUrl={uiConfig.grafanaDashboardUrl} />
                  </div>
                )
              }
            />

            <Route
              path="/settings"
              element={
                !isAuthenticated ? (
                  <Navigate to="/login" replace />
                ) : (
                  <div className="page-container">
                    <Settings uiConfig={uiConfig} />
                  </div>
                )
              }
            />

            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        )}
      </div>
    </>
  );
}

export default App;
