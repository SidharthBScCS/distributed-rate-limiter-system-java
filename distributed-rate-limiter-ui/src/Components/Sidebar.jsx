import { useState } from "react";
import { NavLink } from "react-router-dom";
import { LayoutDashboard, BarChart3, Shield, LogOut, Settings as SettingsIcon, X } from "lucide-react";
import { buildAuthHeaders, setFrontendAuthenticated } from "../auth.js";
import { readAppPreferences } from "../preferences.js";
import { apiUrl } from "../apiBase.js";
import "../Styles/Sidebar.css";

function Sidebar({ isMobileOpen }) {
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const menuItems = [
    {
      icon: LayoutDashboard,
      label: "Dashboard",
      path: "/dashboard",
    },
    {
      icon: BarChart3,
      label: "Analytics",
      path: "/analytics",
    },
    {
      icon: SettingsIcon,
      label: "Settings",
      path: "/settings",
    },
  ];

  const performLogout = async () => {
    try {
      await fetch(apiUrl("/api/auth/logout"), {
        method: "POST",
        headers: buildAuthHeaders(),
      });
    } catch {
      // Clear the local session state even if the backend is unavailable.
    }
    setShowLogoutConfirm(false);
    setFrontendAuthenticated(false);
    window.dispatchEvent(new Event("auth-changed"));
    window.location.assign("/login");
  };

  const handleLogout = () => {
    const preferences = readAppPreferences();
    if (preferences.confirmLogout) {
      setShowLogoutConfirm(true);
      return;
    }

    void performLogout();
  };

  return (
    <>
      <aside className={`sidebar ${isMobileOpen ? "open" : ""}`}>
        <div className="sidebar-brand">
          <div className="brand-logo">
            <Shield size={28} className="logo-icon" />
          </div>
          <div className="brand-info">
            <h2>RateLimiter</h2>
            <span>Control Panel</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          {menuItems.map((item) => {
            const Icon = item.icon;

            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`}
              >
                <div className="nav-content">
                  <div className="nav-icon">
                    <Icon size={20} />
                  </div>
                  <span className="nav-label">{item.label}</span>
                </div>
              </NavLink>
            );
          })}
        </nav>

        <div className="sidebar-footer">
          <button className="logout-btn" onClick={handleLogout}>
            <LogOut size={20} />
            <span>Logout</span>
          </button>
        </div>
      </aside>

      {showLogoutConfirm ? (
        <div className="logout-modal-overlay" onClick={() => setShowLogoutConfirm(false)}>
          <div className="logout-modal-card" onClick={(event) => event.stopPropagation()}>
            <button
              type="button"
              className="logout-modal-close"
              onClick={() => setShowLogoutConfirm(false)}
              aria-label="Close logout confirmation"
            >
              <X size={18} />
            </button>
            <div className="logout-modal-icon">
              <LogOut size={24} />
            </div>
            <h3>Confirm Logout</h3>
            <p>Are you sure you want to logout from the control panel?</p>
            <div className="logout-modal-actions">
              <button type="button" className="logout-cancel-btn" onClick={() => setShowLogoutConfirm(false)}>
                Stay Logged In
              </button>
              <button type="button" className="logout-confirm-btn" onClick={() => void performLogout()}>
                Logout Now
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

export default Sidebar;
