import { useEffect, useMemo, useState } from "react";
import "../Styles/Analytics.css";

function buildGrafanaCandidates(grafanaDashboardUrl) {
  if (!grafanaDashboardUrl) {
    return [];
  }

  const raw = String(grafanaDashboardUrl).trim();
  if (!raw) {
    return [];
  }

  const candidates = [raw];
  try {
    const url = new URL(raw);
    const host = url.hostname;
    const port = url.port;
    const isLocal = host === "localhost" || host === "127.0.0.1";
    if (isLocal && (port === "3001" || port === "3002")) {
      const nextPort = port === "3001" ? "3002" : "3001";
      url.port = nextPort;
      candidates.push(url.toString());
    }
  } catch {
    // Leave only the raw value if it is not a valid URL.
  }

  return Array.from(new Set(candidates));
}

function Analytics({ grafanaDashboardUrl }) {
  const [embedBlocked, setEmbedBlocked] = useState(false);
  const [frameLoaded, setFrameLoaded] = useState(false);
  const candidates = useMemo(() => buildGrafanaCandidates(grafanaDashboardUrl), [grafanaDashboardUrl]);
  const [candidateIndex, setCandidateIndex] = useState(0);
  const resolvedGrafanaUrl = candidates[candidateIndex] || "";

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEmbedBlocked(false);
    setFrameLoaded(false);
    setCandidateIndex(0);
  }, [grafanaDashboardUrl]);

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
