import "./LoginPage.css";
import { AlertCircle } from "lucide-react";
import { useState } from "react";
import { apiUrl } from "./apiBase";

function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

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
      <div className="gh-main">
        <div className="gh-right gh-right--single">
          <div className="gh-card">
            <span className="gh-chip">Cyber Access</span>
            <div className="gh-auth-head">
              <h3>Secure Sign In</h3>
              <p>Authenticate to access the rate limiter control panel</p>
            </div>

            {error ? (
              <div className="gh-error">
                <AlertCircle size={16} />
                <span>{error}</span>
              </div>
            ) : null}

            <form className="gh-form" onSubmit={handleSubmit}>
              <label htmlFor="username">Username</label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
              />

              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />

              <button className="gh-submit" type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Signing in..." : "Sign in"}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
