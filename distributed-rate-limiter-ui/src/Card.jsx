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

  useEffect(() => {
    fetchStats();
  }, [refreshTick]);

  const fetchStats = async () => {
    try {
      const res = await fetch(apiUrl("/api/view/dashboard"), { credentials: "include" });
      const data = await res.json();
      setStats(data.stats || {});
    // eslint-disable-next-line no-unused-vars
    } catch (error) {
      console.error("Failed to fetch stats");
    } finally {
      setLoading(false);
    }
  };

  const cards = [
    {
      title: "Total Requests",
      value: stats.totalRequests || 0,
      icon: Activity,
      change: "+12.3%",
      trend: "up",
      color: "#8B5CF6",
      bgColor: "rgba(139, 92, 246, 0.1)"
    },
    {
      title: "Allowed Requests",
      value: stats.allowedRequests || 0,
      icon: CheckCircle,
      change: `${stats.allowedPercent?.toFixed(1) || 0}%`,
      trend: "up",
      color: "#10B981",
      bgColor: "rgba(16, 185, 129, 0.1)"
    },
    {
      title: "Blocked Requests",
      value: stats.blockedRequests || 0,
      icon: XCircle,
      change: `${stats.blockedPercent?.toFixed(1) || 0}%`,
      trend: "down",
      color: "#EF4444",
      bgColor: "rgba(239, 68, 68, 0.1)"
    }
  ];

  if (loading) {
    return <div className="cards-skeleton" />;
  }

  return (
    <div className="stats-grid">
      {cards.map((card, index) => {
        const Icon = card.icon;
        const formattedValue = new Intl.NumberFormat().format(card.value);

        return (
          <div 
            key={index} 
            className="stat-card"
            style={{ animationDelay: `${index * 0.1}s` }}
          >
            <div className="card-header">
              <div className="card-icon-wrapper" style={{ background: card.bgColor }}>
                <Icon size={22} color={card.color} />
              </div>
            </div>

            <div className="card-body">
              <h3 className="card-title">{card.title}</h3>
              <p className="card-value">{formattedValue}</p>
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

            <div className="card-glow" style={{ background: card.color }} />
          </div>
        );
      })}
    </div>
  );
}

export default StatsCards;
