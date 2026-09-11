import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import CompanyNav from '../components/CompanyNav';

export default function CompanyDashboard() {
  const [currentUser] = useState(localStorage.getItem('algo_arena_user'));
  const [problems, setProblems] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const canvasRef = useRef(null);

  useEffect(() => {
    const token = localStorage.getItem('algo_arena_token');
    const headers = { Authorization: `Bearer ${token}` };

    axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company/problems`, { headers })
      .then(res => setProblems(res.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('algo_arena_token');
    localStorage.removeItem('algo_arena_user');
    localStorage.removeItem('algo_arena_role');
    window.location.href = '/login';
  };

  // Particle background
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animId;
    const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; };
    resize();
    window.addEventListener('resize', resize);
    
    const particles = Array.from({ length: 40 }, () => ({
      x: Math.random() * (canvas.width || 1200),
      y: Math.random() * (canvas.height || 900),
      r: Math.random() * 2 + 1,
      dx: (Math.random() - 0.5) * 0.3,
      dy: (Math.random() - 0.5) * 0.3,
      alpha: Math.random() * 0.3 + 0.05,
    }));
    
    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (let i = 0; i < particles.length; i++) {
        const p = particles[i];
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255,122,0,${p.alpha})`;
        ctx.fill();
        p.x += p.dx;
        p.y += p.dy;
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
        if (p.y < 0 || p.y > canvas.height) p.dy *= -1;

        for (let j = i + 1; j < particles.length; j++) {
          const p2 = particles[j];
          const dist = Math.hypot(p.x - p2.x, p.y - p2.y);
          if (dist < 120) {
            ctx.beginPath();
            ctx.strokeStyle = `rgba(255, 122, 0, ${0.15 * (1 - dist / 120)})`;
            ctx.lineWidth = 1;
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
          }
        }
      }
      animId = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', resize); };
  }, []);

  if (loading) return <div style={{ minHeight: '100vh', background: '#0a0503', color: '#ff7a00', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>LOADING...</div>;

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #0a0503; margin: 0; color: #fff; font-family: 'Inter', sans-serif; }

        .comp-page {
          min-height: 100vh; position: relative; overflow-x: hidden;
        }

        .comp-canvas { position: fixed; inset: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 0; }
        .comp-bg-gradient { position: fixed; inset: 0; background: radial-gradient(ellipse at top left, rgba(255, 122, 0, 0.08) 0%, transparent 60%); z-index: 1; pointer-events: none; }
        .comp-bg-glow1 { position: absolute; top: -10%; left: -10%; width: 50%; height: 50%; background: radial-gradient(circle, rgba(255,122,0,0.12) 0%, transparent 60%); filter: blur(80px); pointer-events: none; z-index: 1; }
        .comp-bg-glow2 { position: absolute; bottom: -10%; right: -10%; width: 50%; height: 50%; background: radial-gradient(circle, rgba(255,61,0,0.08) 0%, transparent 60%); filter: blur(80px); pointer-events: none; z-index: 1; }

        .nav-container {
          position: sticky; top: 0; width: 100%; padding: 20px 40px; display: flex; justify-content: space-between; align-items: center;
          z-index: 100; backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); border-bottom: 1px solid rgba(255,255,255,0.05); background: rgba(10, 5, 3, 0.7);
        }

        .nav-logo { font-family: 'Outfit', sans-serif; font-size: 1.5rem; font-weight: 800; letter-spacing: -0.5px; cursor: pointer; display: flex; align-items: center; }
        .nav-links { display: flex; gap: 32px; font-size: 0.95rem; color: #a1a1aa; font-weight: 500; }
        .nav-links span { cursor: pointer; transition: color 0.2s; }
        .nav-links span:hover, .nav-links span.active { color: #fff; text-shadow: 0 0 12px rgba(255,255,255,0.3); }
        .nav-profile { display: flex; align-items: center; gap: 20px; }
        .btn-logout { padding: 8px 20px; border-radius: 20px; background: rgba(255,255,255,0.05); color: #fff; border: 1px solid rgba(255,255,255,0.1); font-weight: 600; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; }
        .btn-logout:hover { background: rgba(255, 61, 0, 0.15); border-color: rgba(255, 61, 0, 0.4); color: #ffb84d; }

        .content { position: relative; z-index: 10; max-width: 1200px; margin: 60px auto; padding: 0 40px; display: flex; flexDirection: column; gap: 40px; }

        .glass-panel {
          background: rgba(15, 8, 3, 0.5); backdrop-filter: blur(24px); border: 1px solid rgba(255, 122, 0, 0.2); border-radius: 28px; padding: 48px;
          box-shadow: 0 30px 60px rgba(0,0,0,0.6), inset 0 0 0 1px rgba(255,255,255,0.05);
          position: relative; overflow: hidden;
        }

        .glass-panel::before {
          content: ''; position: absolute; top: 0; left: 0; right: 0; height: 1px; background: linear-gradient(90deg, transparent, rgba(255,122,0,0.5), transparent);
        }

        .action-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 32px; margin-top: 40px; }

        .action-card {
          background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.08); border-radius: 24px; padding: 36px; cursor: pointer; transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
          position: relative; overflow: hidden;
        }

        .action-card::before {
          content: ''; position: absolute; inset: 0; background: radial-gradient(circle at top left, rgba(255,122,0,0.1), transparent 70%); opacity: 0; transition: opacity 0.4s ease; pointer-events: none;
        }

        .action-card:hover { 
          transform: translateY(-8px) scale(1.02); 
          border-color: rgba(255,122,0,0.4); 
          background: rgba(255,122,0,0.02);
          box-shadow: 0 20px 40px rgba(255,122,0,0.15), 0 0 20px rgba(255,122,0,0.1); 
        }

        .action-card:hover::before { opacity: 1; }
        
        .action-icon { font-size: 2.8rem; margin-bottom: 20px; transition: transform 0.4s ease; }
        .action-card:hover .action-icon { transform: scale(1.1) rotate(5deg); }
        .action-title { font-family: 'Outfit', sans-serif; font-size: 1.6rem; font-weight: 700; color: #fff; margin-bottom: 12px; }
        .action-desc { color: #aaa; font-size: 0.95rem; line-height: 1.6; }
      `}</style>

      <div className="comp-page">
        <canvas ref={canvasRef} className="comp-canvas" />
        <div className="comp-bg-gradient" />
        <div className="comp-bg-glow1" />
        <div className="comp-bg-glow2" />

        <CompanyNav />

        <div className="content">
          <div className="glass-panel">
            <h1 style={{ fontFamily: 'Outfit', fontSize: '3rem', fontWeight: 800, marginBottom: '12px', background: 'linear-gradient(135deg, #fff 0%, #ffb84d 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>Company Command Center</h1>
            <p style={{ color: '#888', fontSize: '1.1rem', marginBottom: '48px' }}>Manage your recruiting tournaments and private question banks.</p>

            <div style={{ display: 'flex', gap: '40px', paddingBottom: '32px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
              <div>
                <div style={{ color: '#888', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '1px', fontWeight: 700 }}>Private Problems</div>
                <div style={{ color: '#ff7a00', fontSize: '2.5rem', fontWeight: 800, fontFamily: 'Outfit' }}>{problems.length}</div>
              </div>
            </div>

            <div className="action-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
              <div className="action-card" onClick={() => navigate('/company/create-assessment')}>
                <div className="action-icon">🏆</div>
                <div className="action-title">Host Tournament</div>
                <div className="action-desc">Generate a secure tournament link and invite engineers to compete.</div>
              </div>
              <div className="action-card" onClick={() => navigate('/company/question-bank')}>
                <div className="action-icon">📚</div>
                <div className="action-title">Question Bank</div>
                <div className="action-desc">Create and manage your private coding problems.</div>
              </div>
              <div className="action-card" onClick={() => navigate('/company/selection')}>
                <div className="action-icon">📈</div>
                <div className="action-title">Selection Process</div>
                <div className="action-desc">Review candidate performance and select the best engineers.</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
