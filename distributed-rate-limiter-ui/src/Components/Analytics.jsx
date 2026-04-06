import { useEffect, useMemo, useState } from "react";
import "../Styles/Analytics.css";

function normalizeGrafanaUrl(grafanaDashboardUrl) {
  if (!grafanaDashboardUrl) {
    return "http://localhost:3001/d/adqskbg/total-request-rate?orgId=1&from=now-6h&to=now&timezone=browser";
  }

  return grafanaDashboardUrl
    .replace("http://localhost:3002", "http://localhost:3001")
    .replace("http://127.0.0.1:3002", "http://127.0.0.1:3001");
}

function Analytics({ grafanaDashboardUrl }) {
  const [embedBlocked, setEmbedBlocked] = useState(false);
  const [frameLoaded, setFrameLoaded] = useState(false);
  const resolvedGrafanaUrl = useMemo(() => normalizeGrafanaUrl(grafanaDashboardUrl), [grafanaDashboardUrl]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setEmbedBlocked(false);
    setFrameLoaded(false);

    const timeoutId = window.setTimeout(() => {
      setEmbedBlocked(true);
    }, 4000);

    return () => window.clearTimeout(timeoutId);
  }, [resolvedGrafanaUrl]);

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
                <p>Local Grafana is not reachable in the embedded view.</p>
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
