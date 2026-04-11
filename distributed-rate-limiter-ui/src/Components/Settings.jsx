import { useEffect, useState } from "react";
import { Save, UserRound } from "lucide-react";
import { apiUrl } from "../apiBase.js";
import { buildAuthHeaders, withAuth } from "../auth.js";
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
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let mounted = true;

    const loadAdmin = async () => {
      try {
        const response = await fetch(apiUrl("/api/auth/me"), {
          ...withAuth(),
          headers: buildAuthHeaders(),
          cache: "no-store",
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

  const handleSave = async () => {
    setSaving(true);
    setSaveMessage("");

    try {
      const response = await fetch(apiUrl("/api/auth/me"), {
        ...withAuth({
          headers: buildAuthHeaders({
            "Content-Type": "application/json",
          }),
        }),
        method: "PUT",
        cache: "no-store",
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
      writeAppPreferences(draft);
      window.dispatchEvent(new Event("app-preferences-changed"));
      setSaveMessage("Settings saved.");
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : "Unable to save settings.");
    } finally {
      setSaving(false);
      window.setTimeout(() => setSaveMessage(""), 1800);
    }
  };

  return (
    <div className="settings-page">
      <section className="settings-panel">
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

            </div>
          </>
        ) : (
          <div className="analytics-empty-state">
            <p>{statusMessage}</p>
          </div>
        )}

        <div className="settings-divider"></div>

        <div className="settings-footer">
          <p>{saveMessage || "Preferences are saved only in this browser."}</p>
          <button type="button" className="settings-save-btn" onClick={handleSave} disabled={saving}>
            <Save size={16} />
            {saving ? "Saving..." : "Save"}
          </button>
        </div>
      </section>
    </div>
  );
}

export default Settings;
