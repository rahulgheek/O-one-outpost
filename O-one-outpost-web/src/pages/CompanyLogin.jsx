import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { getRoleFromToken } from '../utils/auth';

export default function CompanyLogin({ setAuth, initialMode = 'login' }) {
  const [isLogin, setIsLogin] = useState(initialMode === 'login');
  const [isOtpMode, setIsOtpMode] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otpArray, setOtpArray] = useState(['', '', '', '', '', '']);
  const otpRefs = useRef([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [focusedField, setFocusedField] = useState(null);
  const navigate = useNavigate();
  const canvasRef = useRef(null);

  useEffect(() => {
    setIsLogin(initialMode === 'login');
    setIsOtpMode(false);
    setError('');
    setSuccess('');
  }, [initialMode]);

  useEffect(() => {
    setError('');
    setSuccess('');
    setOtpArray(['', '', '', '', '', '']);
    if (isLogin) setIsOtpMode(false);
  }, [isLogin]);

  const handleOtpChange = (e, index) => {
    const val = e.target.value.replace(/\D/g, '');
    if (!val) return;
    const newOtp = [...otpArray];
    newOtp[index] = val.slice(-1);
    setOtpArray(newOtp);
    if (index < 5 && otpRefs.current[index + 1]) otpRefs.current[index + 1].focus();
  };

  const handleOtpKeyDown = (e, index) => {
    if (e.key === 'Backspace') {
      if (!otpArray[index] && index > 0) {
        if (otpRefs.current[index - 1]) otpRefs.current[index - 1].focus();
      } else {
        const newOtp = [...otpArray];
        newOtp[index] = '';
        setOtpArray(newOtp);
      }
    }
  };

  // Subtle corporate indigo particles
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

    const particles = Array.from({ length: 40 }, () => ({
      x: Math.random() * (canvas.width || 1200),
      y: Math.random() * (canvas.height || 900),
      r: Math.random() * 1.8 + 0.6,
      dx: (Math.random() - 0.5) * 0.25,
      dy: (Math.random() - 0.5) * 0.25,
      alpha: Math.random() * 0.4 + 0.08,
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
    setSuccess('');

    try {
      if (isLogin) {
        const res = await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/login`, {
          email,
          password,
          role: 'COMPANY',
        });

        const token = res.data.token;
        const actualRole = res.data?.role || getRoleFromToken(token);

        // STRICT RULE: Only COMPANY accounts can log in through the Company portal
        if (actualRole !== 'COMPANY') {
          localStorage.removeItem('algo_arena_token');
          localStorage.removeItem('algo_arena_user');
          localStorage.removeItem('algo_arena_role');

          setError('Invalid email or credentials.');
          setLoading(false);
          return;
        }

        localStorage.setItem('algo_arena_token', token);
        localStorage.setItem('algo_arena_user', email);
        localStorage.setItem('algo_arena_role', 'COMPANY');
        setAuth(email);
        navigate('/company/dashboard');
      } else if (isOtpMode) {
        const otp = otpArray.join('');
        await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/verify`, { email, otp });
        setSuccess('Company account verified! Redirecting to login...');
        setTimeout(() => {
          navigate('/company/login');
          setIsLogin(true);
        }, 1500);
      } else {
        await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/register`, {
          email,
          password,
          role: 'COMPANY',
        });
        setSuccess('Verification code sent to your company email.');
        setIsOtpMode(true);
      }
    } catch (err) {
      const data = err.response?.data;
      const msg =
        (typeof data === 'object' && (data?.message || data?.error)) ||
        (typeof data === 'string' ? data : null) ||
        `Operation Error (${err.response?.status || 'Network'})`;
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = (field) => ({
    width: '100%',
    padding: '14px 18px',
    background: 'rgba(255, 255, 255, 0.04)',
    border: focusedField === field ? '1px solid #ff7a00' : '1px solid rgba(255, 255, 255, 0.1)',
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
        .company-auth-page {
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
        .company-auth-canvas {
          position: absolute;
          inset: 0;
          width: 100%;
          height: 100%;
          pointer-events: none;
          z-index: 0;
        }
        .company-auth-card {
          position: relative;
          z-index: 10;
          width: 100%;
          max-width: 480px;
          background: rgba(15, 8, 3, 0.7);
          backdrop-filter: blur(24px);
          -webkit-backdrop-filter: blur(24px);
          border: 1px solid rgba(255, 122, 0, 0.25);
          border-radius: 24px;
          padding: 44px 38px;
          box-shadow:
            0 0 0 1px rgba(255, 255, 255, 0.05) inset,
            0 30px 60px rgba(0, 0, 0, 0.8),
            0 0 40px rgba(255, 122, 0, 0.08);
        }
        .company-badge {
          display: inline-flex;
          align-items: center;
          gap: 6px;
          padding: 4px 12px;
          border-radius: 20px;
          background: rgba(255, 122, 0, 0.15);
          border: 1px solid rgba(255, 122, 0, 0.35);
          color: #ffb84d;
          font-size: 0.75rem;
          font-weight: 700;
          letter-spacing: 1px;
          text-transform: uppercase;
          margin-bottom: 16px;
        }
        .company-btn {
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
        .company-btn:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: 0 8px 28px rgba(255, 122, 0, 0.5);
        }
        .company-btn:disabled {
          background: #1e1e1e;
          color: #666;
          cursor: not-allowed;
          box-shadow: none;
          transform: none;
        }
        .company-alert {
          padding: 12px 16px;
          border-radius: 10px;
          font-size: 0.88rem;
          margin-bottom: 20px;
          text-align: center;
          line-height: 1.4;
        }
        .company-alert.error {
          background: rgba(239, 68, 68, 0.12);
          border: 1px solid rgba(239, 68, 68, 0.4);
          color: #fca5a5;
        }
        .company-alert.success {
          background: rgba(34, 197, 94, 0.12);
          border: 1px solid rgba(34, 197, 94, 0.4);
          color: #86efac;
        }
      `}</style>

      <div className="company-auth-page">
        <canvas ref={canvasRef} className="company-auth-canvas" />

        <div className="company-auth-card">
          <div style={{ textAlign: 'center', marginBottom: '28px' }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px' }}>
              <img src="/O1.png" alt="O1 Logo" style={{ height: '48px', cursor: 'pointer' }} onClick={() => navigate('/')} />
            </div>
            <div className="company-badge">
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#ff7a00', display: 'inline-block' }}></span>
              Enterprise Portal
            </div>
            <h1 style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.8rem', fontWeight: 800, color: '#fff', marginBottom: '8px' }}>
              {isLogin ? 'Recruiter Command' : isOtpMode ? 'Email Verification' : 'Register Enterprise'}
            </h1>
            <p style={{ fontSize: '0.9rem', color: '#94a3b8' }}>
              {isLogin ? 'Sign in to access assessments, candidate pipelines, and question banks.' :
               isOtpMode ? 'Enter the verification code sent to your company inbox.' :
               'Create an enterprise hiring workspace for your company.'}
            </p>
          </div>

          {error && <div className="company-alert error">{error}</div>}
          {success && <div className="company-alert success">{success}</div>}

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {!isOtpMode && (
              <>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>
                    Corporate Email
                  </label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    placeholder="recruiter@enterprise.com"
                    autoComplete="email"
                    style={inputStyle('email')}
                    onFocus={() => setFocusedField('email')}
                    onBlur={() => setFocusedField(null)}
                  />
                </div>

                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>
                    Password
                  </label>
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    placeholder="••••••••••••••••"
                    autoComplete={isLogin ? 'current-password' : 'new-password'}
                    style={inputStyle('password')}
                    onFocus={() => setFocusedField('password')}
                    onBlur={() => setFocusedField(null)}
                  />
                </div>
              </>
            )}

            {isOtpMode && (
              <div>
                <label style={{ display: 'block', textAlign: 'center', fontSize: '0.8rem', fontWeight: 600, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '12px' }}>
                  6-Digit Verification Code
                </label>
                <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', marginBottom: '12px' }}>
                  {otpArray.map((digit, index) => (
                    <input
                      key={index}
                      ref={(el) => (otpRefs.current[index] = el)}
                      type="text"
                      inputMode="numeric"
                      maxLength={1}
                      value={digit}
                      onChange={(e) => handleOtpChange(e, index)}
                      onKeyDown={(e) => handleOtpKeyDown(e, index)}
                      style={{
                        width: '52px',
                        height: '60px',
                        textAlign: 'center',
                        fontSize: '1.6rem',
                        fontWeight: 700,
                        fontFamily: "'Share Tech Mono', monospace",
                        background: 'rgba(255, 255, 255, 0.04)',
                        border: '1px solid rgba(255, 122, 0, 0.3)',
                        borderRadius: '12px',
                        color: '#fff',
                        outline: 'none',
                      }}
                    />
                  ))}
                </div>
                <button
                  type="button"
                  onClick={() => setIsOtpMode(false)}
                  style={{ background: 'none', border: 'none', color: '#ff7a00', cursor: 'pointer', fontSize: '0.85rem', width: '100%', textAlign: 'center' }}
                >
                  ← Back to Company Registration
                </button>
              </div>
            )}

            <button type="submit" className="company-btn" disabled={loading}>
              {loading ? 'Processing...' : isLogin ? 'Enter Enterprise Suite →' : isOtpMode ? 'Verify Company Account' : 'Register Workspace →'}
            </button>
          </form>

          {!isOtpMode && (
            <div style={{ textAlign: 'center', marginTop: '24px', fontSize: '0.88rem', color: '#94a3b8' }}>
              {isLogin ? 'Need an enterprise workspace? ' : 'Already have a corporate account? '}
              <button
                type="button"
                onClick={() => {
                  if (isLogin) navigate('/company/signup');
                  else navigate('/company/login');
                }}
                style={{ background: 'none', border: 'none', color: '#ff7a00', cursor: 'pointer', fontWeight: 600, fontSize: '0.88rem' }}
              >
                {isLogin ? 'Register Company' : 'Log In'}
              </button>
            </div>
          )}

          <div style={{ textAlign: 'center', marginTop: '16px' }}>
            <button
              type="button"
              onClick={() => navigate('/')}
              style={{ background: 'none', border: 'none', color: '#64748b', fontSize: '0.82rem', cursor: 'pointer' }}
            >
              ← Back to Outpost Homepage
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
