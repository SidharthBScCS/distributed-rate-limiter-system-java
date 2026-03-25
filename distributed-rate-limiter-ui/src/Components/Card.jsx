import { 
  Activity, 
  CheckCircle, 
  XCircle,
  TrendingUp,
  TrendingDown
} from "lucide-react";
import "../Cards.css";

function StatsCards({ stats, loading }) {
  const cards = (stats?.cards ?? []).map((card) => ({
    ...card,
    icon:
      card.title === "Total Requests"
        ? Activity
        : card.title === "Allowed"
          ? CheckCircle
          : XCircle,
  }));

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
        const formattedValue = card.valueLabel ?? "-";

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
                <span>{card.changeLabel}</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default StatsCards;
