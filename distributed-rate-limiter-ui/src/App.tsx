import { useEffect, useRef, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import Sidebar from "./Components/Sidebar";
import StatsCards from "./Components/Card";
import ApiTable from "./Components/Table_Box";
import Analytics from "./Components/Analytics";
import LoginPage from "./Components/LoginPage";
import { apiUrl } from "./apiBase";
import type {
  ApiErrorResponse,
  DashboardAlertEvent,
  DashboardPagination,
  DashboardResponse,
  DashboardStats,
  PublicConfig,
  TableQuery,
  ToastItem,
} from "./types";
import "./App.css";

const DEFAULT_PAGINATION: DashboardPagination = {
  page: 1,
  size: 10,
  totalItems: 0,
  totalPages: 1,
  filtered: false,
  search: "",
};

const DEFAULT_DASHBOARD_STATS: DashboardStats = {
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

const DEFAULT_DASHBOARD_RESPONSE: DashboardResponse = {
  stats: DEFAULT_DASHBOARD_STATS,
  apiKeys: [],
  pagination: DEFAULT_PAGINATION,
  sources: {
    postgres: "",
    redis: "",
  },
  generatedAt: "",
};

function App() {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [uiConfig, setUiConfig] = useState<PublicConfig | null>(null);
  const [configError, setConfigError] = useState("");
  const [dashboardData, setDashboardData] = useState<DashboardResponse>(DEFAULT_DASHBOARD_RESPONSE);
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [tableQuery, setTableQuery] = useState<TableQuery>({ search: "", page: 1, size: 10 });
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const dashboardRequestInFlightRef = useRef(false);
  const lastDashboardLoadAtRef = useRef(0);
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage;

  const loadDashboardData = async (force = false, queryOverride: TableQuery | null = null): Promise<boolean> => {
    if (isFullWidthPage) {
      return false;
    }
    const refreshIntervalMs = Math.max(500, Number(uiConfig?.refreshIntervalMs) || 1000);
    const now = Date.now();
    if (!force && now - lastDashboardLoadAtRef.current < refreshIntervalMs) {
      return false;
    }
    if (dashboardRequestInFlightRef.current) {
      return false;
    }
    dashboardRequestInFlightRef.current = true;
    try {
      const query = queryOverride ?? tableQuery;
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
          window.location.assign("/login");
          return false;
        }
        return false;
      }
      const data: DashboardResponse = await response.json();
      setDashboardData({
        stats: data?.stats ?? DEFAULT_DASHBOARD_STATS,
        apiKeys: data?.apiKeys ?? [],
        pagination: data?.pagination ?? DEFAULT_PAGINATION,
        sources: data?.sources ?? DEFAULT_DASHBOARD_RESPONSE.sources,
        generatedAt: data?.generatedAt ?? "",
      });
      lastDashboardLoadAtRef.current = Date.now();
      return true;
    } catch {
      return false;
    } finally {
      dashboardRequestInFlightRef.current = false;
      setDashboardLoading(false);
    }
  };

  const loadUiConfig = async (): Promise<boolean> => {
    try {
      const response = await fetch(apiUrl("/api/config"), {
        credentials: "include",
      });
      if (!response.ok) {
        if (response.status === 401) {
          window.location.assign("/login");
          return false;
        }
        setConfigError(`Failed to load backend config (HTTP ${response.status}).`);
        return false;
      }
      const data: PublicConfig = await response.json();
      setUiConfig(data);
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
    const onAuthChanged = () => {
      void loadUiConfig();
      void loadDashboardData();
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
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    const source = new EventSource(apiUrl("/api/stream/dashboard"), { withCredentials: true });

    const onTick = () => {
      void loadDashboardData();
    };

    const onAlert = (event: MessageEvent<string>) => {
      try {
        const payload: DashboardAlertEvent = JSON.parse(event.data);
        if (!payload?.id) {
          return;
        }
        setToasts((current) => {
          if (current.some((toast) => toast.id === payload.id)) {
            return current;
          }
          const next: ToastItem[] = [
            ...current,
            {
              id: payload.id,
              title: payload.title ?? "Too many requests (429)",
              message: payload.message ?? "Rate limit exceeded.",
            },
          ];
          return next.slice(-4);
        });
        window.setTimeout(() => {
          setToasts((current) => current.filter((toast) => toast.id !== payload.id));
        }, 4000);
      } catch {
        // Ignore malformed event payloads.
      }
    };

    source.addEventListener("tick", onTick);
    source.addEventListener("alert", onAlert as EventListener);
    source.onerror = () => {
      source.close();
    };

    return () => {
      source.removeEventListener("tick", onTick);
      source.removeEventListener("alert", onAlert as EventListener);
      source.close();
    };
  }, [isFullWidthPage, uiConfig]);

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

            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/dashboard"
              element={
                !uiConfig ? (
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
                !uiConfig ? (
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

            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        )}
      </div>

      {toasts.length > 0 ? (
        <div className="toast-stack" aria-live="polite" aria-atomic="true">
          {toasts.map((toast) => (
            <div key={toast.id} className="app-toast app-toast--danger">
              <strong>{toast.title}</strong>
              <span>{toast.message}</span>
            </div>
          ))}
        </div>
      ) : null}
    </>
  );
}

export default App;
