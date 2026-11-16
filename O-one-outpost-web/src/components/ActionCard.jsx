import React from 'react';
import './ActionCard.css'; // We'll add styles to this file or index.css

export default function ActionCard({ 
  title, 
  description, 
  icon, 
  onClick, 
  isPrimary, 
  isLocked, 
  badgeText,
  colorGradient 
}) {
  return (
    <div 
      className={`action-card ${isPrimary ? 'primary' : ''} ${isLocked ? 'locked' : ''}`}
      onClick={!isLocked ? onClick : undefined}
      style={colorGradient ? { '--card-gradient': colorGradient } : {}}
    >
      <div className="card-content">
        {badgeText && <div className="card-badge">{badgeText}</div>}
        <div className="card-icon">{icon}</div>
        <h3 className="card-title">{title}</h3>
        <p className="card-desc">{description}</p>
        
        {isLocked && (
          <div className="lock-overlay">
            <i className="bi bi-lock-fill" aria-hidden="true" />
            <span>Coming Soon</span>
          </div>
        )}
      </div>
    </div>
  );
}
