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
  const [profileForm, setProfileForm] = useState({ fullName: "", email: "" });
  const [profileSaving, setProfileSaving] = useState(false);

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
        setProfileForm({
          fullName: data?.fullName ?? "",
          email: data?.email ?? "",
        });
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

  const handleProfileFieldChange = (field, value) => {
    setProfileForm((current) => ({ ...current, [field]: value }));
  };

  const handleProfileSave = async () => {
    setProfileSaving(true);
    setSaveMessage("");

    try {
      const response = await fetch(apiUrl("/api/auth/me"), {
        method: "PUT",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          fullName: profileForm.fullName.trim(),
          email: profileForm.email.trim(),
        }),
      });

      const data = await response.json();
      if (!response.ok) {
        throw new Error(data?.message || "Unable to save settings.");
      }

      setCurrentAdmin(data);
      setProfileForm({
        fullName: data?.fullName ?? "",
        email: data?.email ?? "",
      });
      setSaveMessage("Profile saved.");
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : "Unable to save settings.");
    } finally {
      setProfileSaving(false);
      window.setTimeout(() => setSaveMessage(""), 1800);
    }
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
          <>
            <div className="settings-simple-grid">
              <div className="settings-simple-row">
                <span>User ID</span>
                <strong>{formatValue(currentAdmin.userId)}</strong>
              </div>
            </div>

            <div className="settings-form profile-form">
              <div className="settings-field">
                <label htmlFor="admin-full-name">Full name</label>
                <input
                  id="admin-full-name"
                  type="text"
                  value={profileForm.fullName}
                  onChange={(event) => handleProfileFieldChange("fullName", event.target.value)}
                />
              </div>

              <div className="settings-field">
                <label htmlFor="admin-email">Email</label>
                <input
                  id="admin-email"
                  type="email"
                  value={profileForm.email}
                  onChange={(event) => handleProfileFieldChange("email", event.target.value)}
                />
              </div>

              <div className="settings-actions-inline">
                <button type="button" className="settings-save-btn" onClick={handleProfileSave} disabled={profileSaving}>
                  <Save size={16} />
                  {profileSaving ? "Saving..." : "Save Profile"}
                </button>
              </div>
            </div>
          </>
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
