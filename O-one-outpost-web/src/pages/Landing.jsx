import { useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { getCurrentRole } from '../utils/auth';

export default function Landing() {
  const navigate = useNavigate();
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    if (sessionStorage.getItem('sessionExpired') === 'true') {
      setSessionExpired(true);
      sessionStorage.removeItem('sessionExpired');
    }

    const token = localStorage.getItem('algo_arena_token');
    if (token) {
      const role = getCurrentRole();
      if (role === 'ADMIN') {
        navigate('/overseer');
      } else if (role === 'COMPANY') {
        navigate('/company/dashboard');
      } else {
        navigate('/lobby');
      }
    }

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('reveal-active');
        }
      });
    }, { threshold: 0.1 });

    const hiddenElements = document.querySelectorAll('.reveal-hidden');
    hiddenElements.forEach((el) => observer.observe(el));

    return () => observer.disconnect();
  }, [navigate]);

  return (
    <div style={{ backgroundColor: '#0a0503', color: '#fff', minHeight: '100vh', fontFamily: 'var(--font-ui)', overflowX: 'hidden', position: 'relative' }}>
      <style>
        {`
          @keyframes levitate {
            0% { transform: translateY(0px); }
            50% { transform: translateY(-15px); }
            100% { transform: translateY(0px); }
          }
          @keyframes gradient-flow {
            0% { background-position: 0% 50%; }
            50% { background-position: 100% 50%; }
            100% { background-position: 0% 50%; }
          }
          @keyframes pulse-glow {
            0% { opacity: 0.3; transform: scale(1); }
            50% { opacity: 0.6; transform: scale(1.05); }
            100% { opacity: 0.3; transform: scale(1); }
          }
          .glass-card {
            background: linear-gradient(135deg, rgba(255,122,0,0.05), rgba(20,10,5,0.6), rgba(255,61,0,0.05));
            background-size: 200% 200%;
            animation: levitate 6s ease-in-out infinite, gradient-flow 8s ease infinite;
            transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
            cursor: default;
          }
          .glass-card:hover {
            transform: translateY(-10px) scale(1.05) !important;
            box-shadow: 0 15px 35px rgba(255,122,0,0.2) !important;
            border-color: rgba(255,122,0,0.5) !important;
            z-index: 50 !important;
          }
          .pulse-element {
            animation: pulse-glow 4s ease-in-out infinite;
          }
          .reveal-hidden {
            opacity: 0 !important;
            transform: translateY(30px) !important;
            transition: opacity 0.8s ease-out, transform 0.8s ease-out !important;
          }
            .reveal-active {
              opacity: 1 !important;
              transform: translateY(0) !important;
            }
            @media (max-width: 768px) {
              .nav-links { display: none !important; }
              .nav-container { padding: 16px 20px !important; }
              .auth-buttons button { padding: 6px 12px !important; font-size: 0.8rem !important; }
              .hero-title { font-size: 2.5rem !important; }
              .hero-subtitle { font-size: 1rem !important; padding: 0 20px !important; }
              .hero-section { padding-top: 100px !important; padding-bottom: 40px !important; }
              .hero-buttons { flex-direction: column !important; padding: 0 20px !important; }
              .glass-card { display: none !important; }
              .split-section { grid-template-columns: 1fr !important; gap: 40px !important; padding: 60px 20px !important; }
              .split-section h2 { font-size: 2rem !important; }
              .logo-strip { gap: 30px !important; padding: 30px 20px !important; }
              .footer-grid { grid-template-columns: 1fr 1fr !important; gap: 30px !important; }
              .footer-container { padding: 40px 20px 20px !important; }
            }
            @media (max-width: 480px) {
              .footer-grid { grid-template-columns: 1fr !important; }
              .arena-trace { display: none !important; }
            }
            @keyframes slideDownFade {
              0% { opacity: 0; transform: translate(-50%, -40px); }
              10% { opacity: 1; transform: translate(-50%, 0); }
              90% { opacity: 1; transform: translate(-50%, 0); }
              100% { opacity: 0; transform: translate(-50%, -40px); }
            }
            .premium-toast {
              position: fixed;
              top: 100px;
              left: 50%;
              transform: translateX(-50%);
              background: rgba(15, 5, 5, 0.85);
              backdrop-filter: blur(16px);
              border: 1px solid rgba(239, 68, 68, 0.4);
              box-shadow: 0 10px 40px rgba(239, 68, 68, 0.2), inset 0 0 15px rgba(239, 68, 68, 0.1);
              padding: 16px 24px;
              border-radius: 16px;
              color: #fff;
              display: flex;
              align-items: center;
              gap: 16px;
              z-index: 9999;
              animation: slideDownFade 6s ease-in-out forwards;
              pointer-events: none;
            }
          `}
        </style>

        {sessionExpired && (
          <div className="premium-toast">
            <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: 'rgba(239, 68, 68, 0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#ef4444', fontSize: '1.2rem', boxShadow: '0 0 15px rgba(239, 68, 68, 0.3)' }}>
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
            </div>
            <div>
              <div style={{ fontSize: '1rem', fontWeight: 600, color: '#fca5a5' }}>Session Expired</div>
              <div style={{ fontSize: '0.85rem', color: '#a1a1aa', marginTop: '2px' }}>For your security, please log in again.</div>
            </div>
          </div>
        )}

        {/* Scattered Animated Glass Cards (Absolute positioned, scrolls with page) */}
      <div className="glass-card" style={{ position: 'absolute', top: '15%', left: '8%', padding: '16px', backdropFilter: 'blur(10px)', border: '1px solid rgba(255,122,0,0.2)', borderRadius: '12px', zIndex: 0, display: 'flex', flexDirection: 'column', gap: '8px', opacity: 0.9 }}>
        <div style={{ color: '#ff7a00', fontSize: '0.85rem', fontWeight: 'bold', fontFamily: 'var(--font-mono)' }}>function solve() {"{"}</div>
        <div style={{ width: '100px', height: '4px', background: '#333', borderRadius: '2px', marginLeft: '12px' }}><div style={{ width: '60%', height: '100%', background: '#ff7a00', borderRadius: '2px' }}/></div>
        <div style={{ width: '80px', height: '4px', background: '#333', borderRadius: '2px', marginLeft: '12px' }}><div style={{ width: '100%', height: '100%', background: '#ffbd2e', borderRadius: '2px' }}/></div>
        <div style={{ color: '#ff7a00', fontSize: '0.85rem', fontWeight: 'bold', fontFamily: 'var(--font-mono)' }}>{"}"}</div>
      </div>

      <div className="glass-card" style={{ position: 'absolute', top: '45%', right: '8%', padding: '20px', backdropFilter: 'blur(10px)', border: '1px solid rgba(255,122,0,0.2)', borderRadius: '16px', zIndex: 0, display: 'flex', alignItems: 'center', gap: '16px', opacity: 0.9, animationDelay: '1s' }}>
        <div style={{ width: '44px', height: '44px', borderRadius: '50%', background: 'linear-gradient(135deg, #ff7a00, #ff3d00)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 'bold', fontSize: '1.2rem', boxShadow: '0 4px 15px rgba(255,122,0,0.4)' }}>+</div>
        <div>
          <div style={{ color: '#fff', fontSize: '1rem', fontWeight: 'bold' }}>Elo +25</div>
          <div style={{ color: '#27c93f', fontSize: '0.85rem', fontWeight: 600 }}>Victory</div>
        </div>
      </div>

      <div className="glass-card" style={{ position: 'absolute', top: '75%', left: '10%', padding: '16px', backdropFilter: 'blur(10px)', border: '1px solid rgba(255,122,0,0.2)', borderRadius: '12px', zIndex: 0, display: 'flex', alignItems: 'center', gap: '12px', opacity: 0.9, animationDelay: '2s' }}>
        <div style={{ color: '#ff7a00', fontSize: '1.5rem' }}>⚡</div>
        <div>
          <div style={{ color: '#fff', fontSize: '0.9rem', fontWeight: 'bold' }}>Sandbox Ready</div>
          <div style={{ color: '#888', fontSize: '0.8rem' }}>Latency: 12ms</div>
        </div>
      </div>
      
      {/* Top Navigation */}
      <nav className="nav-container" style={{ position: 'fixed', top: 0, width: '100%', padding: '24px 40px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', zIndex: 100, backdropFilter: 'blur(10px)', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '40px' }}>
          <div style={{ display: 'flex', alignItems: 'center', fontSize: '1.4rem', fontWeight: 800, fontFamily: 'var(--font-heading)', letterSpacing: '-0.5px' }}>
            <img src="/O1.png" alt="O1 Logo" style={{ height: '48px' }} />
          </div>
          <div className="nav-links" style={{ display: 'flex', gap: '24px', fontSize: '0.9rem', color: '#a1a1aa' }}>
            <span style={{ cursor: 'pointer', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#fff'} onMouseLeave={e => e.target.style.color = '#a1a1aa'}>Arena</span>
            <span style={{ cursor: 'pointer', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#fff'} onMouseLeave={e => e.target.style.color = '#a1a1aa'}>Assessments</span>
            <span style={{ cursor: 'pointer', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#fff'} onMouseLeave={e => e.target.style.color = '#a1a1aa'}>Security</span>
            <span style={{ cursor: 'pointer', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#fff'} onMouseLeave={e => e.target.style.color = '#a1a1aa'}>Pricing</span>
          </div>
        </div>
        <div className="auth-buttons" style={{ display: 'flex', gap: '12px' }}>
          <button 
            onClick={() => navigate('/company/login')}
            style={{ padding: '8px 18px', borderRadius: '20px', background: 'rgba(99, 102, 241, 0.12)', color: '#a5b4fc', border: '1px solid rgba(99, 102, 241, 0.35)', fontWeight: 600, fontSize: '0.85rem', transition: 'all 0.3s', cursor: 'pointer' }}
            onMouseEnter={e => {e.target.style.background = 'rgba(99, 102, 241, 0.25)'; e.target.style.borderColor = '#818cf8';}}
            onMouseLeave={e => {e.target.style.background = 'rgba(99, 102, 241, 0.12)'; e.target.style.borderColor = 'rgba(99, 102, 241, 0.35)';}}
          >
            🏢 Enterprise Portal
          </button>
          <button 
            onClick={() => navigate('/login')}
            style={{ padding: '8px 18px', borderRadius: '20px', background: 'transparent', color: '#fff', border: '1px solid rgba(255,255,255,0.15)', fontWeight: 600, fontSize: '0.85rem', transition: 'all 0.3s', cursor: 'pointer' }}
            onMouseEnter={e => {e.target.style.background = 'rgba(255,255,255,0.1)'; e.target.style.borderColor = 'rgba(255,255,255,0.3)';}}
            onMouseLeave={e => {e.target.style.background = 'transparent'; e.target.style.borderColor = 'rgba(255,255,255,0.15)';}}
          >
            Engineer Log In
          </button>
          <button 
            onClick={() => navigate('/signup')}
            style={{ padding: '8px 20px', borderRadius: '20px', background: 'linear-gradient(135deg, #ff7a00, #ff3d00)', color: '#fff', border: 'none', fontWeight: 600, fontSize: '0.85rem', transition: 'all 0.3s', cursor: 'pointer', boxShadow: '0 4px 15px rgba(255, 122, 0, 0.3)' }}
            onMouseEnter={e => {e.target.style.transform = 'translateY(-1px)'; e.target.style.boxShadow = '0 6px 20px rgba(255, 122, 0, 0.4)';}}
            onMouseLeave={e => {e.target.style.transform = 'none'; e.target.style.boxShadow = '0 4px 15px rgba(255, 122, 0, 0.3)';}}
          >
            Sign Up
          </button>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="hero-section" style={{ position: 'relative', paddingTop: '160px', paddingBottom: '80px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', background: 'radial-gradient(circle at top, rgba(255, 122, 0, 0.15) 0%, rgba(255, 61, 0, 0.05) 40%, #0a0503 80%)' }}>
        
        {/* Background Floating Orbs */}
        <div className="pulse-element" style={{ position: 'absolute', top: '10%', left: '15%', width: '300px', height: '300px', background: 'radial-gradient(circle, rgba(255,122,0,0.15) 0%, transparent 70%)', filter: 'blur(40px)', zIndex: 1, pointerEvents: 'none' }} />
        <div className="pulse-element" style={{ position: 'absolute', top: '40%', right: '10%', width: '400px', height: '400px', background: 'radial-gradient(circle, rgba(255,61,0,0.1) 0%, transparent 70%)', filter: 'blur(50px)', zIndex: 1, pointerEvents: 'none', animationDelay: '2s' }} />

        <h1 className="reveal-hidden hero-title" style={{ color: '#ffffff', textShadow: '0 0 20px rgba(255,255,255,0.3)', fontSize: '4.5rem', fontWeight: 600, letterSpacing: '-2px', lineHeight: 1.1, marginBottom: '24px', maxWidth: '900px', zIndex: 10 }}>
          Where the world's best engineers battle, <br/>
          <span style={{ color: '#ff7a00', textShadow: '0 0 20px rgba(255,122,0,0.4)' }}>and top companies hire.</span>
        </h1>
        <p className="reveal-hidden hero-subtitle" style={{ fontSize: '1.2rem', color: '#e4e4e7', textShadow: '0 0 10px rgba(255,255,255,0.1)', maxWidth: '700px', marginBottom: '40px', zIndex: 10, lineHeight: 1.6, transitionDelay: '0.2s' }}>
          O1 Outpost is the ultimate 1v1 competitive coding platform. Climb the global Elo ladder in real-time battles, or host elite hiring tournaments to find your next star engineer with AI-driven autonomy.
        </p>
        
        <div className="reveal-hidden hero-buttons" style={{ display: 'flex', gap: '16px', zIndex: 10, transitionDelay: '0.4s' }}>
          <button 
            onClick={() => navigate('/login')}
            style={{ padding: '14px 32px', borderRadius: '30px', background: 'linear-gradient(135deg, #ff7a00, #ff3d00)', color: '#fff', border: 'none', fontWeight: 600, fontSize: '1rem', transition: 'all 0.3s', cursor: 'pointer', boxShadow: '0 4px 20px rgba(255, 122, 0, 0.4)' }}
            onMouseEnter={e => {e.target.style.transform = 'translateY(-2px)'; e.target.style.boxShadow = '0 8px 30px rgba(255, 122, 0, 0.6)';}}
            onMouseLeave={e => {e.target.style.transform = 'none'; e.target.style.boxShadow = '0 4px 20px rgba(255, 122, 0, 0.4)';}}
          >
            Enter the Arena
          </button>
          <button 
            onClick={() => navigate('/login')}
            style={{ padding: '14px 32px', borderRadius: '30px', background: 'rgba(255,255,255,0.05)', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', fontWeight: 600, fontSize: '1rem', transition: 'all 0.3s', cursor: 'pointer' }}
            onMouseEnter={e => {e.target.style.background = 'rgba(255,255,255,0.1)';}}
            onMouseLeave={e => {e.target.style.background = 'rgba(255,255,255,0.05)';}}
          >
            For Companies
          </button>
        </div>

        {/* Floating Glass UI Element - Arena Trace */}
        <div className="arena-trace" style={{ marginTop: '80px', position: 'relative', zIndex: 10, width: '100%', maxWidth: '900px', perspective: '1000px', padding: '0 20px', boxSizing: 'border-box' }}>
          <div style={{ 
            background: 'rgba(20, 10, 5, 0.6)', 
            backdropFilter: 'blur(20px)', 
            border: '1px solid rgba(255,122,0,0.2)', 
            borderRadius: '16px', 
            padding: '24px',
            boxShadow: '0 30px 60px rgba(0,0,0,0.8), inset 0 0 40px rgba(255, 122, 0, 0.05)',
            transform: 'rotateX(5deg) scale(0.95)',
            textAlign: 'left',
            fontFamily: 'var(--font-mono)'
          }}>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '20px' }}>
              <div style={{ width: '12px', height: '12px', borderRadius: '50%', background: '#ff5f56' }} />
              <div style={{ width: '12px', height: '12px', borderRadius: '50%', background: '#ffbd2e' }} />
              <div style={{ width: '12px', height: '12px', borderRadius: '50%', background: '#27c93f' }} />
            </div>
            
            <div style={{ color: '#888', fontSize: '0.9rem', marginBottom: '8px' }}>
              <span style={{ color: '#ff7a00' }}>[SYS]</span> MATCHMAKER: Player 'alex_dev' (Elo: 1840) joined queue...
            </div>
            <div style={{ color: '#888', fontSize: '0.9rem', marginBottom: '8px' }}>
              <span style={{ color: '#ff7a00' }}>[SYS]</span> MATCHMAKER: Player 'sarah_codes' (Elo: 1855) joined queue...
            </div>
            <div style={{ color: '#888', fontSize: '0.9rem', marginBottom: '8px' }}>
              <span style={{ color: '#ff7a00' }}>[SYS]</span> LOBBY: Match found! Establishing secure WebSocket...
            </div>
            <div style={{ background: 'rgba(255, 122, 0, 0.1)', padding: '12px', borderRadius: '8px', margin: '16px 0', borderLeft: '3px solid #ff7a00' }}>
              <div style={{ color: '#fff', fontSize: '1rem', fontWeight: 'bold' }}>ARENA INITIALIZED: Hard Difficulty</div>
              <div style={{ color: '#aaa', fontSize: '0.85rem', marginTop: '4px' }}>Problem: "Dynamic Programming Traversal". 15 Minutes. Sandboxed Environment.</div>
            </div>
          </div>
        </div>
      </section>

      {/* Logo Strip */}
      <section className="reveal-hidden logo-strip" style={{ borderTop: '1px solid rgba(255,255,255,0.05)', borderBottom: '1px solid rgba(255,255,255,0.05)', padding: '40px 0', display: 'flex', justifyContent: 'center', gap: '60px', flexWrap: 'wrap', opacity: 0.5, background: 'rgba(0,0,0,0.3)' }}>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>scale</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>AT&T</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>replit</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>Fidelity</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', fontFamily: 'var(--font-heading)' }}>Windsurf</div>
      </section>

      {/* Arena Features Split Section (B2C) */}
      <section className="reveal-hidden split-section" style={{ padding: '120px 40px', maxWidth: '1200px', margin: '0 auto', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '80px', alignItems: 'center' }}>
        {/* Simulated Arena UI */}
        <div style={{ background: '#0f0805', border: '1px solid rgba(255,122,0,0.2)', borderRadius: '16px', padding: '30px', boxShadow: '0 20px 40px rgba(0,0,0,0.5)' }}>
          <div style={{ color: '#ff7a00', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '20px', fontWeight: 'bold' }}>Live 1v1 Arena</div>
          
          <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: '20px', alignItems: 'center', marginBottom: '24px' }}>
            <div style={{ textAlign: 'center', background: '#1a100a', padding: '20px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <div style={{ color: '#fff', fontSize: '1.2rem', fontWeight: 'bold' }}>alex_dev</div>
              <div style={{ color: '#ff7a00', fontSize: '0.9rem', marginTop: '4px' }}>Elo 1840</div>
            </div>
            <div style={{ color: '#555', fontSize: '1.5rem', fontWeight: 'bold' }}>VS</div>
            <div style={{ textAlign: 'center', background: '#1a100a', padding: '20px', borderRadius: '12px', border: '1px solid rgba(255,255,255,0.05)' }}>
              <div style={{ color: '#fff', fontSize: '1.2rem', fontWeight: 'bold' }}>sarah_codes</div>
              <div style={{ color: '#ff7a00', fontSize: '0.9rem', marginTop: '4px' }}>Elo 1855</div>
            </div>
          </div>

          <div style={{ background: '#120a06', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '16px', fontFamily: 'var(--font-mono)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
              <span style={{ color: '#aaa' }}>Test Cases Passing:</span>
              <span style={{ color: '#27c93f' }}>14 / 15</span>
            </div>
            <div style={{ width: '100%', height: '6px', background: '#222', borderRadius: '3px', overflow: 'hidden' }}>
              <div style={{ width: '93%', height: '100%', background: 'linear-gradient(90deg, #ff7a00, #ffbd2e, #27c93f)' }} />
            </div>
            <div style={{ marginTop: '16px', color: '#ff7a00', fontSize: '0.85rem' }}>&gt; Execution Time: 42ms (Memory: 38MB)</div>
          </div>
        </div>

        <div>
          <h2 style={{ fontSize: '3rem', fontWeight: 500, lineHeight: 1.1, marginBottom: '24px' }}>
            Prove your skill.<br/>
            <span style={{ color: '#ff7a00' }}>Earn your rank.</span>
          </h2>
          <p style={{ color: '#a1a1aa', fontSize: '1.1rem', marginBottom: '40px', lineHeight: 1.6 }}>
            Drop into the arena and face off against engineers around the world in real-time algorithmic battles. Our secure, isolated sandboxes execute code instantly.
          </p>

          <div style={{ marginBottom: '32px' }}>
            <h3 style={{ fontSize: '1.2rem', color: '#fff', marginBottom: '8px' }}>True Skill Matchmaking</h3>
            <p style={{ color: '#888', fontSize: '0.95rem' }}>Our Elo-based matchmaking engine pairs you with opponents of similar skill levels, ensuring every battle is fiercely competitive.</p>
          </div>

          <div style={{ marginBottom: '32px' }}>
            <h3 style={{ fontSize: '1.2rem', color: '#fff', marginBottom: '8px' }}>Real-Time Execution</h3>
            <p style={{ color: '#888', fontSize: '0.95rem' }}>Type, compile, and run. See your test cases pass in milliseconds while WebSockets broadcast your progress to your opponent.</p>
          </div>
        </div>
      </section>

      {/* B2B Assessment Features Split Section */}
      <section className="reveal-hidden split-section" style={{ padding: '80px 40px', maxWidth: '1200px', margin: '0 auto', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '80px', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '3rem', fontWeight: 500, lineHeight: 1.1, marginBottom: '24px' }}>
            <span style={{ color: '#ff7a00' }}>Autonomy</span> ends where<br/>
            your signature begins.
          </h2>
          <p style={{ color: '#a1a1aa', fontSize: '1.1rem', marginBottom: '40px', lineHeight: 1.6 }}>
            For enterprise recruiters, agents can draft assessments, evaluate code, and rank candidates automatically. Anything that moves a candidate forward waits for a person.
          </p>

          <div style={{ marginBottom: '32px' }}>
            <h3 style={{ fontSize: '1.2rem', color: '#fff', marginBottom: '8px' }}>Thresholds you set</h3>
            <p style={{ color: '#888', fontSize: '0.95rem' }}>Difficulty limits, passing scores, and role types that always escalate. Automatically filter noise.</p>
          </div>

          <div style={{ marginBottom: '32px', paddingLeft: '16px', borderLeft: '2px solid #ff7a00' }}>
            <h3 style={{ fontSize: '1.2rem', color: '#fff', marginBottom: '8px' }}>Review the diff, not the summary</h3>
            <p style={{ color: '#888', fontSize: '0.95rem' }}>Every line of code the agent analyzed, side by side. You approve the actual evaluation, never a paraphrase of it.</p>
          </div>
        </div>

        {/* Simulated B2B Assessment Panel */}
        <div style={{ background: '#0a0a0f', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '16px', padding: '30px', boxShadow: '0 20px 40px rgba(0,0,0,0.5)' }}>
          <div style={{ color: '#a1a1aa', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '1px', marginBottom: '20px' }}>Review Candidate Evaluation</div>
          
          <div style={{ background: '#12121a', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', overflow: 'hidden' }}>
            <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'grid', gridTemplateColumns: '120px 1fr' }}>
              <span style={{ color: '#666' }}>Candidate</span>
              <span style={{ color: '#ddd' }}>Jane Smith</span>
            </div>
            <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'grid', gridTemplateColumns: '120px 1fr' }}>
              <span style={{ color: '#666' }}>Role</span>
              <span style={{ color: '#ddd' }}>Senior Backend Engineer</span>
            </div>
            <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)', display: 'grid', gridTemplateColumns: '120px 1fr', background: 'rgba(255, 122, 0, 0.05)' }}>
              <span style={{ color: '#666' }}>Score</span>
              <span style={{ color: '#ff7a00', fontWeight: 'bold' }}>98% (Exceeds Threshold)</span>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '12px', marginTop: '24px' }}>
            <button style={{ flex: 1, padding: '12px', background: 'rgba(255,255,255,0.05)', color: '#fff', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', cursor: 'pointer' }}>Reject</button>
            <button style={{ flex: 1, padding: '12px', background: '#ff7a00', color: '#000', border: 'none', borderRadius: '8px', fontWeight: 'bold', cursor: 'pointer' }}>Approve Move to Onsite</button>
          </div>
        </div>
      </section>

      {/* Platform Features Grid */}
      <section className="reveal-hidden" style={{ padding: '100px 40px', maxWidth: '1200px', margin: '0 auto' }}>
        <div style={{ textAlign: 'center', marginBottom: '80px' }}>
          <h2 style={{ fontSize: '3rem', fontWeight: 500, lineHeight: 1.1, marginBottom: '24px' }}>
            Built for <span style={{ color: '#ff7a00' }}>Scale</span> and <span style={{ color: '#ff7a00' }}>Security</span>.
          </h2>
          <p style={{ color: '#a1a1aa', fontSize: '1.1rem', maxWidth: '600px', margin: '0 auto', lineHeight: 1.6 }}>
            Our infrastructure is designed to handle thousands of concurrent matches with millisecond latency, all while keeping malicious code isolated.
          </p>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '40px' }}>
          {/* Feature 1: The Platform */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '16px', padding: '40px', transition: 'all 0.3s', cursor: 'default' }} onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-5px)'} onMouseLeave={e => e.currentTarget.style.transform = 'none'}>
            <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'rgba(255,122,0,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', marginBottom: '24px', color: '#ff7a00' }}>⚔️</div>
            <h3 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '16px' }}>The Ultimate Arena</h3>
            <p style={{ color: '#888', lineHeight: 1.6, fontSize: '0.95rem' }}>
              Engage in 1v1 algorithmic battles across multiple difficulty tiers. Our real-time matchmaking system pairs you with opponents of identical skill levels using a global Elo ranking system. Companies use these verified ratings to discover top talent.
            </p>
          </div>

          {/* Feature 2: The Sandbox */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '16px', padding: '40px', transition: 'all 0.3s', cursor: 'default' }} onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-5px)'} onMouseLeave={e => e.currentTarget.style.transform = 'none'}>
            <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'rgba(255,122,0,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', marginBottom: '24px', color: '#ff7a00' }}>⚡</div>
            <h3 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '16px' }}>Isolated Execution Sandbox</h3>
            <p style={{ color: '#888', lineHeight: 1.6, fontSize: '0.95rem' }}>
              Code is compiled and run inside containerized, ephemeral sandboxes. Supporting over 10+ programming languages, the sandbox provides instant feedback on hidden test cases, execution time, and memory usage without compromising the host server.
            </p>
          </div>

          {/* Feature 3: Security */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.05)', borderRadius: '16px', padding: '40px', transition: 'all 0.3s', cursor: 'default' }} onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-5px)'} onMouseLeave={e => e.currentTarget.style.transform = 'none'}>
            <div style={{ width: '48px', height: '48px', borderRadius: '12px', background: 'rgba(255,122,0,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', marginBottom: '24px', color: '#ff7a00' }}>🛡️</div>
            <h3 style={{ fontSize: '1.4rem', color: '#fff', marginBottom: '16px' }}>Enterprise-Grade Security</h3>
            <p style={{ color: '#888', lineHeight: 1.6, fontSize: '0.95rem' }}>
              We deploy advanced anti-cheat mechanisms, plagiarism detection, and strictly enforced resource quotas (CPU/Memory). All communication flows through encrypted WebSockets, guaranteeing secure and fair assessments for B2B hiring partners.
            </p>
          </div>
        </div>
      </section>

      {/* Footer Section */}
      <footer className="footer-container" style={{ background: '#050201', borderTop: '1px solid rgba(255,255,255,0.05)', padding: '80px 40px 40px', marginTop: '60px' }}>
        <div className="footer-grid" style={{ maxWidth: '1200px', margin: '0 auto', display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '40px', marginBottom: '60px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', marginBottom: '20px' }}>
              <img src="/O1.png" alt="O1 Logo" style={{ height: '48px' }} />
            </div>
            <p style={{ color: '#666', fontSize: '0.9rem', lineHeight: 1.6 }}>
              The definitive platform for global engineering talent. Code, compete, and get hired.
            </p>
          </div>
          
          <div>
            <h4 style={{ color: '#fff', fontWeight: 600, marginBottom: '20px' }}>Product</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>The Arena</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>B2B Assessments</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Leaderboards</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Pricing</a>
            </div>
          </div>

          <div>
            <h4 style={{ color: '#fff', fontWeight: 600, marginBottom: '20px' }}>Resources</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Documentation</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>API Reference</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Blog</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Community</a>
            </div>
          </div>

          <div>
            <h4 style={{ color: '#fff', fontWeight: 600, marginBottom: '20px' }}>Company</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>About Us</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Careers</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Privacy Policy</a>
              <a href="#" style={{ color: '#888', textDecoration: 'none', fontSize: '0.9rem', transition: 'color 0.2s' }} onMouseEnter={e => e.target.style.color = '#ff7a00'} onMouseLeave={e => e.target.style.color = '#888'}>Terms of Service</a>
            </div>
          </div>
        </div>
        
        <div style={{ borderTop: '1px solid rgba(255,255,255,0.05)', paddingTop: '30px', textAlign: 'center', color: '#555', fontSize: '0.85rem' }}>
          &copy; {new Date().getFullYear()} O1 Outpost, Inc. All rights reserved.
        </div>
      </footer>
    </div>
  );
}
