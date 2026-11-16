import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { getRoleFromToken } from '../utils/auth';

const CODE_LINES = [
  'function solve(graph, src) {',
  '  const dist = new Map();',
  '  const pq = new MinHeap();',
  '  dist.set(src, 0);',
  '  pq.push([0, src]);',
  '  while (!pq.isEmpty()) {',
  '    const [d, u] = pq.pop();',
  '    if (d > dist.get(u)) continue;',
  '    for (const [v, w] of graph[u]) {',
  '      if (dist.get(u) + w < dist.get(v)) {',
  '        dist.set(v, dist.get(u) + w);',
  '        pq.push([dist.get(v), v]);',
  '      }',
  '    }',
  '  }',
  '  return dist;',
  '}',
  '',
  'class ArenaMatch {',
  '  constructor(p1, p2, problem) {',
  '    this.players = [p1, p2];',
  '    this.startTime = Date.now();',
  '  }',
  '  submit(player, code) {',
  '    const result = sandbox.run(code);',
  '    if (result.passed === result.total) {',
  '      this.winner = player;',
  '      this.end();',
  '    }',
  '    return result;',
  '  }',
  '}',
  '',
  '// [SYS] MATCHMAKER: "alex_dev" (Elo: 1840)',
  '// [SYS] MATCHMAKER: "sarah_codes" (Elo: 1855)',
  '// [SYS] LOBBY: Match found! Establishing...',
  '',
  'function longestSubarray(nums, k) {',
  '  let left = 0, maxLen = 0, sum = 0;',
  '  for (let right = 0; right < nums.length; right++) {',
  '    sum += nums[right];',
  '    while (sum > k) sum -= nums[left++];',
  '    maxLen = Math.max(maxLen, right - left + 1);',
  '  }',
  '  return maxLen;',
  '}',
  '',
  '// ARENA INITIALIZED: Hard Difficulty',
  '// Problem: Dynamic Programming Traversal',
  '// Time Limit: 15 Minutes | Sandbox: Secure ✓',
];

