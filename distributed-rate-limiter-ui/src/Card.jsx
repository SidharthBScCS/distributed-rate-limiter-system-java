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
    
    if (numericValue >= 10) {
      return `${numericValue.toFixed(1)}%`;
    }
    return `${numericValue.toFixed(2)}%`;
  };

  const cards = useMemo(() => [
    {
      title: "Total Requests",
      value: stats.totalRequests,
      caption: "Total traffic",
      icon: Activity,
      change: formatPercent(stats.totalPercent),
      trend: "up",
      color: "#4299e1",
      bgColor: "rgba(66, 153, 225, 0.15)"
    },
    {
      title: "Allowed",
      value: stats.allowedRequests,
      caption: "Passed rate limits",
      icon: CheckCircle,
      change: formatPercent(stats.allowedPercent),
      trend: "up",
      color: "#48bb78",
      bgColor: "rgba(72, 187, 120, 0.15)"
    },
    {
      title: "Blocked",
      value: stats.blockedRequests,
      caption: "Throttled requests",
      icon: XCircle,
      change: formatPercent(stats.blockedPercent),
      trend: "down",
      color: "#f56565",
      bgColor: "rgba(245, 101, 101, 0.15)"
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
              <div className="card-icon-wrapper" style={{ background: card.bgColor }}>
                <Icon size={20} color={card.color} />
              </div>
            </div>

            <div className="card-body">
              <h3 className="card-title">{card.title}</h3>
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