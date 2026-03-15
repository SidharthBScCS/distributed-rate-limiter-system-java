import { useEffect, useState } from "react";
import { 
  Activity, 
  CheckCircle, 
  XCircle,
  TrendingUp,
  TrendingDown
} from "lucide-react";
import { apiUrl } from "./apiBase";
import "./Cards.css";

function StatsCards({ refreshTick }) {
  const [stats, setStats] = useState({});
  const [loading, setLoading] = useState(true);

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

  useEffect(() => {
    fetchStats();
  }, [refreshTick]);

  const fetchStats = async () => {
    try {
      const res = await fetch(apiUrl("/api/view/dashboard"), { credentials: "include" });
      if (!res.ok) {
        throw new Error(`Failed dashboard stats request: HTTP ${res.status}`);
      }
      const data = await res.json();
      setStats(data?.stats ?? {});
    } catch {
      setStats({});
    } finally {
      setLoading(false);
    }
  };

  const cards = [
    {
      title: "Total Requests",
      value: stats.totalRequests,
      caption: "All traffic processed",
      icon: Activity,
      change: formatPercent(stats.totalPercent),
      trend: "up",
      color: "#8B949E",
      bgColor: "rgba(139, 148, 158, 0.16)"
    },
    {
      title: "Allowed Requests",
      value: stats.allowedRequests,
      caption: "Passed rate-limit checks",
      icon: CheckCircle,
      change: formatPercent(stats.allowedPercent),
      trend: "up",
      color: "#3FB950",
      bgColor: "rgba(63, 185, 80, 0.14)"
    },
    {
      title: "Blocked Requests",
      value: stats.blockedRequests,
      caption: "Throttled by the limiter",
      icon: XCircle,
      change: formatPercent(stats.blockedPercent),
      trend: "down",
      color: "#F85149",
      bgColor: "rgba(248, 81, 73, 0.14)"
    }
  ];

  if (loading) {
    return <div className="cards-skeleton" />;
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
            style={{ animationDelay: `${index * 0.1}s` }}
          >
            <div className="card-shine" />
            <div className="card-header">
              <div className="card-icon-wrapper" style={{ background: card.bgColor }}>
                <Icon size={22} color={card.color} />
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
                  <TrendingUp size={14} />
                ) : (
                  <TrendingDown size={14} />
                )}
                <span>{card.change}</span>
              </div>
            </div>

            <div className="card-glow" style={{ background: card.color }} />
          </div>
        );
      })}
    </div>
  );
}

export default StatsCards;
