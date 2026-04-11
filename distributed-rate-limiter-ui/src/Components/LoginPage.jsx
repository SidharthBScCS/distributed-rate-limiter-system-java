import { useState } from "react";
import { Eye, EyeOff, AlertCircle } from "lucide-react";
import { withAuth } from "../auth.js";
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
        ...withAuth(),
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: username.trim(),
          password,
        }),
      });

      if (!response.ok) {
        let message = "Authentication failed";
        try {
          const data = await response.json();
          message = data?.message || message;
        } catch {
          // Keep the generic message if the response body is not JSON.
        }
        throw new Error(message);
      }

      await response.json();
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

          <div className="status-badge">System Secure</div>
        </div>
      </div>

      <div className="divider"></div>

      <div className="right-panel">
        <div className={`form-wrapper ${isSubmitting ? "loading" : ""}`}>
          <h1>Admin Access</h1>
          <p className="subtitle">Restricted system | Authorized only</p>

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
              onChange={(event) => setUsername(event.target.value)}
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
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />

              <button
                type="button"
                onClick={() => setShowPassword((previous) => !previous)}
                aria-label="Toggle password visibility"
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? <span className="spinner"></span> : "Enter System"}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
