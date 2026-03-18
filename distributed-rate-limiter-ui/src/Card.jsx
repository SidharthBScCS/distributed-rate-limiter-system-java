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
    if (!Number.isFinite(numericValue)) {
      return "0%";
    }
    const rounded = numericValue >= 10
      ? numericValue.toFixed(1)
      : numericValue.toFixed(2);
    return `${rounded.replace(/\.0+$/, "").replace(/(\.\d*[1-9])0+$/, "$1")}%`;
  };

  const cards = useMemo(() => [
    {
      title: "Total Requests",
      value: stats.totalRequests,
      caption: "All traffic processed",
      icon: Activity,
      change: formatPercent(stats.totalPercent),
      trend: "up",
      color: "#4299e1",
      bgColor: "rgba(66, 153, 225, 0.16)"
    },
    {
      title: "Allowed Requests",
      value: stats.allowedRequests,
      caption: "Passed rate-limit checks",
      icon: CheckCircle,
      change: formatPercent(stats.allowedPercent),
      trend: "up",
      color: "#48bb78",
      bgColor: "rgba(72, 187, 120, 0.14)"
    },
    {
      title: "Blocked Requests",
      value: stats.blockedRequests,
      caption: "Throttled by the limiter",
      icon: XCircle,
      change: formatPercent(stats.blockedPercent),
      trend: "down",
      color: "#f56565",
      bgColor: "rgba(245, 101, 101, 0.14)"
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
          <div 
            key={index} 
            className="stat-card"
            style={{ 
              '--card-index': index,
              animationDelay: `${index * 0.2}s`
            }}
          >
            <div className="card-pattern" />
            <div className="card-shine" />
            
            <div className="card-header">
              <div className="card-icon-wrapper" style={{ background: card.bgColor }}>
                <Icon size={24} color={card.color} />
              </div>
            </div>

            <div className="card-body">
              <h3 className="card-title">{card.title}</h3>
              <p className="card-value">{formattedValue}</p>
              <p className="card-caption">{card.caption}</p>
            </div>

            <div className="card-footer card-footer--compact">
              <div className={`trend-badge ${card.trend}`}>
                {card.trend === "up" ? (
                  <TrendingUp size={16} />
                ) : (
                  <TrendingDown size={16} />
                )}
                <span>{card.change}</span>
              </div>
            </div>

            <div className="card-glow" />
          </div>
        );
      })}
    </div>
  );
}

export default StatsCards;