import { useState, useEffect } from "react";
import { Plus, Copy } from "lucide-react";
import { apiUrl } from "./apiBase";
import "./Table_Box.css";

function ApiTable({ refreshTick, defaults }) {
  const defaultRateLimit = defaults.rateLimit;
  const defaultWindowSeconds = defaults.windowSeconds;
  const [keys, setKeys] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [formState, setFormState] = useState({
    userName: "",
    rateLimit: defaultRateLimit,
    windowSeconds: defaultWindowSeconds,
  });
  const [createError, setCreateError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [copiedText, setCopiedText] = useState("");
  const [successModal, setSuccessModal] = useState({
    open: false,
    apiKey: "",
    userName: "",
  });

  const formatUsage = (value) => {
    const numericValue = Number(value ?? 0);
    if (!Number.isFinite(numericValue)) {
      return "0%";
    }
    if (numericValue === 100) {
      return "100%";
    }
    const rounded = numericValue >= 10 ? numericValue.toFixed(1) : numericValue.toFixed(2);
    return `${rounded.replace(/\.0+$/, "").replace(/(\.\d*[1-9])0+$/, "$1")}%`;
  };

  useEffect(() => {
    fetchKeys();
  }, [refreshTick]);

  useEffect(() => {
    if (!isCreateModalOpen) {
      setFormState({
        userName: "",
        rateLimit: defaultRateLimit,
        windowSeconds: defaultWindowSeconds,
      });
    }
  }, [defaultRateLimit, defaultWindowSeconds, isCreateModalOpen]);

  useEffect(() => {
    const anyModalOpen = isCreateModalOpen || successModal.open;
    if (anyModalOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }

    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        if (successModal.open) {
          setSuccessModal({ open: false, apiKey: "", userName: "" });
          return;
        }
        if (isCreateModalOpen && !isSubmitting) {
          setIsCreateModalOpen(false);
        }
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [isCreateModalOpen, successModal.open, isSubmitting]);

  const handleCopy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedText("Copied to clipboard");
      window.setTimeout(() => setCopiedText(""), 1200);
    } catch {
      setCopiedText("Copy failed");
      window.setTimeout(() => setCopiedText(""), 1200);
    }
  };

  const fetchKeys = async () => {
    try {
      const res = await fetch(apiUrl("/api/view/dashboard"), { credentials: "include" });
      if (!res.ok) {
        throw new Error(`Failed dashboard table request: HTTP ${res.status}`);
      }
      const data = await res.json();
      const sortedKeys = [...(data.apiKeys ?? [])].sort((left, right) => {
        const usageDiff = Number(right.usagePercentage ?? 0) - Number(left.usagePercentage ?? 0);
        if (usageDiff !== 0) {
          return usageDiff;
        }

        const requestDiff = Number(right.requestCount ?? 0) - Number(left.requestCount ?? 0);
        if (requestDiff !== 0) {
          return requestDiff;
        }

        return String(left.userName ?? "").localeCompare(String(right.userName ?? ""));
      });
      setKeys(sortedKeys);
    } catch {
      setKeys([]);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="table-skeleton" />;
  }

  const openCreateModal = () => {
    setCreateError("");
    setFormState({
      userName: "",
      rateLimit: defaultRateLimit,
      windowSeconds: defaultWindowSeconds,
    });
    setIsCreateModalOpen(true);
  };

  const closeCreateModal = () => {
    if (isSubmitting) return;
    setIsCreateModalOpen(false);
  };

  const updateField = (field, value) => {
    setFormState((prev) => ({ ...prev, [field]: value }));
  };

  const handleCreateApiKey = async (event) => {
    event.preventDefault();
    setCreateError("");
    setIsSubmitting(true);

    const payload = {
      userName: formState.userName,
      rateLimit: formState.rateLimit,
      windowSeconds: formState.windowSeconds,
    };

    try {
      const response = await fetch(apiUrl("/api/keys"), {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const body = await response.json();
        const message = body.message;
        throw new Error(message);
      }
      const created = await response.json();

      await fetchKeys();
      setIsCreateModalOpen(false);
      setSuccessModal({
        open: true,
        apiKey: created.apiKey,
        userName: created.userName,
      });
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="api-table-container">
      <div className="table-header">
        <div>
          <h2>API Keys</h2>
          <p>Manage and monitor your API access keys</p>
        </div>
        <button className="create-btn" onClick={openCreateModal}>
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
                    <h3>NO API KEY FOUND</h3>
                    <button className="empty-action-btn" onClick={openCreateModal}>
                      <Plus size={16} />
                      Create First API Key
                    </button>
                  </div>
                </td>
              </tr>
            ) : (
              keys.map((key) => {
                const statusColor = key.statusColor;
                const usage = key.usagePercentage;
                const usageColor = key.usageColor;
                const fullApiKey = key.apiKey;
                const usageLabel = formatUsage(usage);

                return (
                  <tr key={key.id} className="table-row">
                    <td>
                      <div className="key-cell">
                        <code className="key-code">{fullApiKey}</code>
                        <button
                          className="action-btn"
                          onClick={() => handleCopy(fullApiKey)}
                          aria-label="Copy API key"
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
                      <code className="algo-code">SLIDING_WINDOW</code>
                    </td>
                    <td>
                      <div className="usage-cell">
                        <div className="usage-header">
                          <span>{key.requestCount} req</span>
                          <span style={{ color: usageColor }}>
                            {usageLabel}
                          </span>
                        </div>
                        <div className="usage-bar">
                          <div
                            className="usage-progress"
                            style={{
                              width: `${usage}%`,
                              background: usageColor,
                            }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="status-cell" style={{ background: `${statusColor}22` }}>
                        <span className="status-dot" style={{ background: statusColor }} />
                        <span style={{ color: statusColor }}>{key.status}</span>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {isCreateModalOpen ? (
        <div className="modal-overlay" onClick={closeCreateModal}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <h3>Create API Key</h3>
            <form className="create-form" onSubmit={handleCreateApiKey}>
              <label>
                User Name
                <input
                  type="text"
                  value={formState.userName}
                  onChange={(event) => updateField("userName", event.target.value)}
                />
              </label>

              <label>
                Rate Limit
                <input
                  type="number"
                  value={formState.rateLimit}
                  onChange={(event) => updateField("rateLimit", event.target.value)}
                />
              </label>

              <label>
                Window (seconds)
                <input
                  type="number"
                  value={formState.windowSeconds}
                  onChange={(event) => updateField("windowSeconds", event.target.value)}
                />
              </label>

              {createError ? <p className="create-error">{createError}</p> : null}

              <div className="modal-actions">
              <button type="button" className="secondary-btn" onClick={closeCreateModal} disabled={isSubmitting}>
                Cancel
              </button>
                <button type="submit" className="create-btn" disabled={isSubmitting}>
                  {isSubmitting ? "Creating..." : "Create API Key"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {successModal.open ? (
        <div className="modal-overlay" onClick={() => setSuccessModal({ open: false, apiKey: "", userName: "" })}>
          <div className="modal-card success-modal" onClick={(event) => event.stopPropagation()}>
            <h3>API Key Created Successfully</h3>
            <p className="success-message">
              New API key has been created for <strong>{successModal.userName}</strong>.
            </p>
            <div className="success-key-box">
              <code>{successModal.apiKey}</code>
              <button
                type="button"
                className="action-btn"
                onClick={() => handleCopy(successModal.apiKey)}
                aria-label="Copy API key"
              >
                <Copy size={16} />
              </button>
            </div>
            <div className="modal-actions">
              <button
                type="button"
                className="create-btn"
                onClick={() => setSuccessModal({ open: false, apiKey: "", userName: "" })}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {copiedText ? <div className="copy-toast">{copiedText}</div> : null}
    </div>
  );
}

export default ApiTable;

