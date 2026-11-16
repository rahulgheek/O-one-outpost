import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';
import EngineerNav from '../components/EngineerNav';

const GATEWAY_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';
const ARENA_WS_URL = (import.meta.env.VITE_ARENA_WS_URL || 'ws://localhost:8084').replace(/^ws/, 'http');

export default function Lobby({ currentUser, getToken }) {
  const [status, setStatus] = useState('IDLE');
  const [statusMsg, setStatusMsg] = useState('');
  const [queueTime, setQueueTime] = useState(0);
  const navigate = useNavigate();
  const stompRef = useRef(null);
  const matchFoundRef = useRef(false);
  const timerRef = useRef(null);
  const isSearchingRef = useRef(false);
  const canvasRef = useRef(null);
  
  // States
  const [stats, setStats] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);

  useEffect(() => {
    const headers = { Authorization: `Bearer ${getToken()}` };
    
    // Fetch user stats
    axios.get(`${GATEWAY_URL}/api/profiles/${currentUser}`, { headers })
      .then(s => setStats(s.data))
      .catch(console.error);
      
      // Fetch leaderboard
    axios.get(`${GATEWAY_URL}/api/profiles/leaderboard`, { headers })
      .then(res => setLeaderboard(res.data))
      .catch(console.error);
  }, [currentUser, getToken]);


  useEffect(() => {
    if (status === 'SEARCHING') {
      timerRef.current = setInterval(() => setQueueTime(t => t + 1), 1000);
    } else {
      clearInterval(timerRef.current);
      if (status === 'IDLE') setQueueTime(0);
    }
    return () => clearInterval(timerRef.current);
  }, [status]);

  const formatTime = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

  const handleFind = async () => {
    if (isSearchingRef.current) return;
    isSearchingRef.current = true;
    matchFoundRef.current = false;
    setStatus('SEARCHING');
    setStatusMsg('Scanning Elo brackets for a worthy opponent...');

    const client = new Client({
      webSocketFactory: () => new SockJS(`${ARENA_WS_URL}/ws-arena`),
      reconnectDelay: 0,
      onConnect: async () => {
        client.subscribe(`/topic/lobby/${currentUser}`, (msg) => {
          const data = JSON.parse(msg.body);
          if (data.status === 'MATCH_FOUND' && !matchFoundRef.current) {
            matchFoundRef.current = true;
            setStatus('FOUND');
            setStatusMsg('Opponent locked in. Preparing battle arena...');
            client.deactivate();
            setTimeout(() => navigate(`/arena/${data.matchId}`, { state: { problemId: data.problemId } }), 1200);
          }
        });

        try {
          const res = await axios.post(
            `${GATEWAY_URL}/api/match/join`,
            {},
            { headers: { Authorization: `Bearer ${getToken()}` } }
          );
          const match = res.data;
          if (match.status === 'IN_PROGRESS' && !matchFoundRef.current) {
            matchFoundRef.current = true;
            setStatus('FOUND');
            setStatusMsg('Instant match found. Preparing battle arena...');
            client.deactivate();
            setTimeout(() => navigate(`/arena/${match.matchId}`, { state: { problemId: match.problemId } }), 1200);
          } else {
            setStatusMsg('In queue — waiting for opponent...');
          }
        } catch {
          setStatus('IDLE');
          setStatusMsg('');
          isSearchingRef.current = false;
          client.deactivate();
        }
      },
      onWebSocketError: () => { setStatus('IDLE'); isSearchingRef.current = false; },
    });

    stompRef.current = client;
    client.activate();
  };

  const handleCancel = () => {
    stompRef.current?.deactivate();
    setStatus('IDLE');
    setStatusMsg('');
    isSearchingRef.current = false;
    matchFoundRef.current = false;
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
    
    const particles = Array.from({ length: 70 }, () => ({
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

  const winRate = stats?.matchesPlayed > 0 ? Math.round((stats.wins / stats.matchesPlayed) * 100) : 0;

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        
        body { background: #0a0503; margin: 0; }

        .lobby-page {
          min-height: 100vh;
          background: #0a0503;
          font-family: 'Inter', sans-serif;
          color: #fff;
          position: relative;
          overflow-x: hidden;
        }

        .lobby-canvas {
          position: fixed;
          inset: 0;
          width: 100vw;
          height: 100vh;
          pointer-events: none;
          z-index: 0;
        }

        .lobby-bg-gradient {
          position: fixed;
          inset: 0;
          background: radial-gradient(ellipse at top, rgba(255, 122, 0, 0.08) 0%, transparent 70%);
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

        .nav-logo { font-family: 'Outfit', sans-serif; font-size: 1.5rem; font-weight: 800; letter-spacing: -0.5px; cursor: pointer; }
        .nav-links { display: flex; gap: 32px; font-size: 0.95rem; color: #a1a1aa; font-weight: 500; }
        .nav-links span { cursor: pointer; transition: color 0.2s; }
        .nav-links span:hover, .nav-links span.active { color: #fff; }
        .nav-profile { display: flex; align-items: center; gap: 20px; }
        .btn-logout {
          padding: 8px 20px; border-radius: 20px; background: rgba(255,255,255,0.05);
          color: #fff; border: 1px solid rgba(255,255,255,0.1); font-weight: 600; font-size: 0.85rem; cursor: pointer; transition: all 0.2s;
        }
        .btn-logout:hover { background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.4); color: #fca5a5; }

        /* Layout Grid */
        .lobby-content {
          position: relative;
          z-index: 10;
          max-width: 1400px;
          margin: 40px auto;
          padding: 0 40px;
          display: grid;
          grid-template-columns: 320px 1fr 320px;
          gap: 40px;
          align-items: start;
        }

        /* Glass Panels (Sidebars) */
        .side-panel {
          background: rgba(15, 8, 3, 0.4);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          border: 1px solid rgba(255, 122, 0, 0.1);
          border-radius: 20px;
          padding: 28px 24px;
          box-shadow: inset 0 0 20px rgba(255,122,0,0.02);
        }

        .panel-title {
          font-family: 'Outfit', sans-serif;
          font-size: 1.2rem;
          font-weight: 700;
          color: #fff;
          margin-bottom: 24px;
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .panel-title::before {
          content: ''; display: block; width: 4px; height: 16px; background: #ff7a00; border-radius: 2px;
        }

        /* Stats visualizers */
        .stat-row { display: flex; justify-content: space-between; margin-bottom: 8px; }
        .stat-label { color: #888; font-size: 0.9rem; }
        .stat-value { color: #fff; font-weight: 600; font-size: 1rem; font-family: 'Share Tech Mono', monospace; }
        
        .progress-bg { width: 100%; height: 6px; background: rgba(255,255,255,0.1); border-radius: 4px; overflow: hidden; margin-bottom: 20px; }
        .progress-fill { height: 100%; background: #27c93f; border-radius: 4px; transition: width 1s ease; }

        /* Leaderboard styling */
        .leaderboard-item {
          display: flex; align-items: center; gap: 12px; padding: 12px;
          border-radius: 12px; background: rgba(255,255,255,0.02);
          margin-bottom: 8px; transition: all 0.2s; border: 1px solid transparent;
        }
        .leaderboard-item:hover { background: rgba(255,122,0,0.08); border-color: rgba(255,122,0,0.2); transform: translateX(4px); }
        .lb-rank { color: #ff7a00; font-weight: bold; font-family: 'Share Tech Mono', monospace; width: 24px; text-align: center; }
        .lb-rank.top-3 { text-shadow: 0 0 10px #ff7a00; font-size: 1.1rem; }
        .lb-name { color: #ddd; font-size: 0.9rem; flex: 1; font-weight: 500; }
        .lb-elo { color: #fff; font-size: 0.95rem; font-weight: bold; font-family: 'Share Tech Mono', monospace; }

        /* Glassmorphism Card (Center Matchmaking) */
        .lobby-card {
          background: rgba(15, 8, 3, 0.6);
          backdrop-filter: blur(28px);
          -webkit-backdrop-filter: blur(28px);
          border: 1px solid rgba(255, 122, 0, 0.2);
          border-radius: 28px;
          box-shadow:
            0 0 0 1px rgba(255,255,255,0.04) inset,
            0 30px 60px rgba(0,0,0,0.6),
            0 0 60px rgba(255,122,0,0.08);
          display: flex; flex-direction: column; align-items: center; justify-content: center;
          padding: 60px 40px; text-align: center; min-height: 550px;
          position: relative; overflow: hidden;
        }

        .lobby-card::before {
            content: ''; position: absolute; inset: 0;
            background-image: 
                linear-gradient(rgba(255, 122, 0, 0.03) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255, 122, 0, 0.03) 1px, transparent 1px);
            background-size: 30px 30px;
            pointer-events: none; z-index: -1;
        }

        .mock-metrics {
            position: absolute; top: 20px; left: 24px; right: 24px;
            display: flex; justify-content: space-between;
            font-family: 'Share Tech Mono', monospace; font-size: 0.75rem; color: #ff7a00; opacity: 0.7;
        }

        .badge-terminal {
          display: inline-block; padding: 8px 16px; border: 1px solid rgba(255,122,0,0.4);
          border-radius: 20px; color: #ff7a00; font-size: 0.75rem; letter-spacing: 3px;
          font-family: 'Share Tech Mono', monospace; background: rgba(255,122,0,0.1); margin-bottom: 24px;
          box-shadow: 0 0 15px rgba(255,122,0,0.1);
        }

        .lobby-title { font-family: 'Outfit', sans-serif; font-size: 3.8rem; font-weight: 800; line-height: 1.1; letter-spacing: -1px; color: #fff; margin-bottom: 40px; }
        .text-orange { color: #ff7a00; text-shadow: 0 0 30px rgba(255,122,0,0.3); }

        /* Radar Animation */
        @keyframes radarPingOrange {
          0% { transform: scale(0.8); opacity: 0.8; }
          100% { transform: scale(3.5); opacity: 0; }
        }

        .radar-pulse { position: absolute; inset: 0; border-radius: 50%; border: 1px solid #ff7a00; }
        .radar-core {
          position: absolute; inset: 30px; border-radius: 50%;
          display: flex; flex-direction: column; align-items: center; justify-content: center;
          background: #0a0503; border: 2px solid rgba(255, 122, 0, 0.3);
          transition: all 0.4s ease; z-index: 10;
        }

        .radar-core.searching { box-shadow: 0 0 50px rgba(255,122,0,0.2); border-color: rgba(255, 122, 0, 0.6); }
        .radar-core.found {
          box-shadow: 0 0 80px rgba(255, 122, 0, 0.5), inset 0 0 40px rgba(255, 122, 0, 0.3);
          border: 2px solid #ff7a00; background: radial-gradient(circle, rgba(255,122,0,0.2) 0%, #0a0503 100%);
        }

        @keyframes fadeInOut { 0% { opacity: 0.3; } 100% { opacity: 1; } }

        /* Buttons */
        .lobby-btn {
          padding: 16px 48px; border-radius: 14px; font-weight: 700; font-size: 1rem;
          font-family: 'Inter', sans-serif; cursor: pointer;
          transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1); letter-spacing: 0.5px; border: none;
        }

        .btn-find { background: linear-gradient(135deg, #ff7a00 0%, #ff3d00 100%); color: #fff; box-shadow: 0 4px 24px rgba(255,122,0,0.4); }
        .btn-find:hover { transform: translateY(-2px); box-shadow: 0 10px 32px rgba(255,122,0,0.6); }
        .btn-cancel { background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.4); color: #fca5a5; }
        .btn-cancel:hover { background: rgba(239, 68, 68, 0.2); box-shadow: 0 4px 20px rgba(239, 68, 68, 0.2); transform: translateY(-2px); }

        @media (max-width: 1200px) {
          .lobby-content { grid-template-columns: 1fr; }
          .side-panel { display: none; }
        }
      `}</style>

      <div className="lobby-page">
        <canvas ref={canvasRef} className="lobby-canvas" />
        <div className="lobby-bg-gradient" />

        {/* Top Navbar */}
        <EngineerNav currentUser={currentUser} />

        {/* Main Content Grid */}
        <div className="lobby-content">
          
          {/* Left Panel: Career Stats */}
          <div className="side-panel">
            <h2 className="panel-title">Career Stats</h2>
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
              <div style={{ width: '60px', height: '60px', borderRadius: '16px', background: 'rgba(255,122,0,0.1)', border: '1px solid rgba(255,122,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '1.5rem', boxShadow: '0 0 20px rgba(255,122,0,0.1)' }}>
                🏆
              </div>
              <div>
                <div style={{ color: '#888', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '1px' }}>Current Elo</div>
                <div style={{ color: '#ff7a00', fontSize: '2rem', fontWeight: 800, fontFamily: 'Outfit', textShadow: '0 0 15px rgba(255,122,0,0.2)' }}>
                  {stats?.currentElo || 1200}
                </div>
              </div>
            </div>

            <div className="stat-row">
              <span className="stat-label">Matches Played</span>
              <span className="stat-value">{stats?.matchesPlayed || 0}</span>
            </div>
            
            <div className="stat-row" style={{ marginTop: '16px' }}>
              <span className="stat-label">Win Rate</span>
              <span className="stat-value" style={{ color: '#27c93f' }}>{winRate}%</span>
            </div>
            <div className="progress-bg">
                <div className="progress-fill" style={{ width: `${winRate}%` }} />
            </div>

            <div className="stat-row" style={{ marginTop: '16px' }}>
              <span className="stat-label">Current Streak</span>
              <span className="stat-value" style={{ color: '#ff7a00' }}>{stats?.currentWinStreak || 0} W</span>
            </div>
          </div>

          {/* Center Panel: Matchmaking Terminal */}
          <div className="lobby-card">
            <div className="mock-metrics">
                <span>LATENCY: 12ms</span>
                <span>NODE: IND-SOUTH-1</span>
            </div>

            <div style={{ position: 'relative', zIndex: 10 }}>
              <div className="badge-terminal">MATCHMAKING TERMINAL</div>
              <h1 className="lobby-title">
                Find Your <br /><span className="text-orange">Opponent</span>
              </h1>
            </div>

            <div style={{ position: 'relative', width: '280px', height: '280px', margin: '0 auto 50px' }}>
              {status === 'SEARCHING' && [0, 1, 2].map(i => (
                <div key={i} className="radar-pulse" style={{ animation: `radarPingOrange 2.4s ${i * 0.8}s infinite linear` }} />
              ))}
              
              <div className={`radar-core ${status === 'SEARCHING' ? 'searching' : status === 'FOUND' ? 'found' : ''}`}>
                {status === 'IDLE' && (
                  <>
                    <div style={{ fontSize: '3rem', marginBottom: '8px', opacity: 0.8 }}>⚔️</div>
                    <span style={{ color: '#888', fontSize: '0.8rem', letterSpacing: '4px', fontFamily: "'Share Tech Mono', monospace" }}>STANDBY</span>
                  </>
                )}
                {status === 'SEARCHING' && (
                  <>
                    <span style={{ color: '#ff7a00', fontSize: '3.5rem', fontWeight: 'bold', fontFamily: "'Share Tech Mono', monospace", marginBottom: '4px', textShadow: '0 0 20px rgba(255,122,0,0.5)' }}>
                      {formatTime(queueTime)}
                    </span>
                    <span style={{ color: '#aaa', fontSize: '0.8rem', letterSpacing: '4px', fontFamily: "'Share Tech Mono', monospace", animation: 'fadeInOut 1s infinite alternate' }}>
                      SEARCHING
                    </span>
                  </>
                )}
                {status === 'FOUND' && (
                  <>
                    <div style={{ fontSize: '3.5rem', marginBottom: '8px', textShadow: '0 0 30px rgba(255,122,0,0.8)' }}>🎯</div>
                    <span style={{ color: '#ff7a00', fontSize: '1rem', letterSpacing: '4px', fontFamily: "'Share Tech Mono', monospace", fontWeight: 'bold' }}>
                      LOCKED
                    </span>
                  </>
                )}
              </div>
            </div>

            <p style={{ color: status === 'FOUND' ? '#ff7a00' : '#888', fontSize: '0.95rem', fontFamily: "'Share Tech Mono', monospace", letterSpacing: '1px', height: '24px', transition: 'color 0.3s' }}>
              {statusMsg || 'Ready to join the queue.'}
            </p>

            <div style={{ marginTop: '40px', position: 'relative', zIndex: 10 }}>
              {status === 'IDLE' && (
                <button onClick={handleFind} className="lobby-btn btn-find">Enter Matchmaking →</button>
              )}
              {status === 'SEARCHING' && (
                <button onClick={handleCancel} className="lobby-btn btn-cancel">Abort Search</button>
              )}
            </div>
          </div>

          {/* Right Panel: Global Leaderboard */}
          <div className="side-panel">
            <h2 className="panel-title">Top Players</h2>
            
            {leaderboard.length === 0 ? (
                <div style={{ color: '#666', textAlign: 'center', marginTop: '40px', fontFamily: "'Share Tech Mono', monospace" }}>Loading ranks...</div>
            ) : (
                leaderboard.slice(0, 5).map((player, index) => (
                    <div key={player.userId || player.id || player.username} className="leaderboard-item">
                        <span className={`lb-rank ${index < 3 ? 'top-3' : ''}`}>{index + 1}</span>
                        <span className="lb-name">{player.username.split('@')[0]}</span>
                        <span className="lb-elo">{player.currentElo}</span>
                    </div>
                ))
            )}

            <div style={{ marginTop: '24px', textAlign: 'center' }}>
              <button style={{ background: 'none', border: 'none', color: '#ff7a00', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', transition: 'opacity 0.2s' }}>
                View Full Leaderboard →
              </button>
            </div>
          </div>

        </div>
      </div>
    </>
  );
}