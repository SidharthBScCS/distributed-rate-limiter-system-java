import { useState } from "react";
import { Eye, EyeOff, AlertCircle, Shield, Lock } from "lucide-react";
import { apiUrl } from "../apiBase";
import "../Styles/LoginPage.css";

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
      <div className="login-noise" />
      <div className="login-scanlines" />

      <div className="login-code login-code--left" aria-hidden="true">
        <span>0040BC41</span>
        <span>apiKey.lookup()</span>
        <span>traffic.audit()</span>
        <span>REQUEST DETECTED</span>
        <span>window=60s</span>
        <span>blocked=FALSE</span>
        <span>limit.status=OK</span>
        <span>redis.sync()</span>
        <span>cluster.node()</span>
        <span>operator.auth()</span>
      </div>

      <div className="login-code login-code--right" aria-hidden="true">
        <span>TRACE ENABLED</span>
        <span>sliding_window()</span>
        <span>policy=ACTIVE</span>
        <span>alert=NONE</span>
        <span>shield=ONLINE</span>
        <span>analytics.ready()</span>
        <span>session.secure()</span>
        <span>rate_limit.enforce()</span>
        <span>throughput=HIGH</span>
        <span>access.console()</span>
      </div>

      <div className="login-terminal">
        <div className="terminal-header">
          <div className="terminal-brand">
            <Shield size={15} />
            <span>Distributed Rate Limiter</span>
          </div>
          <span className="terminal-status">secure console</span>
        </div>

        <div className="terminal-panel">
          <div className="terminal-title-block">
            <span className="terminal-kicker">administrator authentication required</span>
            <h1>ENTER PASSWORD</h1>
            <p>Authenticate to access the rate limiting control system.</p>
          </div>

          {error ? (
            <div className="error-message">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          ) : null}

          <form className="login-form" onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="username">Operator ID</label>
              <div className="input-wrapper">
                <input
                  id="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="ENTER USERNAME"
                  autoComplete="username"
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="password">Password</label>
              <div className="input-wrapper">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="ENTER PASSWORD"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((value) => !value)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            <div className="login-links">
              <span className="login-link-muted">
                <Lock size={12} />
                session protected
              </span>
            </div>

            <button type="submit" className="submit-btn" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner" />
                  AUTHENTICATING
                </>
              ) : (
                "INITIALIZE ACCESS"
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
