import { useState, useEffect } from "react";
import { Plus, Copy, Search } from "lucide-react";
import { apiUrl } from "../apiBase.js";
import { buildAuthHeaders, withAuth } from "../auth.js";
import "../Styles/Table_Box.css";

function formatNumber(value) {
  const numericValue = Number(value ?? 0);
  return Number.isFinite(numericValue) ? numericValue.toLocaleString() : "0";
}

function formatUsagePercentage(value) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) {
    return "0%";
  }
  if (numericValue === 100) {
    return "100%";
  }
  return `${numericValue >= 10 ? numericValue.toFixed(1) : numericValue.toFixed(2)}`
    .replace(/\.0+$/, "")
    .replace(/(\.\d*[1-9])0+$/, "$1") + "%";
}

function usageColor(percentage) {
  if (percentage > 90) return "#ef4444";
  if (percentage > 70) return "#f59e0b";
  return "#10b981";
}

function statusColor(status) {
  const value = String(status ?? "").toLowerCase();
  if (value === "blocked") return "#ef4444";
  if (value === "warning") return "#f59e0b";
  if (value === "normal") return "#10b981";
  return "#94a3b8";
}

function ApiTable({
  dashboardData,
  loading,
  refreshing,
  defaults,
  onDashboardRefresh,
  tableQuery,
  onTableQueryChange,
}) {
  const defaultRateLimit = defaults.rateLimit;
  const defaultWindowSeconds = defaults.windowSeconds;
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

  const keys = dashboardData.apiKeys ?? [];
  const pagination = dashboardData.pagination;
  const searchTerm = tableQuery.search;
  const [searchInput, setSearchInput] = useState(searchTerm);
  const currentPage = tableQuery.page;
  const isPaginationBusy = refreshing && tableQuery.page !== pagination.page;

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
    setSearchInput(searchTerm);
  }, [searchTerm]);

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
  const pageSize = pagination.size ?? tableQuery.size ?? 15;
  const safeCurrentPage = Math.min(Math.max(1, currentPage), totalPages);
  const startIndex = totalItems === 0 ? 0 : (safeCurrentPage - 1) * pageSize + 1;
  const endIndex = Math.min(safeCurrentPage * pageSize, totalItems);

  const queueTableQueryUpdate = (nextQuery) => {
    onTableQueryChange(nextQuery);
  };

  useEffect(() => {
    const currentSearch = searchTerm.trim();
    const draftSearch = searchInput.trim();
    if (currentSearch === draftSearch) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      queueTableQueryUpdate({
        ...tableQuery,
        search: searchInput,
        page: 1,
      });
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [searchInput, searchTerm, tableQuery]);

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
        ...withAuth({
          headers: buildAuthHeaders({
            "Content-Type": "application/json",
          }),
        }),
        method: "POST",
        cache: "no-store",
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const body = await response.json();
        throw new Error(body.message);
      }
      const created = await response.json();
      const nextQuery = {
        ...tableQuery,
        search: "",
        page: 1,
      };
      onTableQueryChange(nextQuery);
      await onDashboardRefresh(true, nextQuery);
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
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                queueTableQueryUpdate({
                  ...tableQuery,
                  search: searchInput,
                  page: 1,
                });
              }
            }}
            placeholder="Search by API key, user, status, limit, or window"
            disabled={refreshing}
          />
        </label>
        {searchTerm.trim() ? (
          <button
            type="button"
            className="empty-action-btn"
            onClick={() => {
              setSearchInput("");
              queueTableQueryUpdate({
                ...tableQuery,
                search: "",
                page: 1,
              });
            }}
            disabled={refreshing}
          >
            Clear Search
          </button>
        ) : null}
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
                const keyUsageColor = usageColor(usage);
                const keyStatusColor = statusColor(key.status);
                const requestCountLabel = `${formatNumber(key.requestCount)} req`;
                const rateLimitLabel = `${key.rateLimit ?? 0}/window`;
                const windowLabel = `${key.windowSeconds ?? 0}s`;
                const algorithmLabel = key.algorithm?.trim() ? key.algorithm : "-";
                const usageLabel = formatUsagePercentage(usage);
                const statusLabel = key.status?.trim() ? key.status : "Unknown";

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
                      <span className="rate-value">{rateLimitLabel}</span>
                    </td>
                    <td>
                      <span className="window-value">{windowLabel}</span>
                    </td>
                    <td>
                      <code className="algo-code">{algorithmLabel}</code>
                    </td>
                    <td>
                      <div className="usage-cell">
                        <div className="usage-header">
                          <span>{requestCountLabel}</span>
                          <span style={{ color: keyUsageColor }}>{usageLabel}</span>
                        </div>
                        <div className="usage-bar">
                          <div
                            className="usage-progress"
                            style={{
                              width: `${usage}%`,
                              background: keyUsageColor,
                            }}
                          />
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="status-cell" style={{ background: `${keyStatusColor}22` }}>
                        <span className="status-dot" style={{ background: keyStatusColor }} />
                        <span style={{ color: keyStatusColor }}>{statusLabel}</span>
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
              !isPaginationBusy &&
              queueTableQueryUpdate({
                ...tableQuery,
                page: Math.max(1, safeCurrentPage - 1),
              })
            }
            disabled={safeCurrentPage === 1 || isPaginationBusy}
          >
            {isPaginationBusy ? "Loading..." : "Previous"}
          </button>
          <span className="pagination-status">
            Showing {totalItems === 0 ? 0 : startIndex}-{endIndex} of {totalItems}
            {searchTerm.trim() ? " (filtered)" : ""}
          </span>
          <button
            type="button"
            className="pagination-btn"
            onClick={() =>
              !isPaginationBusy &&
              queueTableQueryUpdate({
                ...tableQuery,
                page: Math.min(totalPages, safeCurrentPage + 1),
              })
            }
            disabled={safeCurrentPage === totalPages || isPaginationBusy}
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
