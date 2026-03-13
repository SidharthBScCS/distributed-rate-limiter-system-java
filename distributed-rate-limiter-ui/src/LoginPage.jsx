import "./LoginPage.css";
import { AlertCircle, Eye, EyeOff, Shield } from "lucide-react";
import { useState } from "react";
import { apiUrl } from "./apiBase";

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

      const payload = await response.json();

      if (!response.ok) {
        const message = payload.message;
        throw new Error(message);
      }

      if (payload && typeof payload === "object") {
        window.dispatchEvent(new Event("auth-changed"));
      }
      window.location.assign("/dashboard");
    } catch (err) {
      const message = err instanceof Error ? err.message : "";
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="gh-login-shell">
      <div className="gh-grid-lines" />
      <div className="gh-halo gh-halo--one" />
      <div className="gh-halo gh-halo--two" />
      <div className="gh-halo gh-halo--three" />

      <div className="gh-main">
        <section className="gh-stage">
          <div className="gh-auth-card">
            <div className="gh-card-orbit gh-card-orbit--one" />
            <div className="gh-card-orbit gh-card-orbit--two" />
            <div className="gh-card-shine" />
            <div className="gh-brand-row">
              <div className="gh-brand-badge">
                <Shield size={18} strokeWidth={2.1} />
              </div>
              <div className="gh-brand-copy">
                <strong>RateLimiter Console</strong>
                <span>Protected admin gateway</span>
              </div>
            </div>
            <div className="gh-auth-topbar">
              <span className="gh-topbar-dot" />
              <span className="gh-topbar-dot" />
              <span className="gh-topbar-dot" />
            </div>

            <div className="gh-auth-head">
              <span className="gh-kicker">Admin Access</span>
              <h3>Welcome back</h3>
              <p>Sign in to open the rate limiter dashboard.</p>
            </div>

            {error ? (
              <div className="gh-error">
                <AlertCircle size={16} />
                <span>{error}</span>
              </div>
            ) : null}

            <div className="gh-status-strip" aria-label="System status">
              <span className="gh-status-item">
                <span className="gh-status-dot" />
                System secure
              </span>
              <span className="gh-status-item">
                <span className="gh-status-dot" />
                Redis live
              </span>
              <span className="gh-status-item">
                <span className="gh-status-dot" />
                Session protected
              </span>
            </div>

            <form className="gh-form" onSubmit={handleSubmit}>
              <label htmlFor="username">Username</label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                placeholder="Enter admin username"
              />

              <label htmlFor="password">Password</label>
              <div className="gh-password-field">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                />
                <button
                  type="button"
                  className="gh-password-toggle"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((value) => !value)}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>

              <button className="gh-submit" type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <span className="gh-submit-spinner" />
                    Signing in...
                  </>
                ) : (
                  "Enter Dashboard"
                )}
              </button>
            </form>

            <div className="gh-card-meta">
              <span className="gh-meta-pill">Secure Session</span>
              <span className="gh-meta-pill">Live Metrics</span>
              <span className="gh-meta-pill">Admin Console</span>
            </div>

            <p className="gh-footer-note">Authorized admin access only</p>
          </div>
        </section>
      </div>
    </div>
  );
}

export default LoginPage;
