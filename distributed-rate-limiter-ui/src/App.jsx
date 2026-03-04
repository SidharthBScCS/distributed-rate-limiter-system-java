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
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const [uiConfig, setUiConfig] = useState(null);
  const [configError, setConfigError] = useState("");
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage;

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
    };
    window.addEventListener("auth-changed", onAuthChanged);
    return () => window.removeEventListener("auth-changed", onAuthChanged);
  }, []);

  // Refresh data periodically
  useEffect(() => {
    if (isFullWidthPage) {
      return undefined;
    }

    const source = new EventSource(apiUrl("/api/stream/dashboard"), { withCredentials: true });

    const onTick = () => {
      setRefreshTick((prev) => prev + 1);
    };

    source.addEventListener("tick", onTick);
    source.onerror = () => {
      source.close();
      window.location.assign("/login");
    };

    return () => {
      source.removeEventListener("tick", onTick);
      source.close();
    };
  }, [isFullWidthPage]);

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
                    <StatsCards refreshTick={refreshTick} />
                    <ApiTable
                      refreshTick={refreshTick}
                      defaults={uiConfig.defaults}
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
    </>
  );
}

export default App;
