import { useNavigate, useLocation } from 'react-router-dom';

/**
 * Unified Engineer Portal Navbar
 * Used across all engineer-facing routes: dashboard, lobby, tournaments, candidate pages
 */
export default function EngineerNav({ currentUser: propUser }) {
  const navigate = useNavigate();
  const location = useLocation();
  const currentUser = propUser || localStorage.getItem('algo_arena_user');

  const handleLogout = () => {
    localStorage.removeItem('algo_arena_token');
    localStorage.removeItem('algo_arena_user');
    localStorage.removeItem('algo_arena_role');
    window.location.href = '/';
  };

  const links = [
    { label: 'Lobby', path: '/lobby' },
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Tournaments', path: '/candidate/directory' },
    { label: 'My Progress', path: '/candidate/dashboard' },
  ];

  const isActive = (path) => location.pathname === path;

  return (
    <>
      <style>{`
        .engineer-nav {
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
          border-bottom: 1px solid rgba(255, 122, 0, 0.1);
          background: rgba(10, 5, 3, 0.85);
        }
        .engineer-nav-left { display: flex; align-items: center; gap: 40px; }
        .engineer-nav-logo {
          display: flex; align-items: center; gap: 10px; cursor: pointer;
        }
        .engineer-nav-logo img { height: 38px; width: auto; object-fit: contain; flex-shrink: 0; }
        .engineer-nav-badge {
          color: #ff7a00; font-size: 0.72rem; font-weight: 700;
          background: rgba(255,122,0,0.12); padding: 3px 9px;
          border-radius: 6px; border: 1px solid rgba(255,122,0,0.25);
          letter-spacing: 1px; font-family: 'Inter', sans-serif;
        }
        .engineer-nav-links { display: flex; gap: 4px; }
        .engineer-nav-link {
          padding: 7px 16px; border-radius: 10px; font-size: 0.88rem;
          font-weight: 500; cursor: pointer; color: #888; border: none;
          background: none; transition: all 0.2s; font-family: 'Inter', sans-serif;
        }
        .engineer-nav-link:hover { color: #fff; background: rgba(255,122,0,0.08); }
        .engineer-nav-link.active { color: #ff7a00; background: rgba(255,122,0,0.12); border: 1px solid rgba(255,122,0,0.25); }
        .engineer-nav-right { display: flex; align-items: center; gap: 16px; }
        .engineer-nav-status { display: flex; align-items: center; gap: 8px; }
        .engineer-nav-dot { width: 8px; height: 8px; border-radius: 50%; background: #27c93f; box-shadow: 0 0 8px #27c93f; flex-shrink: 0; }
        .engineer-nav-user { font-size: 0.85rem; color: #aaa; font-family: 'Share Tech Mono', monospace; }
        .engineer-nav-logout {
          padding: 7px 18px; border-radius: 18px; background: rgba(255,255,255,0.05);
          color: #fff; border: 1px solid rgba(255,255,255,0.1); font-weight: 600;
          font-size: 0.82rem; cursor: pointer; transition: all 0.2s; font-family: 'Inter', sans-serif;
        }
        .engineer-nav-logout:hover {
          background: rgba(255,122,0,0.12); border-color: rgba(255,122,0,0.35); color: #ffb84d;
        }
        @media (max-width: 768px) {
          .engineer-nav { padding: 14px 20px; }
          .engineer-nav-links { display: none; }
        }
      `}</style>

      <nav className="engineer-nav">
        <div className="engineer-nav-left">
          <div className="engineer-nav-logo" onClick={() => navigate('/')}>
            <img src="/O1.png" alt="O1 Logo" />
            <span className="engineer-nav-badge">ENGINEER</span>
          </div>
          <div className="engineer-nav-links">
            {links.map(link => (
              <button
                key={link.path}
                className={`engineer-nav-link ${isActive(link.path) ? 'active' : ''}`}
                onClick={() => navigate(link.path)}
              >
                {link.label}
              </button>
            ))}
          </div>
        </div>
        <div className="engineer-nav-right">
          <div className="engineer-nav-status">
            <div className="engineer-nav-dot" />
            <span className="engineer-nav-user">{currentUser}</span>
          </div>
          <button className="engineer-nav-logout" onClick={handleLogout}>Log Out</button>
        </div>
      </nav>
    </>
  );
}
