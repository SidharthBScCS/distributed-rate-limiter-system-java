import { useState } from "react";
import { Eye, EyeOff, AlertCircle, Lock } from "lucide-react";
import { apiUrl } from "../apiBase.js";
import "../Styles/LoginPage.css";

function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
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
        throw new Error(data.message || "Authentication failed");
      }

      window.dispatchEvent(new Event("auth-changed"));
      window.location.assign("/dashboard");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Authentication failed");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="card-content">

          {/* System Identity */}
          <div className="system-header">
            <div className="badge-icon">
              <Lock size={16} />
            </div>
            <span className="system-name">API Rate Limiter</span>
          </div>

          {/* Heading */}
          <div className="heading-group">
            <h1>Admin Access</h1>
            <p className="subhead">Authorized personnel only</p>
          </div>

          {/* Error */}
          {error && (
            <div className="error-alert">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="input-field">
              <label htmlFor="username">Username</label>
              <div className="input-wrapper">
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  required
                />
              </div>
            </div>

            <div className="input-field">
              <label htmlFor="password">Password</label>
              <div className="input-wrapper">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                  autoFocus
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((prev) => !prev)}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            <button type="submit" className="submit-btn" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner"></span>
                  Authenticating...
                </>
              ) : (
                "Enter System"
              )}
            </button>
          </form>

          {/* Footer */}
          <div className="login-footer">
            <span>Secure session enforced</span>
          </div>

        </div>
      </div>
    </div>
  );
}

export default LoginPage;