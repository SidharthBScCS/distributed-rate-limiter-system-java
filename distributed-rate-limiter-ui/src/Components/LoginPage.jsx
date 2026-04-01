import { useState } from "react";
import { Eye, EyeOff, AlertCircle } from "lucide-react";
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
    if (isSubmitting) return;

    setError("");
    setIsSubmitting(true);

    try {
      const response = await fetch(apiUrl("/api/auth/login"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ username, password }),
      });

      

      if (!response.ok) {
        throw new Error("Authentication failed");
      }

      window.dispatchEvent(new Event("auth-changed"));
      window.location.assign("/dashboard");
    } catch (err) {
      setError(err.message || "Authentication failed");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="login-container">

      <div className="left-panel">
        <div className="left-content">

          <div className="logo-row">
            <div className="logo-shape"></div>
              <h2>RateLimiter</h2>
          </div>

          <div className="system-info">
            <h3>API Traffic Control</h3>
            <p>Secure rate limiting and traffic governance for distributed systems.</p>
          </div>

          <div className="status-badge">
            ● System Secure
          </div>

        </div>
      </div>
      <div className="divider"></div>
      <div className="right-panel">
        <div className={`form-wrapper ${isSubmitting ? "loading" : ""}`}>

          <h1>Admin Access</h1>
          <p className="subtitle">Restricted system • Authorized only</p>

          {error && (
            <div className="error-alert">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">

            <input
              id="username"
              type="text"
              placeholder="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              autoComplete="username"
              required
            />

            <div className="password-field">
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />

              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label="Toggle password visibility"
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <span className="spinner"></span>
              ) : (
                "Enter System"
              )}
            </button>

          </form>

        </div>
      </div>
    </div>
  );
}

export default LoginPage;
