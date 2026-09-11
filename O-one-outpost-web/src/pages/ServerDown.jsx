import React from 'react';

export default function ServerDown() {
  const handleRetry = () => {
    window.location.reload();
  };

  return (
    <div style={{ backgroundColor: '#0a0503', color: '#fff', minHeight: '100vh', fontFamily: "'Inter', sans-serif", overflow: 'hidden', position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
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
            background: linear-gradient(135deg, rgba(255,61,0,0.1), rgba(20,10,5,0.8), rgba(255,122,0,0.05));
            background-size: 200% 200%;
            animation: levitate 6s ease-in-out infinite, gradient-flow 8s ease infinite;
            border: 1px solid rgba(255,61,0,0.2);
            box-shadow: 0 20px 50px rgba(0,0,0,0.5), inset 0 0 20px rgba(255,61,0,0.05);
            backdrop-filter: blur(20px);
            border-radius: 24px;
            padding: 60px 40px;
            text-align: center;
            max-width: 600px;
            width: 90%;
            position: relative;
            z-index: 10;
          }
          .pulse-bg {
            position: absolute;
            border-radius: 50%;
            filter: blur(80px);
            z-index: 1;
            animation: pulse-glow 6s ease-in-out infinite;
          }
          .btn-primary {
            background: linear-gradient(45deg, #ff3d00, #ff7a00);
            color: #fff;
            border: none;
            padding: 14px 32px;
            border-radius: 30px;
            font-size: 1.1rem;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.3s;
            box-shadow: 0 10px 25px rgba(255, 122, 0, 0.4);
            display: inline-flex;
            align-items: center;
            gap: 12px;
            font-family: "'Inter', sans-serif";
            text-transform: uppercase;
            letter-spacing: 1px;
          }
          .btn-primary:hover {
            transform: translateY(-3px);
            box-shadow: 0 15px 35px rgba(255, 122, 0, 0.6);
          }
          .btn-secondary {
            background: rgba(255,255,255,0.05);
            color: #fff;
            border: 1px solid rgba(255,255,255,0.1);
            padding: 14px 32px;
            border-radius: 30px;
            font-size: 1.1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            text-decoration: none;
            display: inline-block;
            font-family: "'Inter', sans-serif";
          }
          .btn-secondary:hover {
            background: rgba(255,255,255,0.1);
            border-color: rgba(255,255,255,0.2);
          }
          .tech-font {
            font-family: 'Share Tech Mono', monospace;
            color: #ff7a00;
            letter-spacing: 2px;
          }
          .warning-icon {
            font-size: 4rem;
            margin-bottom: 20px;
            display: inline-block;
            animation: pulse-glow 3s ease-in-out infinite;
            filter: drop-shadow(0 0 20px rgba(255,61,0,0.8));
          }
        `}
      </style>

      {/* Decorative Background Elements */}
      <div className="pulse-bg" style={{ top: '20%', left: '10%', width: '400px', height: '400px', background: 'rgba(255, 61, 0, 0.15)', animationDelay: '0s' }} />
      <div className="pulse-bg" style={{ bottom: '10%', right: '10%', width: '500px', height: '500px', background: 'rgba(255, 122, 0, 0.1)', animationDelay: '2s' }} />
      
      {/* Brand Navbar (Static Top) */}
      <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', padding: '24px 40px', display: 'flex', justifyContent: 'center', zIndex: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <img src="/O1.png" alt="O1 Logo" style={{ height: '36px', filter: 'brightness(1.2)' }} />
          <span style={{ fontFamily: "'Outfit', sans-serif", fontSize: '1.5rem', fontWeight: 800, letterSpacing: '-0.5px' }}>
            OUTPOST
          </span>
        </div>
      </div>

      <div className="glass-card">
        <div className="warning-icon">⚠️</div>
        
        <h1 style={{ fontFamily: "'Outfit', sans-serif", fontSize: '3.5rem', fontWeight: 800, margin: '0 0 16px 0', lineHeight: 1.1, letterSpacing: '-1px' }}>
          System <span style={{ color: '#ff3d00' }}>Offline</span>
        </h1>
        
        <p style={{ fontSize: '1.1rem', color: '#a1a1aa', lineHeight: 1.6, marginBottom: '40px', maxWidth: '450px', margin: '0 auto 40px auto' }}>
          The AlgoArena backend servers are currently unreachable. We are either performing scheduled maintenance or experiencing an unexpected core outage.
          <br /><br />
          Our engineers have been notified and we will be back online shortly.
        </p>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', alignItems: 'center' }}>
          <button onClick={handleRetry} className="btn-primary">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 2v6h-6"></path>
              <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
            </svg>
            Re-establish Link
          </button>
          
          <a href="mailto:support@algoarena.com" className="btn-secondary">
            Contact Support
          </a>
        </div>
        
        <div style={{ marginTop: '50px', paddingTop: '20px', borderTop: '1px solid rgba(255,255,255,0.05)' }}>
          <p className="tech-font" style={{ fontSize: '0.85rem', margin: 0, opacity: 0.7 }}>
            DIAGNOSTIC CODE: ERR_CORE_CONNECTION_REFUSED
          </p>
        </div>
      </div>
    </div>
  );
}
