import { useNavigate, useLocation } from 'react-router-dom';

/**
 * Unified Company Portal Navbar
 * Used across all company-facing routes: dashboard, question bank, tournaments, selection
 */
export default function CompanyNav() {
  const navigate = useNavigate();
  const location = useLocation();
  const currentUser = localStorage.getItem('algo_arena_user');

  const handleLogout = () => {
    localStorage.removeItem('algo_arena_token');
    localStorage.removeItem('algo_arena_user');
    localStorage.removeItem('algo_arena_role');
    window.location.href = '/';
  };

  const links = [
    { label: 'Dashboard', path: '/company/dashboard' },
    { label: 'Question Bank', path: '/company/question-bank' },
    { label: 'Tournaments', path: '/company/create-assessment' },
    { label: 'Selection', path: '/company/selection' },
  ];

  const isActive = (path) => location.pathname === path;

  return (
    <>
      <style>{`
        .company-nav {
          position: sticky;
          top: 0;
          width: 100%;
          padding: 16px 40px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          z-index: 100;
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          border-bottom: 1px solid rgba(255, 122, 0, 0.12);
          background: rgba(10, 5, 3, 0.85);
        }
        .company-nav-left { display: flex; align-items: center; gap: 40px; }
        .company-nav-logo {
          display: flex; align-items: center; gap: 10px; cursor: pointer;
          text-decoration: none;
        }
        .company-nav-logo img { height: 38px; width: auto; object-fit: contain; flex-shrink: 0; }
        .company-nav-badge {
          color: #ffb84d; font-size: 0.72rem; font-weight: 700;
          background: rgba(255,122,0,0.15); padding: 3px 9px;
          border-radius: 6px; border: 1px solid rgba(255,122,0,0.3);
          letter-spacing: 1px; font-family: 'Inter', sans-serif;
        }
        .company-nav-links { display: flex; gap: 4px; }
        .company-nav-link {
          padding: 7px 16px; border-radius: 10px; font-size: 0.88rem;
          font-weight: 500; cursor: pointer; color: #888; border: none;
          background: none; transition: all 0.2s; font-family: 'Inter', sans-serif;
        }
        .company-nav-link:hover { color: #fff; background: rgba(255,122,0,0.08); }
        .company-nav-link.active { color: #ff7a00; background: rgba(255,122,0,0.12); border: 1px solid rgba(255,122,0,0.25); }
        .company-nav-right { display: flex; align-items: center; gap: 16px; }
        .company-nav-user {
          font-size: 0.85rem; color: #aaa;
          font-family: 'Share Tech Mono', monospace;
        }
        .company-nav-logout {
          padding: 7px 18px; border-radius: 18px; background: rgba(255,255,255,0.05);
          color: #fff; border: 1px solid rgba(255,255,255,0.1); font-weight: 600;
          font-size: 0.82rem; cursor: pointer; transition: all 0.2s; font-family: 'Inter', sans-serif;
        }
        .company-nav-logout:hover {
          background: rgba(255,122,0,0.12); border-color: rgba(255,122,0,0.35); color: #ffb84d;
        }
        @media (max-width: 768px) {
          .company-nav { padding: 14px 20px; }
          .company-nav-links { display: none; }
        }
      `}</style>

      <nav className="company-nav">
        <div className="company-nav-left">
          <div className="company-nav-logo" onClick={() => navigate('/company/dashboard')}>
            <img src="/O1.png" alt="O1 Logo" />
            <span className="company-nav-badge">ENTERPRISE</span>
          </div>
          <div className="company-nav-links">
            {links.map(link => (
              <button
                key={link.path}
                className={`company-nav-link ${isActive(link.path) ? 'active' : ''}`}
                onClick={() => navigate(link.path)}
              >
                {link.label}
              </button>
            ))}
          </div>
        </div>
        <div className="company-nav-right">
          <span className="company-nav-user">{currentUser}</span>
          <button className="company-nav-logout" onClick={handleLogout}>Log Out</button>
        </div>
      </nav>
    </>
  );
}