export default function Auth({ setAuth, initialMode = 'login' }) {
  const [isLogin, setIsLogin] = useState(initialMode === 'login');
  const [isOtpMode, setIsOtpMode] = useState(false);
  const navigate = useNavigate();
  const canvasRef = useRef(null);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otpArray, setOtpArray] = useState(['', '', '', '', '', '']);
  const otpRefs = useRef([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [focusedField, setFocusedField] = useState(null);

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

  const handleOtpPaste = (e) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    const newOtp = [...otpArray];
    for (let i = 0; i < text.length; i++) newOtp[i] = text[i];
    setOtpArray(newOtp);
    if (text.length > 0 && otpRefs.current[Math.min(text.length, 5)]) {
      otpRefs.current[Math.min(text.length, 5)].focus();
    }
  };

  // Particle canvas (full screen behind everything)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animId;
    const resize = () => { canvas.width = canvas.offsetWidth; canvas.height = canvas.offsetHeight; };
    resize();
    window.addEventListener('resize', resize);
    const particles = Array.from({ length: 60 }, () => ({
      x: Math.random() * (canvas.width || 1200),
      y: Math.random() * (canvas.height || 900),
      r: Math.random() * 1.8 + 0.3,
      dx: (Math.random() - 0.5) * 0.25,
      dy: -Math.random() * 0.35 - 0.05,
      alpha: Math.random() * 0.45 + 0.08,
    }));
    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (const p of particles) {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255,122,0,${p.alpha})`;
        ctx.fill();
        p.x += p.dx; p.y += p.dy;
        if (p.y < -5) { p.y = canvas.height + 5; p.x = Math.random() * canvas.width; }
        if (p.x < 0 || p.x > canvas.width) p.dx *= -1;
      }
      animId = requestAnimationFrame(draw);
    };
    draw();
    return () => { cancelAnimationFrame(animId); window.removeEventListener('resize', resize); };
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true); setError(''); setSuccess('');
    try {
      if (isLogin) {
        const res = await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/login`, { email, password, role: 'USER' });
        const token = res.data.token;
        const actualRole = res.data?.role || getRoleFromToken(token);
        
        // STRICT RULE: Only USER accounts can log in through the Engineer portal
        if (actualRole !== 'USER') {
          localStorage.removeItem('algo_arena_token');
          localStorage.removeItem('algo_arena_user');
          localStorage.removeItem('algo_arena_role');
          
          setError('Invalid email or credentials.');
          setLoading(false);
          return;
        }

        localStorage.setItem('algo_arena_token', token);
        localStorage.setItem('algo_arena_user', email);
        localStorage.setItem('algo_arena_role', 'USER');
        setAuth(email);
        navigate('/lobby');
      } else if (isOtpMode) {
        const fullOtp = otpArray.join('');
        await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/verify`, { email, otp: fullOtp });
        setSuccess('Engineer account verified! Redirecting to login...');
        setTimeout(() => { navigate('/login'); setIsLogin(true); }, 1500);
      } else {
        await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/register`, { email, password, role: 'USER' });
        setSuccess('Verification email sent — check your inbox.');
        setIsOtpMode(true);
      }
    } catch (err) {
      const data = err.response?.data;
      const msg = (typeof data === 'object' && (data?.message || data?.error))
        || (typeof data === 'string' ? data : null)
        || `Error (${err.response?.status || 'Network'})`;
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const currentMode = isLogin ? 'login' : isOtpMode ? 'otp' : 'register';

  const inputStyle = (field) => ({
    width: '100%',
    padding: '14px 18px',
    background: 'rgba(255,255,255,0.04)',
    border: focusedField === field ? '1px solid #ff7a00' : '1px solid rgba(255,255,255,0.1)',
    borderRadius: '14px',
    color: '#fff',
    fontSize: '0.95rem',
    outline: 'none',
    transition: 'all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)',
    fontFamily: "'Inter', sans-serif",
    transform: focusedField === field ? 'translateY(-3px)' : 'translateY(0)',
    boxShadow: focusedField === field
      ? '0 8px 24px rgba(255,122,0,0.18), 0 0 0 3px rgba(255,122,0,0.12)'
      : '0 2px 8px rgba(0,0,0,0.2)',
  });

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }

        .auth-page {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 20px;
          background: #0a0503;
          font-family: 'Inter', sans-serif;
          color: #fff;
          position: relative;
          overflow: hidden;
        }

        .auth-canvas {
          position: absolute;
          inset: 0;
          width: 100%;
          height: 100%;
          pointer-events: none;
          z-index: 0;
        }

        /* Center Wrapper Container */
        .auth-card-wrapper {
          position: relative;
          z-index: 10;
          display: flex;
          width: 100%;
          max-width: 1050px;
          min-height: 550px;
          background: rgba(15, 8, 3, 0.4);
          border: 1px solid rgba(255, 122, 0, 0.22);
          border-radius: 28px;
          box-shadow:
            0 0 0 1px rgba(255,255,255,0.04) inset,
            0 40px 80px rgba(0,0,0,0.7),
            0 0 60px rgba(255,122,0,0.06);
          overflow: hidden; /* Important so inner children don't spill over corners */
        }

        /* ── Left side: Welcome Text + CODE BACKGROUND ── */
        .auth-card-left {
          flex: 1;
          display: flex;
          flex-direction: column;
          justify-content: center;
          position: relative;
          padding: 40px 50px;
          overflow: hidden;
          background: radial-gradient(circle at top left, rgba(255,122,0,0.08) 0%, transparent 60%);
        }

        /* Scrolling code background INSIDE the left card */
        .code-waterfall {
          position: absolute;
          inset: 0;
          display: flex;
          gap: 28px;
          padding: 40px;
          pointer-events: none;
          z-index: 1;
        }

        .code-col {
          display: flex;
          flex-direction: column;
          animation: codeScroll linear infinite;
          flex-shrink: 0;
        }

        .code-ln {
          font-family: 'Share Tech Mono', monospace;
          font-size: 13px;
          line-height: 1.75;
          white-space: nowrap;
          color: rgba(255, 122, 0, 0.4);
        }
        .code-ln.hl { color: rgba(255, 122, 0, 0.85); text-shadow: 0 0 8px rgba(255,122,0,0.5); }

        @keyframes codeScroll {
          from { transform: translateY(0); }
          to   { transform: translateY(-50%); }
        }

        /* Gradient overlay to make text readable over code */
        .code-overlay {
          position: absolute;
          inset: 0;
          z-index: 2;
          background:
            linear-gradient(to right, rgba(10,5,3,0.8) 0%, rgba(10,5,3,0.2) 30%, rgba(10,5,3,0.2) 70%, rgba(10,5,3,0.8) 100%),
            linear-gradient(to bottom, rgba(10,5,3,0.8) 0%, rgba(10,5,3,0) 20%, rgba(10,5,3,0) 80%, rgba(10,5,3,0.8) 100%);
          pointer-events: none;
        }

        .left-content {
          position: relative;
          z-index: 10; /* above the code */
        }

        /* ── Right side: Login Form (Glassmorphism) ── */
        .auth-card-right {
          width: 480px;
          padding: 40px 48px;
          display: flex;
          flex-direction: column;
          justify-content: center;
          background: rgba(10, 5, 2, 0.6);
          backdrop-filter: blur(28px);
          -webkit-backdrop-filter: blur(28px);
          border-left: 1px solid rgba(255, 122, 0, 0.15);
          position: relative;
        }

        /* Logo */
        .auth-logo {
          font-family: 'Outfit', sans-serif;
          font-size: 1.6rem;
          font-weight: 800;
          letter-spacing: -0.5px;
          cursor: pointer;
          color: #fff;
          margin-bottom: 40px;
          display: inline-block;
        }

        /* Hero text block */
        .auth-hero {
          margin-bottom: 40px;
        }

        .auth-hero-title {
          font-family: 'Outfit', sans-serif;
          font-size: 3.5rem;
          font-weight: 800;
          line-height: 1.1;
          letter-spacing: -1px;
          color: #fff;
          margin-bottom: 20px;
        }

        .auth-hero-sub {
          font-size: 1.1rem;
          color: #a1a1aa;
          line-height: 1.6;
          max-width: 400px;
        }

        /* Stats row */
        .auth-stats {
          display: flex;
          gap: 32px;
          margin-top: 40px;
        }
        .stat-item {
          display: flex;
          flex-direction: column;
          align-items: flex-start;
          gap: 4px;
        }
        .stat-val {
          font-family: 'Outfit', sans-serif;
          font-size: 1.6rem;
          font-weight: 700;
          color: #ff7a00;
        }
        .stat-lbl {
          font-size: 0.8rem;
          color: #666;
          text-transform: uppercase;
          letter-spacing: 0.8px;
          font-weight: 600;
        }

        /* Remove toggle styles */

        /* Role */
        .auth-roles {
          display: flex;
          gap: 10px;
          margin-bottom: 20px;
        }
        .auth-role-btn {
          flex: 1;
          padding: 12px;
          border-radius: 12px;
          border: 1px solid rgba(255,255,255,0.08);
          background: rgba(255,255,255,0.03);
          color: #777;
          cursor: pointer;
          transition: all 0.22s ease;
          display: flex; align-items: center; justify-content: center; gap: 8px;
          font-weight: 600; font-size: 0.9rem;
          font-family: 'Inter', sans-serif;
        }
        .auth-role-btn:hover { border-color: rgba(255,122,0,0.3); color: #ccc; }
        .auth-role-btn.active {
          border-color: #ff7a00;
          color: #ff7a00;
          background: rgba(255,122,0,0.1);
          box-shadow: 0 0 20px rgba(255,122,0,0.12);
        }

        /* Label */
        .auth-label {
          display: block;
          color: #888;
          font-size: 0.8rem;
          font-weight: 600;
          margin-bottom: 8px;
          letter-spacing: 0.6px;
          text-transform: uppercase;
        }

        /* Submit */
        .auth-submit {
          width: 100%;
          padding: 16px;
          border-radius: 14px;
          border: none;
          font-weight: 700;
          font-size: 1rem;
          cursor: pointer;
          transition: all 0.3s;
          background: linear-gradient(135deg, #ff7a00 0%, #ff3d00 100%);
          color: #fff;
          box-shadow: 0 4px 24px rgba(255,122,0,0.4);
          margin-top: 10px;
          font-family: 'Inter', sans-serif;
          letter-spacing: 0.3px;
        }
        .auth-submit:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: 0 10px 32px rgba(255,122,0,0.55);
        }
        .auth-submit:disabled {
          background: #1e1e1e;
          color: #555;
          cursor: not-allowed;
          box-shadow: none;
          transform: none;
        }

        /* Alerts */
        .auth-alert {
          padding: 13px 16px;
          border-radius: 10px;
          font-size: 0.88rem;
          margin-bottom: 18px;
          text-align: center;
          animation: alertIn 0.3s ease;
        }
        .auth-alert.error  { background: rgba(239,68,68,0.1);  border: 1px solid rgba(239,68,68,0.3);  color: #fca5a5; }
        .auth-alert.success{ background: rgba(34,197,94,0.1);  border: 1px solid rgba(34,197,94,0.3);  color: #86efac; }

        @keyframes alertIn {
          from { opacity: 0; transform: translateY(-6px); }
          to   { opacity: 1; transform: translateY(0); }
        }

        .auth-form-anim {
          animation: formIn 0.35s cubic-bezier(0.16,1,0.3,1);
        }
        @keyframes formIn {
          from { opacity: 0; transform: translateY(10px); }
          to   { opacity: 1; transform: translateY(0); }
        }

        input::placeholder { color: #3a3a3a; }

        @media (max-width: 900px) {
          .auth-card-wrapper {
            flex-direction: column;
            max-width: 500px;
          }
          .auth-card-left {
            display: none;
          }
          .auth-card-right {
            width: 100%;
            border-left: none;
          }
        }

        @media (max-width: 560px) {
          .auth-card-wrapper { border-radius: 20px; }
          .auth-card-right { padding: 36px 24px; }
        }
      `}</style>

      <div className="auth-page">
        {/* Canvas particles */}
        <canvas ref={canvasRef} className="auth-canvas" />

        {/* Center Wrapper Container */}
        <div className="auth-card-wrapper">
          
          {/* ── Left Side: Welcome Text + CODE BG ── */}
          <div className="auth-card-left">
            
            {/* The scrolling code is now INSIDE the left side of the card, so it doesn't get blurred! */}
            <div className="code-waterfall">
              {[0, 1, 2].map((col) => {
                const start = (col * 14) % CODE_LINES.length;
                const slice = [...CODE_LINES.slice(start), ...CODE_LINES.slice(0, start)];
                const doubled = [...slice, ...slice];
                return (
                  <div
                    key={col}
                    className="code-col"
                    style={{ animationDuration: `${28 + col * 9}s`, animationDelay: `${col * -6}s` }}
                  >
                    {doubled.map((line, i) => (
                      <div key={i} className={`code-ln${i % 5 === 0 ? ' hl' : ''}`}>
                        {line || '\u00A0'}
                      </div>
                    ))}
                  </div>
                );
              })}
            </div>
            <div className="code-overlay" />

            <div className="left-content">
              <div className="auth-logo" onClick={() => navigate('/')} style={{ display: 'flex', alignItems: 'center' }}>
                <img src="/O1.png" alt="O1 Logo" style={{ height: '48px' }} />
              </div>
              
              <div className="auth-hero">
                <h1 className="auth-hero-title">
                  The Arena <br />
                  <span style={{ color: '#ff7a00' }}>Awaits.</span>
                </h1>
                <p className="auth-hero-sub">
                  Join the ultimate 1v1 competitive coding platform. Climb the global Elo ladder in real-time battles, or participate in elite hiring tournaments.
                </p>
              </div>

              <div className="auth-stats">
                <div className="stat-item">
                  <span className="stat-val">50K+</span>
                  <span className="stat-lbl">Engineers</span>
                </div>
                <div className="stat-item">
                  <span className="stat-val">2M+</span>
                  <span className="stat-lbl">Matches</span>
                </div>
                <div className="stat-item">
                  <span className="stat-val">340+</span>
                  <span className="stat-lbl">Tournaments</span>
                </div>
              </div>
            </div>
          </div>

          {/* ── Right Side: Login Form ── */}
          <div className="auth-card-right">
            <div style={{ textAlign: 'center', marginBottom: '24px' }}>
              <div style={{ display: 'inline-block', padding: '4px 12px', borderRadius: '20px', background: 'rgba(255,122,0,0.12)', border: '1px solid rgba(255,122,0,0.3)', color: '#ff7a00', fontSize: '0.75rem', fontWeight: 700, letterSpacing: '1px', textTransform: 'uppercase', marginBottom: '12px' }}>
                Engineer Arena Portal
              </div>
              <h2 style={{ fontSize: '1.8rem', fontWeight: 700, marginBottom: '8px', color: '#fff', fontFamily: "'Outfit', sans-serif" }}>
                {isLogin ? 'Engineer Login' : isOtpMode ? 'Email Verification' : 'Register Engineer'}
              </h2>
              <p style={{ color: '#888', fontSize: '0.95rem' }}>
                {isLogin ? 'Enter your credentials to enter the 1v1 Arena.' : 
                 isOtpMode ? 'Check your inbox for your 6-digit code.' : 
                 'Initialize your engineer profile to climb the Elo leaderboard.'}
              </p>
            </div>

            {/* Alerts */}
            {error   && <div className="auth-alert error">{error}</div>}
            {success && <div className="auth-alert success">{success}</div>}

            {/* Form */}
            <div key={currentMode} className="auth-form-anim">
              <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

                {!isOtpMode && (
                  <>

                    {/* Email */}
                    <div>
                      <label className="auth-label">Email Address</label>
                      <input
                        type="email"
                        value={email}
                        onChange={e => setEmail(e.target.value)}
                        required
                        placeholder="you@example.com"
                        autoComplete="email"
                        style={inputStyle('email')}
                        onFocus={() => setFocusedField('email')}
                        onBlur={() => setFocusedField(null)}
                      />
                    </div>

                    {/* Password */}
                    <div>
                      <label className="auth-label">Password</label>
                      <input
                        type="password"
                        value={password}
                        onChange={e => setPassword(e.target.value)}
                        required
                        placeholder="••••••••••••"
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
                    <label className="auth-label" style={{ textAlign: 'center' }}>Verification Code</label>
                    <div style={{ display: 'flex', gap: '8px', justifyContent: 'center', marginBottom: '8px' }}>
                      {otpArray.map((digit, index) => (
                        <input
                          key={index}
                          ref={el => otpRefs.current[index] = el}
                          type="text"
                          inputMode="numeric"
                          maxLength={1}
                          value={digit}
                          onChange={e => handleOtpChange(e, index)}
                          onKeyDown={e => handleOtpKeyDown(e, index)}
                          onPaste={handleOtpPaste}
                          onFocus={() => setFocusedField(`otp-${index}`)}
                          onBlur={() => setFocusedField(null)}
                          required={index === 0}
                          style={{
                            width: '56px',
                            height: '64px',
                            textAlign: 'center',
                            fontSize: '1.8rem',
                            fontWeight: 700,
                            fontFamily: "'Share Tech Mono', monospace",
                            background: 'rgba(255,255,255,0.04)',
                            border: focusedField === `otp-${index}` ? '1px solid #ff7a00' : '1px solid rgba(255,255,255,0.1)',
                            borderRadius: '12px',
                            color: '#fff',
                            outline: 'none',
                            transition: 'all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1)',
                            transform: focusedField === `otp-${index}` ? 'translateY(-3px)' : 'translateY(0)',
                            boxShadow: focusedField === `otp-${index}`
                              ? '0 8px 24px rgba(255,122,0,0.18), 0 0 0 3px rgba(255,122,0,0.12)'
                              : '0 2px 8px rgba(0,0,0,0.2)',
                          }}
                        />
                      ))}
                    </div>
                    <button
                      type="button"
                      onClick={() => setIsOtpMode(false)}
                      style={{ background: 'none', border: 'none', color: '#ff7a00', cursor: 'pointer', fontSize: '0.88rem', fontWeight: 600, marginTop: '14px', width: '100%', textAlign: 'center' }}
                    >
                      ← Back to Sign Up
                    </button>
                  </div>
                )}

                <button type="submit" className="auth-submit" disabled={loading}>
                  {loading ? 'Processing...' : isLogin ? 'Log In →' : isOtpMode ? 'Verify & Activate' : 'Create Account →'}
                </button>
              </form>
            </div>

            {!isOtpMode && (
              <>
                <div style={{ textAlign: 'center', marginTop: '24px', fontSize: '0.9rem', color: '#888' }}>
                  {isLogin ? "Don't have an engineer account? " : "Already have an engineer account? "}
                  <button 
                    type="button" 
                    onClick={() => navigate(isLogin ? '/signup' : '/login')}
                    style={{ background: 'none', border: 'none', color: '#ff7a00', cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem', transition: 'color 0.2s' }}
                    onMouseEnter={e => e.target.style.color = '#fff'}
                    onMouseLeave={e => e.target.style.color = '#ff7a00'}
                  >
                    {isLogin ? "Sign Up" : "Log In"}
                  </button>
                </div>
                <div style={{ textAlign: 'center', marginTop: '16px', fontSize: '0.85rem' }}>
                  <span style={{ color: '#666' }}>Hiring technical talent? </span>
                  <button
                    type="button"
                    onClick={() => navigate('/company/login')}
                    style={{ background: 'none', border: 'none', color: '#818cf8', cursor: 'pointer', fontWeight: 600, fontSize: '0.85rem' }}
                  >
                    Company Portal →
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}