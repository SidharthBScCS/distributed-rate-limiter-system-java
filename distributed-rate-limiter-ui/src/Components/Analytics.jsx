import React from "react";
import "../Styles/Analytics.css";

function Analytics({ grafanaDashboardUrl }) {
  const [embedBlocked, setEmbedBlocked] = React.useState(false);

  return (
    <div className="analytics-page page-full-bleed">
      <section className="grafana-full-section">
        {grafanaDashboardUrl ? (
          <div className="grafana-frame-wrap grafana-frame-wrap--full">
            <iframe
              title="Grafana Analytics"
              src={grafanaDashboardUrl}
              className="grafana-frame grafana-frame--full"
              loading="lazy"
              onError={() => setEmbedBlocked(true)}
            />
            {embedBlocked ? (
              <div className="grafana-empty">
                Embedding is blocked by Grafana security headers. Use <code>Open in Grafana</code>.
              </div>
            ) : null}
          </div>
        ) : (
          <div className="grafana-empty">
            Grafana dashboard URL is not configured on backend.
          </div>
        )}
      </section>
    </div>
  );
}

export default Analytics;
