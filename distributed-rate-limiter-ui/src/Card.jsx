import { useMemo } from "react";
import { 
  Activity, 
  CheckCircle, 
  XCircle,
  TrendingUp,
  TrendingDown
} from "lucide-react";
import "./Cards.css";

function StatsCards({ stats, loading }) {
  const formatPercent = (value) => {
    const numericValue = Number(value ?? 0);
    if (!Number.isFinite(numericValue)) return "0%";
    return numericValue >= 10 
      ? `${numericValue.toFixed(1)}%` 
      : `${numericValue.toFixed(2)}%`;
  };

  const cards = useMemo(() => [
    {
      title: "Total Requests",
      value: stats.totalRequests,
      caption: "All requests processed",
      icon: Activity,
      change: formatPercent(stats.totalPercent),
      trend: stats.totalPercent > 0 ? "up" : "down",
      color: "#94a3b8"
    },
    {
      title: "Allowed",
      value: stats.allowedRequests,
      caption: "Passed rate limits",
      icon: CheckCircle,
      change: formatPercent(stats.allowedPercent),
      trend: stats.allowedPercent > 0 ? "up" : "down",
      color: "#4ade80"
    },
    {
      title: "Blocked",
      value: stats.blockedRequests,
      caption: "Throttled requests",
      icon: XCircle,
      change: formatPercent(stats.blockedPercent),
      trend: stats.blockedPercent > 0 ? "down" : "up",
      color: "#f87171"
    }
  ], [stats]);

  if (loading) {
    return (
      <div className="cards-skeleton">
        <div></div>
        <div></div>
        <div></div>
      </div>
    );
  }

  return (
    <div className="stats-grid">
      {cards.map((card, index) => {
        const Icon = card.icon;
        const rawValue = card.value ?? "-";
        const formattedValue = rawValue === "-" ? rawValue : new Intl.NumberFormat().format(rawValue);

        return (
          <div key={index} className="stat-card">
            <div className="card-header">
              <div className="card-icon-wrapper">
                <Icon size={20} color={card.color} />
              </div>
              <h3 className="card-title">{card.title}</h3>
            </div>

            <div className="card-body">
              <p className="card-value">{formattedValue}</p>
              <p className="card-caption">{card.caption}</p>
            </div>

            <div className="card-footer">
              <div className={`trend-badge ${card.trend}`}>
                {card.trend === "up" ? (
                  <TrendingUp size={14} />
                ) : (
                  <TrendingDown size={14} />
                )}
                <span>{card.change}</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default StatsCards;