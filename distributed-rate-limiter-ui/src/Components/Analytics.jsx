import { useEffect, useMemo, useState } from "react";
import "../Styles/Analytics.css";

function normalizeGrafanaCandidates(grafanaDashboardUrls, grafanaDashboardUrl) {
  const values = Array.isArray(grafanaDashboardUrls) ? grafanaDashboardUrls : [];
  const normalized = values
    .map((value) => String(value ?? "").trim())
    .filter(Boolean);

  if (normalized.length > 0) {
    return Array.from(new Set(normalized));
  }

  const fallback = String(grafanaDashboardUrl ?? "").trim();
  return fallback ? [fallback] : [];
}

function Analytics({ grafanaDashboardUrl, grafanaDashboardUrls }) {
  const [embedBlocked, setEmbedBlocked] = useState(false);
  const [frameLoaded, setFrameLoaded] = useState(false);
  const candidates = useMemo(
    () => normalizeGrafanaCandidates(grafanaDashboardUrls, grafanaDashboardUrl),
    [grafanaDashboardUrl, grafanaDashboardUrls]
  );
  const [candidateIndex, setCandidateIndex] = useState(0);
  const resolvedGrafanaUrl = candidates[candidateIndex] || "";

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEmbedBlocked(false);
    setFrameLoaded(false);
    setCandidateIndex(0);
  }, [grafanaDashboardUrl, grafanaDashboardUrls]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEmbedBlocked(false);
    setFrameLoaded(false);

    const timeoutId = window.setTimeout(() => {
      setEmbedBlocked(true);
    }, 4000);

    return () => window.clearTimeout(timeoutId);
  }, [resolvedGrafanaUrl]);

  useEffect(() => {
    if (!embedBlocked || frameLoaded) {
      return;
    }
    if (candidateIndex < candidates.length - 1) {
      setCandidateIndex((current) => Math.min(current + 1, candidates.length - 1));
    }
  }, [embedBlocked, frameLoaded, candidateIndex, candidates.length]);

  return (
    <div className="analytics-page page-full-bleed">
      <section className="grafana-full-section">
        {resolvedGrafanaUrl ? (
          <div className="grafana-frame-wrap grafana-frame-wrap--full">
            <iframe
              title="Grafana Analytics"
              src={resolvedGrafanaUrl}
              className="grafana-frame grafana-frame--full"
              loading="lazy"
              onLoad={() => {
                setFrameLoaded(true);
                setEmbedBlocked(false);
              }}
              onError={() => setEmbedBlocked(true)}
            />
            {embedBlocked && !frameLoaded ? (
              <div className="grafana-empty">
                <p>Grafana is not reachable in the embedded view.</p>
                <a href={resolvedGrafanaUrl} target="_blank" rel="noreferrer" className="grafana-open-link">
                  Open in Grafana
                </a>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="grafana-empty">Grafana dashboard URL is not configured on backend.</div>
        )}
      </section>
    </div>
  );
}

export default Analytics;
