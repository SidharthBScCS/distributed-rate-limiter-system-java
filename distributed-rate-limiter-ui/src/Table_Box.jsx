import { useState, useEffect } from "react";
import { Plus, Copy } from "lucide-react";
import { apiUrl } from "./apiBase";
import "./Table_Box.css";

function ApiTable({ refreshTick }) {
  const [keys, setKeys] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchKeys();
  }, [refreshTick]);

  const fetchKeys = async () => {
    try {
      const res = await fetch(apiUrl("/api/view/dashboard"), { credentials: "include" });
      const data = await res.json();
      setKeys(data.apiKeys || []);
    } catch {
      console.error("Failed to fetch keys");
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status) => {
    switch (status?.toUpperCase()) {
      case "BLOCKED":
        return { bg: "rgba(239, 68, 68, 0.1)", color: "#EF4444", dot: "#EF4444" };
      case "WARNING":
        return { bg: "rgba(245, 158, 11, 0.1)", color: "#F59E0B", dot: "#F59E0B" };
      default:
        return { bg: "rgba(16, 185, 129, 0.1)", color: "#10B981", dot: "#10B981" };
    }
  };

  if (loading) {
    return <div className="table-skeleton" />;
  }

  return (
    <div className="api-table-container">
      <div className="table-header">
        <div>
          <h2>API Keys</h2>
          <p>Manage and monitor your API access keys</p>
        </div>
        <button className="create-btn">
          <Plus size={18} />
          New API Key
        </button>
      </div>

      <div className="table-wrapper">
        <table className="api-table">
          <thead>
            <tr>
              <th>API Key</th>
              <th>User</th>
              <th>Rate Limit</th>
              <th>Window</th>
              <th>Algorithm</th>
              <th>Usage</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {keys.length === 0 ? (
              <tr>
                <td colSpan="7" className="empty-state">
                  <div className="empty-content">
                    <div className="empty-icon">KEY</div>
                    <h3>No API keys found</h3>
                    <p>Get started by creating your first API key</p>
                    <button className="create-btn">
                      <Plus size={16} />
                      Create API Key
                    </button>
                  </div>
                </td>
              </tr>
            ) : (
              keys.map((key) => {
                const status = getStatusColor(key.status);
                const usage = key.usagePercentage || 0;

                return (
                  <tr key={key.id} className="table-row">
                    <td>
                      <div className="key-cell">
                        <code className="key-code">{key.apiKey}</code>
                        <button
                          className="action-btn"
                          onClick={() => navigator.clipboard.writeText(key.apiKey)}
                        >
                          <Copy size={14} />
                        </button>
                      </div>
                    </td>
                    <td>
                      <span className="user-name">{key.userName}</span>
                    </td>
                    <td>
                      <span className="rate-value">{key.rateLimit}</span>
                      <span className="rate-unit">/window</span>
                    </td>
                    <td>
                      <span className="window-value">{key.windowSeconds}s</span>
                    </td>
                    <td>
                      <span className="algo-badge">{key.algorithm}</span>
                    </td>
                    <td>
                      <div className="usage-cell">
                        <div className="usage-header">
                          <span style={{ color: usage > 80 ? "#EF4444" : "#10B981" }}>
                            {usage.toFixed(1)}%
                          </span>
                        </div>
                        <div className="usage-bar">
                          <div
                            className="usage-progress"
                            style={{
                              width: `${usage}%`,
                              background: usage > 80 ? "#EF4444" : "#10B981",
                            }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="status-cell" style={{ background: status.bg }}>
                        <span className="status-dot" style={{ background: status.dot }} />
                        <span style={{ color: status.color }}>{key.status || "NORMAL"}</span>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default ApiTable;

