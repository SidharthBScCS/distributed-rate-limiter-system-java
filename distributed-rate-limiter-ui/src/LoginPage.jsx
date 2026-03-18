import { useState } from "react";
import { Eye, EyeOff, Shield, AlertCircle, Lock, UserCog, CheckCircle } from "lucide-react";
import { apiUrl } from "./apiBase";
import "./LoginPage.css";

function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const response = await fetch(apiUrl("/api/auth/login"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ username, password }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Access denied");
      }

      window.dispatchEvent(new Event("auth-changed"));
      window.location.assign("/dashboard");
    } catch (err) {
      setError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-container">
      <div className="grid-pattern" />
      <div className="orb orb-1" />
      <div className="orb orb-2" />
      <div className="orb orb-3" />
      
      <div className="login-card">
        {/* Left Section - Brand & Info */}
        <div className="login-left">
          <div>
            <div className="admin-badge">
              <Lock size={14} />
              <span>Admin Portal</span>
            </div>

            <div className="brand-section">
              <div className="logo-wrapper">
                <Shield />
              </div>
              <h1>RateLimiter</h1>
              <p>Enterprise-grade API rate limiting and access control</p>
            </div>
          </div>

          <div className="status-badges">
            <div className="status-badge">
              <span className="status-dot" />
              <span>System Status: Online</span>
            </div>
            <div className="status-badge">
              <span className="status-dot secure" />
              <span>Security: Active</span>
            </div>
            <div className="status-badge">
              <span className="status-dot" />
              <span>Redis: Connected</span>
            </div>
          </div>
        </div>

        {/* Right Section - Login Form */}
        <div className="login-right">
          <div className="admin-notice">
            <UserCog size={20} />
            <span>Restricted to <strong>authorized administrators</strong> only</span>
          </div>

          {error && (
            <div className="error-message">
              <AlertCircle />
              <span>{error}</span>
            </div>
          )}

          <form className="login-form" onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="username">Admin Username</label>
              <div className="input-wrapper">
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter your username"
                  autoComplete="username"
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="password">Admin Password</label>
              <div className="input-wrapper">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <button 
              type="submit" 
              className="submit-btn" 
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <>
                  <span className="spinner" />
                  Authenticating...
                </>
              ) : (
                "Access Dashboard"
              )}
            </button>
          </form>

          <p className="footer-text">
            © 2026 RateLimiter · Secure Admin Access
          </p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;