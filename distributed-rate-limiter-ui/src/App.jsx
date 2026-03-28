import Sidebar from "../src/Components/Sidebar";
import StatsCards from "../src/Components/Card";
import ApiTable from "../src/Components/Table_Box";
import Analytics from "../src/Components/Analytics";
import LoginPage from "../src/Components/LoginPage";
import { useEffect, useRef, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { apiUrl } from "./apiBase";
import "./App.css";

function App() {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [uiConfig, setUiConfig] = useState(null);
  const [configError, setConfigError] = useState("");
  const [dashboardData, setDashboardData] = useState({
    stats: {},
    apiKeys: [],
    pagination: { page: 1, size: 10, totalItems: 0, totalPages: 1, filtered: false, search: "" },
  });
  const [dashboardLoading, setDashboardLoading] = useState(true);
  const [tableQuery, setTableQuery] = useState({ search: "", page: 1, size: 10 });
  const [toasts, setToasts] = useState([]);
  const dashboardRequestInFlightRef = useRef(false);
  const lastDashboardLoadAtRef = useRef(0);
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage;

  const loadDashboardData = async (force = false, queryOverride = null) => {
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
      if (query.search?.trim()) {
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
      const data = await response.json();
      setDashboardData({
        stats: data?.stats ?? {},
        apiKeys: data?.apiKeys ?? [],
        pagination: data?.pagination ?? { page: 1, size: 10, totalItems: 0, totalPages: 1, filtered: false, search: "" },
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

  const loadUiConfig = async () => {
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
      const data = await response.json();
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
      loadUiConfig();
    }, 0);
    return () => {
      window.clearTimeout(configTimeoutId);
    };
  }, [isFullWidthPage]);

  useEffect(() => {
    const onAuthChanged = () => {
      loadUiConfig();
      loadDashboardData();
    };
    window.addEventListener("auth-changed", onAuthChanged);
    return () => window.removeEventListener("auth-changed", onAuthChanged);
  }, []);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }
    loadDashboardData(true);
    return undefined;
  }, [isFullWidthPage, uiConfig, tableQuery]);

  // Refresh data periodically
  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    const source = new EventSource(apiUrl("/api/stream/dashboard"), { withCredentials: true });

    const onTick = () => {
      loadDashboardData();
    };
    const onAlert = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (!payload?.id) {
          return;
        }
        setToasts((current) => {
          if (current.some((toast) => toast.id === payload.id)) {
            return current;
          }
          const next = [
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
    source.addEventListener("alert", onAlert);
    source.onerror = () => {
      source.close();
    };

    return () => {
      source.removeEventListener("tick", onTick);
      source.removeEventListener("alert", onAlert);
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
            onClick={() => setMobileNavOpen((v) => !v)}
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
          <Route
            path="/"
            element={<Navigate to="/dashboard" replace />}
          />
          
          <Route
            path="/login"
            element={<LoginPage />}
          />
          
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
                    <StatsCards
                      stats={dashboardData.stats}
                      loading={dashboardLoading}
                    />
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

          <Route
            path="*"
            element={<Navigate to="/dashboard" replace />}
          />
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
