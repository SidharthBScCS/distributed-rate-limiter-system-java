import Sidebar from "./Sidebar";
import StatsCards from "./Card";
import ApiTable from "./Table_Box";
import Analytics from "./Analytics";
import LoginPage from "./LoginPage";
import { useEffect, useRef, useState } from "react";
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
  const [toasts, setToasts] = useState([]);
  const seenDecisionKeysRef = useRef(new Set());
  const initializedDecisionFeedRef = useRef(false);
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage;
  const triggerRefresh = () => setRefreshTick((prev) => prev + 1);

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
      triggerRefresh();
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
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    const source = new EventSource(apiUrl("/api/stream/dashboard"), { withCredentials: true });

    const onTick = () => {
      triggerRefresh();
    };

    source.addEventListener("tick", onTick);
    source.onerror = () => {
      source.close();
    };

    return () => {
      source.removeEventListener("tick", onTick);
      source.close();
    };
  }, [isFullWidthPage, uiConfig]);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    const intervalMs = Math.max(5000, Number(uiConfig.refreshIntervalMs) || 30000);
    const intervalId = window.setInterval(() => {
      triggerRefresh();
    }, intervalMs);

    return () => window.clearInterval(intervalId);
  }, [isFullWidthPage, uiConfig]);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    const handleWindowFocus = () => {
      triggerRefresh();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        triggerRefresh();
      }
    };

    window.addEventListener("focus", handleWindowFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.removeEventListener("focus", handleWindowFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [isFullWidthPage, uiConfig]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setMobileNavOpen(false);
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [location.pathname]);

  useEffect(() => {
    if (isFullWidthPage || !uiConfig) {
      return undefined;
    }

    let cancelled = false;

    const pushToast = (entry) => {
      const toastId = `${entry.apiKey}-${entry.evaluatedAt}`;
      const shortKey = String(entry.apiKey ?? "").slice(0, 12);
      setToasts((current) => {
        const next = [
          ...current,
          {
            id: toastId,
            title: "Too many requests (429)",
            message: `API key ${shortKey} exceeded the sliding window limit.`,
          },
        ];
        return next.slice(-4);
      });

      window.setTimeout(() => {
        setToasts((current) => current.filter((toast) => toast.id !== toastId));
      }, 4000);
    };

    const loadRecentDecisions = async () => {
      try {
        const response = await fetch(apiUrl("/api/analytics/recent-decisions"), {
          credentials: "include",
        });
        if (!response.ok) {
          return;
        }
        const entries = await response.json();
        if (cancelled || !Array.isArray(entries)) {
          return;
        }

        if (!initializedDecisionFeedRef.current) {
          entries.forEach((entry) => {
            if (entry?.apiKey && entry?.evaluatedAt) {
              seenDecisionKeysRef.current.add(`${entry.apiKey}-${entry.evaluatedAt}`);
            }
          });
          initializedDecisionFeedRef.current = true;
          return;
        }

        entries
          .slice()
          .reverse()
          .forEach((entry) => {
            const decisionKey = `${entry?.apiKey}-${entry?.evaluatedAt}`;
            if (!entry?.allowed && entry?.apiKey && entry?.evaluatedAt && !seenDecisionKeysRef.current.has(decisionKey)) {
              seenDecisionKeysRef.current.add(decisionKey);
              pushToast(entry);
            }
          });
      } catch {
        // Toast feed is best-effort; ignore transient polling failures.
      }
    };

    loadRecentDecisions();
    const intervalId = window.setInterval(loadRecentDecisions, 5000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [isFullWidthPage, uiConfig]);

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
