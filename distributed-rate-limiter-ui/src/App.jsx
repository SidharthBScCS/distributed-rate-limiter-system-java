import Sidebar from "./Sidebar";
import StatsCards from "./Card";
import ApiTable from "./Table_Box";
import Analytics from "./Analytics";
import LoginPage from "./LoginPage";
import { useEffect, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { apiUrl } from "./apiBase";
import "./App.css";

function App() {
  const defaultUiConfig = {
    grafanaDashboardUrl: "",
    refreshIntervalMs: 30000,
    allowedAlgorithms: [],
    defaults: {
      rateLimit: "",
      windowSeconds: "",
      algorithm: "",
    },
  };

  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const [authState, setAuthState] = useState("checking");
  const [uiConfig, setUiConfig] = useState(defaultUiConfig);
  const isAuthenticated = authState === "authenticated";
  const isAuthChecking = authState === "checking";
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage && isAuthenticated;

  const loadUiConfig = async () => {
    try {
      const response = await fetch(apiUrl("/api/config"), {
        credentials: "include",
      });
      if (!response.ok) {
        return false;
      }
      const data = await response.json();
      setUiConfig({
        grafanaDashboardUrl: typeof data?.grafanaDashboardUrl === "string" ? data.grafanaDashboardUrl : "",
        refreshIntervalMs: Number(data?.refreshIntervalMs) > 0 ? Number(data.refreshIntervalMs) : 30000,
        allowedAlgorithms: Array.isArray(data?.allowedAlgorithms) && data.allowedAlgorithms.length > 0
          ? data.allowedAlgorithms
          : [],
        defaults: {
          rateLimit: Number(data?.defaults?.rateLimit) > 0 ? Number(data.defaults.rateLimit) : "",
          windowSeconds: Number(data?.defaults?.windowSeconds) > 0 ? Number(data.defaults.windowSeconds) : "",
          algorithm: typeof data?.defaults?.algorithm === "string" ? data.defaults.algorithm : "",
        },
      });
      return true;
    } catch {
      return false;
    }
  };

  const refreshAuthState = async () => {
    setAuthState("checking");
    try {
      const response = await fetch(apiUrl("/api/auth/me"), {
        credentials: "include",
      });
      setAuthState(response.ok ? "authenticated" : "unauthenticated");
    } catch {
      setAuthState("unauthenticated");
    }
  };

  useEffect(() => {
    const configTimeoutId = window.setTimeout(() => {
      loadUiConfig();
    }, 0);
    const authTimeoutId = window.setTimeout(() => {
      refreshAuthState();
    }, 0);
    return () => {
      window.clearTimeout(configTimeoutId);
      window.clearTimeout(authTimeoutId);
    };
  }, []);

  useEffect(() => {
    const onAuthChanged = () => {
      loadUiConfig();
      refreshAuthState();
    };
    window.addEventListener("auth-changed", onAuthChanged);
    return () => window.removeEventListener("auth-changed", onAuthChanged);
  }, []);

  useEffect(() => {
    if (isAnalyticsPage && !uiConfig.grafanaDashboardUrl) {
      const timeoutId = window.setTimeout(() => {
        loadUiConfig();
      }, 0);
      return () => window.clearTimeout(timeoutId);
    }
    return undefined;
  }, [isAnalyticsPage, uiConfig.grafanaDashboardUrl]);

  // Refresh data periodically
  useEffect(() => {
    const interval = setInterval(() => {
      if (isAuthenticated && !isFullWidthPage) {
        setRefreshTick(prev => prev + 1);
      }
    }, uiConfig.refreshIntervalMs);

    return () => clearInterval(interval);
  }, [isAuthenticated, isFullWidthPage, uiConfig.refreshIntervalMs]);

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
        <Routes>
          <Route
            path="/"
            element={
              isAuthChecking
                ? <div className="auth-loading">Loading...</div>
                : <Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />
            }
          />
          
          <Route
            path="/login"
            element={
              isAuthChecking
                ? <div className="auth-loading">Loading...</div>
                : (isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />)
            }
          />
          
          <Route
            path="/dashboard"
            element={
              isAuthChecking ? (
                <div className="auth-loading">Loading...</div>
              ) : isAuthenticated ? (
                <div className="dashboard-page">
                  <div className="dashboard-content">
                    <StatsCards refreshTick={refreshTick} />
                    <ApiTable
                      refreshTick={refreshTick}
                      defaults={uiConfig.defaults}
                    />
                  </div>
                </div>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          
          <Route
            path="/analytics"
            element={
              isAuthChecking ? (
                <div className="auth-loading">Loading...</div>
              ) : isAuthenticated ? (
                <div className="page-container analytics-page-container">
                  <Analytics grafanaDashboardUrl={uiConfig.grafanaDashboardUrl} />
                </div>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          
          <Route
            path="*"
            element={
              isAuthChecking
                ? <div className="auth-loading">Loading...</div>
                : <Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />
            }
          />
        </Routes>
      </div>
    </>
  );
}

export default App;
