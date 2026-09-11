import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { getRoleFromToken } from '../utils/auth';

export default function OverseerLogin({ setAuth }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [focusedField, setFocusedField] = useState(null);
  const navigate = useNavigate();
  const canvasRef = useRef(null);

  // Background particles with crimson red glow
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animId;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const particles = Array.from({ length: 45 }, () => ({
      x: Math.random() * (canvas.width || 1200),
      y: Math.random() * (canvas.height || 900),
      r: Math.random() * 2 + 0.8,
      dx: (Math.random() - 0.5) * 0.3,
      dy: (Math.random() - 0.5) * 0.3,
      alpha: Math.random() * 0.5 + 0.1,
    }));

    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (const p of particles) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 122, 0, ${p.alpha})`;
        ctx.fill();
        p.x += p.dx;
        p.y += p.dy;
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
        if (p.y < 0 || p.y > canvas.height) p.dy *= -1;
      }
      animId = requestAnimationFrame(draw);
    };
    draw();

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', resize);
    };
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const res = await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/login`, {
        email,
        password,
        role: 'ADMIN',
      });

      const token = res.data.token;
      const actualRole = res.data?.role || getRoleFromToken(token);

      // STRICT RULE: Only ADMIN can pass through this terminal
      if (actualRole !== 'ADMIN') {
        localStorage.removeItem('algo_arena_token');
        localStorage.removeItem('algo_arena_user');
        localStorage.removeItem('algo_arena_role');
        setError('Invalid email or credentials.');
        setLoading(false);
        return;
      }

      localStorage.setItem('algo_arena_token', token);
      localStorage.setItem('algo_arena_user', email);
      localStorage.setItem('algo_arena_role', 'ADMIN');
      setAuth(email);
      navigate('/overseer');
    } catch (err) {
      const data = err.response?.data;
      const msg =
        (typeof data === 'object' && (data?.message || data?.error)) ||
        (typeof data === 'string' ? data : null) ||
        `Authentication Failure (${err.response?.status || 'Network'})`;
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = (field) => ({
    width: '100%',
    padding: '14px 18px',
    background: 'rgba(255, 255, 255, 0.03)',
    border: focusedField === field ? '1px solid #ff7a00' : '1px solid rgba(255, 122, 0, 0.2)',
    borderRadius: '12px',
    color: '#fff',
    fontSize: '0.95rem',
    outline: 'none',
    fontFamily: "'Inter', sans-serif",
    transition: 'all 0.25s ease',
    boxShadow:
      focusedField === field
        ? '0 0 16px rgba(255, 122, 0, 0.25), 0 0 0 2px rgba(255, 122, 0, 0.15)'
        : 'none',
  });

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        .overseer-page {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 24px;
          background: #0a0503;
          font-family: 'Inter', sans-serif;
          color: #fff;
          position: relative;
          overflow: hidden;
        }
        .overseer-canvas {
          position: absolute;
          inset: 0;
          width: 100%;
          height: 100%;
          pointer-events: none;
          z-index: 0;
        }
        .overseer-card {
          position: relative;
          z-index: 10;
          width: 100%;
          max-width: 460px;
          background: rgba(10, 5, 2, 0.7);
          backdrop-filter: blur(24px);
          -webkit-backdrop-filter: blur(24px);
          border: 1px solid rgba(255, 122, 0, 0.25);
          border-radius: 24px;
          padding: 44px 36px;
          box-shadow:
            0 0 0 1px rgba(255, 255, 255, 0.05) inset,
            0 30px 60px rgba(0, 0, 0, 0.85),
            0 0 40px rgba(255, 122, 0, 0.08);
        }
        .overseer-header {
          text-align: center;
          margin-bottom: 32px;
        }
        .overseer-badge {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 4px 12px;
          border-radius: 20px;
          background: rgba(255, 122, 0, 0.12);
          border: 1px solid rgba(255, 122, 0, 0.35);
          color: #ffb84d;
          font-size: 0.75rem;
          font-weight: 700;
          letter-spacing: 1px;
          text-transform: uppercase;
          margin-bottom: 16px;
        }
        .overseer-title {
          font-family: 'Outfit', sans-serif;
          font-size: 1.8rem;
          font-weight: 800;
          letter-spacing: -0.5px;
          color: #fff;
          margin-bottom: 8px;
        }
        .overseer-sub {
          font-size: 0.9rem;
          color: #888;
          line-height: 1.5;
        }
        .overseer-btn {
          width: 100%;
          padding: 15px;
          border-radius: 12px;
          border: none;
          background: linear-gradient(135deg, #ff7a00 0%, #ff3d00 100%);
          color: #fff;
          font-size: 1rem;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.25s ease;
          box-shadow: 0 4px 20px rgba(255, 122, 0, 0.35);
          margin-top: 8px;
        }
        .overseer-btn:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: 0 8px 28px rgba(255, 122, 0, 0.5);
        }
        .overseer-btn:disabled {
          background: #1e1e1e;
          color: #666;
          cursor: not-allowed;
          box-shadow: none;
          transform: none;
        }
        .overseer-alert {
          padding: 12px 16px;
          border-radius: 10px;
          font-size: 0.88rem;
          margin-bottom: 20px;
          background: rgba(239, 68, 68, 0.12);
          border: 1px solid rgba(239, 68, 68, 0.4);
          color: #fca5a5;
          text-align: center;
          line-height: 1.4;
        }
        .overseer-abort {
          background: none;
          border: none;
          color: #666;
          font-size: 0.85rem;
          cursor: pointer;
          margin-top: 24px;
          width: 100%;
          text-align: center;
          transition: color 0.2s;
        }
        .overseer-abort:hover {
          color: #ff7a00;
        }
      `}</style>

      <div className="overseer-page">
        <canvas ref={canvasRef} className="overseer-canvas" />

        <div className="overseer-card">
          <div className="overseer-header">
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px' }}>
              <img src="/O1.png" alt="O1 Logo" style={{ height: '48px', cursor: 'pointer' }} onClick={() => navigate('/')} />
            </div>
            <div className="overseer-badge">
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#ff7a00', display: 'inline-block' }}></span>
              Classified Terminal
            </div>
            <h1 className="overseer-title">System Overseer</h1>
            <p className="overseer-sub">Administrative clearance required to access system orchestration and telemetry.</p>
          </div>

          {error && <div className="overseer-alert">{error}</div>}

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#aaa', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>
                Operator Identity
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                placeholder="operator@o-one.internal"
                autoComplete="email"
                style={inputStyle('email')}
                onFocus={() => setFocusedField('email')}
                onBlur={() => setFocusedField(null)}
              />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#aaa', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>
                Master Authorization Key
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                placeholder="••••••••••••••••"
                autoComplete="current-password"
                style={inputStyle('password')}
                onFocus={() => setFocusedField('password')}
                onBlur={() => setFocusedField(null)}
              />
            </div>

            <button type="submit" className="overseer-btn" disabled={loading}>
              {loading ? 'Authenticating Clearance...' : 'Authorize Access →'}
            </button>
          </form>

          <button type="button" className="overseer-abort" onClick={() => navigate('/')}>
            ← Abort & Return to Outpost
          </button>
        </div>
      </div>
    </>
  );
}
