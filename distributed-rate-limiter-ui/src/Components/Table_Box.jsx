import { useState, useEffect } from "react";
import { Plus, Copy, Search } from "lucide-react";
import { apiUrl } from "../apiBase.js";
import "../Styles/Table_Box.css";

function ApiTable({
  dashboardData,
  loading,
  defaults,
  onDashboardRefresh,
  tableQuery,
  onTableQueryChange,
}) {
  const defaultRateLimit = defaults.rateLimit;
  const defaultWindowSeconds = defaults.windowSeconds;
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [formState, setFormState] = useState<CreateFormState>({
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

  const keys = dashboardData.apiKeys ?? [];
  const pagination = dashboardData.pagination;
  const searchTerm = tableQuery.search;
  const currentPage = pagination.page;

  useEffect(() => {
    const nextSearch = pagination.search;
    const nextPage = pagination.page;
    const nextSize = pagination.size ?? tableQuery.size ?? 10;
    if (nextSearch !== tableQuery.search || nextPage !== tableQuery.page || nextSize !== tableQuery.size) {
      onTableQueryChange({ search: nextSearch, page: nextPage, size: nextSize });
    }
  }, [onTableQueryChange, pagination.page, pagination.search, pagination.size, tableQuery.page, tableQuery.search, tableQuery.size]);

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
    document.body.style.overflow = anyModalOpen ? "hidden" : "";

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

  const totalPages = Math.max(1, pagination.totalPages ?? 1);
  const totalItems = pagination.totalItems ?? keys.length;
  const pageSize = pagination.size ?? tableQuery.size ?? 10;
  const safeCurrentPage = Math.min(currentPage, totalPages);
  const startIndex = totalItems === 0 ? 0 : (safeCurrentPage - 1) * pageSize + 1;
  const endIndex = Math.min(safeCurrentPage * pageSize, totalItems);

  if (loading) {
    return <div className="table-skeleton" />;
  }

  const handleCreateApiKey = async (event) => {
    event.preventDefault();
    setCreateError("");
    setIsSubmitting(true);

    const payload = {
      userName: formState.userName,
      rateLimit: Number(formState.rateLimit),
      windowSeconds: Number(formState.windowSeconds),
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
        throw new Error(body.message);
      }
      const created = await response.json();

      await onDashboardRefresh(true);
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

      <div className="table-toolbar">
        <label className="table-search">
          <Search size={16} />
          <input
            type="text"
            value={searchTerm}
            onChange={(event) =>
              onTableQueryChange({
                ...tableQuery,
                search: event.target.value,
                page: 1,
              })
            }
            placeholder="Search by API key, user, status, limit, or window"
          />
        </label>
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
                <td colSpan={7} className="empty-state">
                  <div className="empty-content">
                    <h3>{totalItems === 0 && !searchTerm.trim() ? "NO API KEY FOUND" : "NO MATCHING API KEY"}</h3>
                    {totalItems === 0 && !searchTerm.trim() ? (
                      <button className="empty-action-btn" onClick={openCreateModal}>
                        <Plus size={16} />
                        Create First API Key
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ) : (
              keys.map((key) => {
                const usage = key.usagePercentage;
                const fullApiKey = key.apiKey;

                return (
                  <tr key={key.id} className="table-row">
                    <td>
                      <div className="key-cell">
                        <code className="key-code">{fullApiKey}</code>
                        <button className="action-btn" onClick={() => handleCopy(fullApiKey)} aria-label="Copy API key">
                          <Copy size={14} />
                        </button>
                      </div>
                    </td>
                    <td>
                      <span className="user-name">{key.userName}</span>
                    </td>
                    <td>
                      <span className="rate-value">{key.rateLimitLabel}</span>
                    </td>
                    <td>
                      <span className="window-value">{key.windowLabel}</span>
                    </td>
                    <td>
                      <code className="algo-code">{key.algorithmLabel}</code>
                    </td>
                    <td>
                      <div className="usage-cell">
                        <div className="usage-header">
                          <span>{key.requestCountLabel}</span>
                          <span style={{ color: key.usageColor }}>{key.usageLabel}</span>
                        </div>
                        <div className="usage-bar">
                          <div
                            className="usage-progress"
                            style={{
                              width: `${usage}%`,
                              background: key.usageColor,
                            }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="status-cell" style={{ background: `${key.statusColor}22` }}>
                        <span className="status-dot" style={{ background: key.statusColor }} />
                        <span style={{ color: key.statusColor }}>{key.statusLabel}</span>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {keys.length > 0 ? (
        <div className="table-pagination">
          <button
            type="button"
            className="pagination-btn"
            onClick={() =>
              onTableQueryChange({
                ...tableQuery,
                page: Math.max(1, safeCurrentPage - 1),
              })
            }
            disabled={safeCurrentPage === 1}
          >
            Previous
          </button>
          <span className="pagination-status">
            Showing {totalItems === 0 ? 0 : startIndex}-{endIndex} of {totalItems}
            {searchTerm.trim() ? " (filtered)" : ""}
          </span>
          <button
            type="button"
            className="pagination-btn"
            onClick={() =>
              onTableQueryChange({
                ...tableQuery,
                page: Math.min(totalPages, safeCurrentPage + 1),
              })
            }
            disabled={safeCurrentPage === totalPages}
          >
            Next
          </button>
        </div>
      ) : null}

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
                  onChange={(event) => updateField("rateLimit", Number(event.target.value))}
                />
              </label>

              <label>
                Window (seconds)
                <input
                  type="number"
                  value={formState.windowSeconds}
                  onChange={(event) => updateField("windowSeconds", Number(event.target.value))}
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
