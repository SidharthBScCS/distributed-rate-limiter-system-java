import {
  Activity,
  CheckCircle,
  XCircle,
  TrendingUp,
  TrendingDown,
} from "lucide-react";
import "../Styles/Cards.css";

function formatNumber(value) {
  const numericValue = Number(value ?? 0);
  return Number.isFinite(numericValue) ? numericValue.toLocaleString() : "0";
}

function formatPercent(value) {
  const numericValue = Number(value ?? 0);
  if (!Number.isFinite(numericValue)) {
    return "0%";
  }
  if (numericValue === 100) {
    return "100%";
  }
  return `${numericValue >= 10 ? numericValue.toFixed(1) : numericValue.toFixed(2)}`
    .replace(/\.0+$/, "")
    .replace(/(\.\d*[1-9])0+$/, "$1") + "%";
}

function StatsCards({ stats, loading }) {
  const cards = (stats?.cards ?? []).map((card) => ({
    ...card,
    icon:
      card.title === "Allowed" ? CheckCircle :
      card.title === "Blocked" ? XCircle :
      Activity,
    color:
      card.title === "Allowed" ? "#4ade80" :
      card.title === "Blocked" ? "#f87171" :
      "#94a3b8",
    trend:
      card.title === "Blocked"
        ? (Number(card.percentage ?? 0) > 0 ? "down" : "up")
        : (Number(card.percentage ?? 0) > 0 ? "up" : "down"),
    changeLabel: formatPercent(card.percentage),
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
      {cards.map((card) => {
        const Icon = card.icon;
        const formattedValue = formatNumber(card.value);

        return (
          <div key={card.title} className="stat-card">
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
                {card.trend === "up" ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
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
