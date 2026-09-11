import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import ActionCard from '../components/ActionCard';
import EngineerNav from '../components/EngineerNav';

export default function Dashboard({ currentUser, getToken }) {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const canvasRef = useRef(null);

  useEffect(() => {
    const headers = { Authorization: `Bearer ${getToken()}` };
    // Fetch stats, we'll skip leaderboard here to save space as it's on the right panel originally
    axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/profiles/${currentUser}`, { headers })
      .then(s => setStats(s.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [currentUser, getToken]);

  const handleLogout = () => {
    localStorage.removeItem('algo_arena_token');
    localStorage.removeItem('algo_arena_user');
    localStorage.removeItem('algo_arena_role');
    window.location.href = '/login';
  };

  // Particle background canvas (subtle orange particles)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animId;
    const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; };
    resize();
    window.addEventListener('resize', resize);
    
    const particles = Array.from({ length: 50 }, () => ({
      x: Math.random() * (canvas.width || 1200),
      y: Math.random() * (canvas.height || 900),
      r: Math.random() * 1.5 + 0.5,
      dx: (Math.random() - 0.5) * 0.4,
      dy: (Math.random() - 0.5) * 0.4,
      alpha: Math.random() * 0.3 + 0.05,
    }));
    
    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (const p of particles) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255,122,0,${p.alpha})`;
        ctx.fill();
        p.x += p.dx; p.y += p.dy;
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
        if (p.y < 0 || p.y > canvas.height) p.dy *= -1;
      }
      animId = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', resize); };
  }, []);

  if (loading) return (
    <div style={{ minHeight: '100vh', background: '#0a0503', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ff7a00', fontFamily: "'Share Tech Mono', monospace", letterSpacing: '4px', fontSize: '1.2rem' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
        <div style={{ width: '50px', height: '50px', border: '3px solid rgba(255,122,0,0.2)', borderTopColor: '#ff7a00', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
        SYNCING COMMAND CENTER...
      </div>
      <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
    </div>
  );

  const username = currentUser.split('@')[0];

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        
        body { background: #0a0503; margin: 0; }

        .dashboard-page {
          min-height: 100vh;
          background: #0a0503;
          font-family: 'Inter', sans-serif;
          color: #fff;
          position: relative;
          overflow-x: hidden;
        }

        .dashboard-canvas {
          position: fixed;
          inset: 0;
          width: 100vw;
          height: 100vh;
          pointer-events: none;
          z-index: 0;
        }

        .dashboard-bg-gradient {
          position: fixed;
          inset: 0;
          background: radial-gradient(ellipse at center, rgba(255, 122, 0, 0.05) 0%, transparent 70%);
          z-index: 1;
          pointer-events: none;
        }

        /* Top Navigation */
        .nav-container {
          position: sticky;
          top: 0;
          width: 100%;
          padding: 20px 40px;
          display: flex;
          justify-content: space-between;
          align-items: center;
          z-index: 100;
          backdrop-filter: blur(10px);
          -webkit-backdrop-filter: blur(10px);
          border-bottom: 1px solid rgba(255,255,255,0.05);
          background: rgba(10, 5, 3, 0.8);
        }

        .nav-logo {
          font-family: 'Outfit', sans-serif;
          font-size: 1.5rem;
          font-weight: 800;
          letter-spacing: -0.5px;
          cursor: pointer;
        }

        .nav-links {
          display: flex;
          gap: 32px;
          font-size: 0.95rem;
          color: #a1a1aa;
          font-weight: 500;
        }
        .nav-links span { cursor: pointer; transition: color 0.2s; }
        .nav-links span:hover, .nav-links span.active { color: #fff; }

        .nav-profile { display: flex; align-items: center; gap: 20px; }

        .btn-logout {
          padding: 8px 20px; border-radius: 20px; background: rgba(255,255,255,0.05); color: #fff;
          border: 1px solid rgba(255,255,255,0.1); font-weight: 600; font-size: 0.85rem;
          cursor: pointer; transition: all 0.2s;
        }
        .btn-logout:hover {
          background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.4); color: #fca5a5;
        }

        /* Main Content Grid */
        .dashboard-content {
          position: relative;
          z-index: 10;
          max-width: 1400px;
          margin: 40px auto;
          padding: 0 40px;
          display: grid;
          grid-template-columns: 320px 1fr;
          gap: 40px;
          align-items: start;
        }

        /* Glass Panels */
        .glass-panel {
          background: rgba(15, 8, 3, 0.4);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          border: 1px solid rgba(255, 122, 0, 0.1);
          border-radius: 20px;
          padding: 32px;
          box-shadow: 0 20px 40px rgba(0,0,0,0.4);
        }

        .hero-panel {
          background: linear-gradient(135deg, rgba(255,122,0,0.1), rgba(15,8,3,0.6));
          border: 1px solid rgba(255,122,0,0.3);
          box-shadow: 0 0 40px rgba(255,122,0,0.08), inset 0 0 20px rgba(255,122,0,0.05);
          text-align: center;
          padding: 40px 24px;
        }

        /* Actions Grid */
        .actions-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 24px;
        }
        
        /* Full width span for certain cards */
        .span-2 {
          grid-column: span 2;
        }

        @media (max-width: 1100px) {
          .dashboard-content { grid-template-columns: 1fr; }
        }
        @media (max-width: 768px) {
          .actions-grid { grid-template-columns: 1fr; }
          .span-2 { grid-column: span 1; }
        }
      `}</style>

      <div className="dashboard-page">
        <canvas ref={canvasRef} className="dashboard-canvas" />
        <div className="dashboard-bg-gradient" />

        {/* Top Navbar */}
        <EngineerNav currentUser={currentUser} />

        {/* Main Content */}
        <div className="dashboard-content">
          
          {/* Left Sidebar: Profile Summary */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <div className="glass-panel hero-panel">
              <div style={{ display: 'inline-block', padding: '6px 16px', border: '1px solid rgba(255,122,0,0.3)', borderRadius: '20px', color: '#ff7a00', fontSize: '0.7rem', letterSpacing: '4px', fontFamily: 'Share Tech Mono', marginBottom: '24px', background: 'rgba(255,122,0,0.05)' }}>
                COMMAND CENTER
              </div>
              <h1 style={{ fontSize: '2.2rem', fontWeight: 800, fontFamily: 'Outfit', marginBottom: '8px' }}>
                {username}
              </h1>
              <div style={{ color: '#888', fontSize: '0.9rem', marginBottom: '32px' }}>Competitive Engineer</div>
              
              <div style={{ color: '#ff7a00', fontSize: '0.8rem', letterSpacing: '3px', fontWeight: 700, marginBottom: '8px' }}>CURRENT ELO</div>
              <div style={{ fontSize: '3.5rem', fontWeight: 800, fontFamily: 'Share Tech Mono', color: '#fff', textShadow: '0 0 20px rgba(255,122,0,0.3)', lineHeight: 1 }}>
                {stats?.currentElo || 1200}
              </div>
            </div>

            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontFamily: 'Outfit', fontSize: '1.2rem', marginBottom: '20px', color: '#fff' }}>Quick Stats</h3>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', paddingBottom: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: '#888' }}>Matches</span>
                <span style={{ fontWeight: 'bold' }}>{stats?.matchesPlayed || 0}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '16px', paddingBottom: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: '#888' }}>Win Rate</span>
                <span style={{ fontWeight: 'bold', color: '#27c93f' }}>
                  {stats?.matchesPlayed > 0 ? Math.round((stats.wins / stats.matchesPlayed) * 100) : 0}%
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#888' }}>Win Streak</span>
                <span style={{ fontWeight: 'bold', color: '#ff7a00' }}>{stats?.currentWinStreak || 0} W</span>
              </div>
            </div>
          </div>

          {/* Right Section: Action Grid */}
          <div>
            <h2 style={{ fontFamily: 'Outfit', fontSize: '2rem', fontWeight: 800, marginBottom: '24px', letterSpacing: '-0.5px' }}>
              Welcome back, <span style={{ color: '#ff7a00' }}>{username}</span>
            </h2>
            
            <div className="actions-grid">
              
              {/* Primary Action */}
              <div className="span-2">
                <ActionCard 
                  title="Enter the Arena"
                  description="Queue up for a 1v1 algorithmic battle against a live opponent of similar rank. Climb the global leaderboard."
                  icon="⚔️"
                  isPrimary={true}
                  onClick={() => navigate('/lobby')}
                  colorGradient="linear-gradient(135deg, rgba(255,122,0,0.2), transparent)"
                  badgeText="RANKED MATCH"
                />
              </div>

              {/* Secondary Actions */}
              <ActionCard 
                title="Daily Challenge"
                description="Solve today's hand-picked algorithm puzzle to keep your daily streak alive and earn extra ELO."
                icon="🎯"
                onClick={() => {}}
                isLocked={true}
                colorGradient="linear-gradient(135deg, rgba(39,201,63,0.1), transparent)"
              />

              <ActionCard 
                title="Learn & Practice"
                description="Master new algorithms through curated roadmaps: Dynamic Programming, Graph Theory, and more."
                icon="📚"
                onClick={() => {}}
                isLocked={true}
                colorGradient="linear-gradient(135deg, rgba(168,85,247,0.1), transparent)"
              />

              <ActionCard 
                title="AI Bug Hunter"
                description="Face off against AI-generated logical bugs in real-world code scenarios. Improve your debugging speed."
                icon="🤖"
                onClick={() => {}}
                isLocked={true}
                colorGradient="linear-gradient(135deg, rgba(59,130,246,0.1), transparent)"
              />

              <ActionCard 
                title="Spectate"
                description="Watch high-ELO Grandmasters code live. Learn strategies and techniques from the best."
                icon="👁️"
                onClick={() => {}}
                isLocked={true}
                colorGradient="linear-gradient(135deg, rgba(236,72,153,0.1), transparent)"
              />

            </div>
          </div>

        </div>
      </div>
    </>
  );
}