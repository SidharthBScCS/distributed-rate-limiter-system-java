import { useEffect, useState } from "react";
import { Save, UserRound } from "lucide-react";
import { apiUrl } from "../apiBase.js";
import { readAppPreferences, writeAppPreferences } from "../preferences.js";
import "../Styles/Settings.css";

function formatValue(value, fallback = "Not available") {
  if (value === null || value === undefined || value === "") {
    return fallback;
  }
  return String(value);
}

function Settings() {
  const [draft, setDraft] = useState(() => readAppPreferences());
  const [currentAdmin, setCurrentAdmin] = useState(null);
  const [statusMessage, setStatusMessage] = useState("Loading admin details...");
  const [saveMessage, setSaveMessage] = useState("");

  useEffect(() => {
    let mounted = true;

    const loadAdmin = async () => {
      try {
        const response = await fetch(apiUrl("/api/auth/me"), {
          credentials: "include",
        });

        if (!response.ok) {
          throw new Error("Unable to load admin details.");
        }

        const data = await response.json();
        if (!mounted) {
          return;
        }

        setCurrentAdmin(data);
        setStatusMessage("");
      } catch (error) {
        if (!mounted) {
          return;
        }
        setStatusMessage(error instanceof Error ? error.message : "Unable to load admin details.");
      }
    };

    void loadAdmin();

    return () => {
      mounted = false;
    };
  }, []);

  const handleChange = (field, value) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };

  const handleSave = () => {
    writeAppPreferences(draft);
    window.dispatchEvent(new Event("app-preferences-changed"));
    setSaveMessage("Settings saved.");
    window.setTimeout(() => setSaveMessage(""), 1600);
  };

  return (
    <div className="settings-page">
      <section className="settings-single-card">
        <div className="settings-single-header">
          <div className="settings-avatar">
            <UserRound size={26} />
          </div>
          <div>
            <p className="settings-overline">Admin Settings</p>
            <h1>{formatValue(currentAdmin?.fullName, "Administrator")}</h1>
            <span className="settings-subtitle">Single place to view admin info and adjust local app behavior.</span>
          </div>
        </div>

        {currentAdmin ? (
          <div className="settings-simple-grid">
            <div className="settings-simple-row">
              <span>Name</span>
              <strong>{formatValue(currentAdmin.fullName)}</strong>
            </div>
            <div className="settings-simple-row">
              <span>User ID</span>
              <strong>{formatValue(currentAdmin.userId)}</strong>
            </div>
            <div className="settings-simple-row">
              <span>Email</span>
              <strong>{formatValue(currentAdmin.email)}</strong>
            </div>
          </div>
        ) : (
          <div className="analytics-empty-state">
            <p>{statusMessage}</p>
          </div>
        )}

        <div className="settings-divider"></div>

        <div className="settings-form">
          <div className="settings-field">
            <label htmlFor="default-page-size">Default table page size</label>
            <select
              id="default-page-size"
              value={draft.defaultPageSize}
              onChange={(event) => handleChange("defaultPageSize", Number(event.target.value))}
            >
              <option value={5}>5 rows</option>
              <option value={10}>10 rows</option>
              <option value={20}>20 rows</option>
              <option value={50}>50 rows</option>
            </select>
          </div>
        </div>

        <div className="settings-footer">
          <p>{saveMessage || "Preferences are saved only in this browser."}</p>
          <button type="button" className="settings-save-btn" onClick={handleSave}>
            <Save size={16} />
            Save
          </button>
        </div>
      </section>
    </div>
  );
}

export default Settings;
