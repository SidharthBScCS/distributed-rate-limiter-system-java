import Sidebar from "./Sidebar";
import StatsCards from "./Card";
import ApiTable from "./Table_Box";
import Analytics from "./Analytics";
import LoginPage from "./LoginPage";
import { useEffect, useState } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import "./App.css";

function hasAuthenticatedUser() {
  try {
    const cached = JSON.parse(localStorage.getItem("adminUser") || "null");
    if (!cached || typeof cached !== "object") return false;
    return Boolean(cached.userId || cached.fullName);
  } catch {
    localStorage.removeItem("adminUser");
    return false;
  }
}

function App() {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [refreshTick, setRefreshTick] = useState(0);
  const isAuthenticated = hasAuthenticatedUser();
  const isAnalyticsPage = location.pathname === "/analytics";
  const isFullWidthPage = location.pathname === "/login";
  const showSidebar = !isFullWidthPage && isAuthenticated;

  // Refresh data periodically
  useEffect(() => {
    const interval = setInterval(() => {
      if (isAuthenticated && !isFullWidthPage) {
        setRefreshTick(prev => prev + 1);
      }
    }, 30000); // Refresh every 30 seconds

    return () => clearInterval(interval);
  }, [isAuthenticated, isFullWidthPage]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMobileNavOpen(false);
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
            element={<Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />}
          />
          
          <Route
            path="/login"
            element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />}
          />
          
          <Route
            path="/dashboard"
            element={
              isAuthenticated ? (
                <div className="dashboard-page">
                  <div className="dashboard-content">
                    <StatsCards refreshTick={refreshTick} />
                    <ApiTable refreshTick={refreshTick} />
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
              isAuthenticated ? (
                <div className="page-container">
                  <Analytics />
                </div>
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          
          <Route
            path="*"
            element={<Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />}
          />
        </Routes>
      </div>
    </>
  );
}

export default App;
