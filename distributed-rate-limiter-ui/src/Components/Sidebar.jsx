import { 
  LayoutDashboard, 
  BarChart3, 
  Shield, 
  LogOut,
  X
} from "lucide-react";
import { NavLink } from "react-router-dom";
import { useState } from "react";
import { apiUrl } from "../apiBase.js";
import "../Styles/Sidebar.css";

function Sidebar({ isMobileOpen }) {
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const menuItems = [
    { 
      icon: LayoutDashboard, 
      label: "Dashboard", 
      path: "/dashboard"
    },
    { 
      icon: BarChart3, 
      label: "Analytics", 
      path: "/analytics"
    },
  ];

  const handleLogout = async () => {
    try {
      await fetch(apiUrl("/api/auth/logout"), {
        method: "POST",
        credentials: "include",
      });
    } catch {
      // Ignore API errors and still force a UI-side auth refresh.
    } finally {
      window.dispatchEvent(new Event("auth-changed"));
      window.location.assign("/login");
    }
  };

  return (
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
        <button className="logout-btn" onClick={() => setShowLogoutConfirm(true)}>
          <LogOut size={20} />
          <span>Logout</span>
        </button>
      </div>

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
            <h3>Confirm Logout</h3>
            <p>Are you sure you want to logout?</p>
            <div className="logout-modal-actions">
              <button
                type="button"
                className="logout-cancel-btn"
                onClick={() => setShowLogoutConfirm(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="logout-confirm-btn"
                onClick={handleLogout}
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </aside>
  );
}

export default Sidebar;
