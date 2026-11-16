import { useState, useEffect, useRef } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

const GATEWAY_URL = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';
const ARENA_WS_URL = (import.meta.env.VITE_ARENA_WS_URL || 'ws://localhost:8084').replace(/^ws/, 'http');

export default function Arena({ currentUser, getToken }) {
  const { matchId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const problemId = location.state?.problemId;

  const [problem, setProblem] = useState(null);
  const [code, setCode] = useState('');
  const [logs, setLogs] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [matchFinished, setMatchFinished] = useState(false);
  const [result, setResult] = useState(null);
  const [showResult, setShowResult] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [isLeaving, setIsLeaving] = useState(false);

  // Competitive progress
  const [myProgress, setMyProgress] = useState({ passed: 0, total: 0 });
  const [opponentProgress, setOpponentProgress] = useState({ passed: 0, total: 0 });

  const logsEndRef = useRef(null);
  const canvasRef = useRef(null);
  const timerRef = useRef(null);

  const addLog = (sender, message, type = 'info') => {
    const ts = new Date().toLocaleTimeString('en-US', { hour12: false });
    setLogs(prev => [...prev, { ts, sender, message, type }]);
  };

  // Match timer
  useEffect(() => {
    timerRef.current = setInterval(() => setElapsed(t => t + 1), 1000);
    return () => clearInterval(timerRef.current);
  }, []);

  useEffect(() => {
    if (matchFinished) clearInterval(timerRef.current);
  }, [matchFinished]);

  const formatTime = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;

  useEffect(() => {
    if (!problemId) { navigate('/lobby'); return; }

    axios.get(`${GATEWAY_URL}/api/problems/${problemId}`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    }).then(res => {
      setProblem(res.data);
      setCode(res.data.startingCodeJava || '// Write your solution here\nclass Solution {\n\n}');
      addLog('SYSTEM', `Problem loaded: ${res.data.title}`, 'system');
      // Initialize progress totals from problem data
      const totalTests = res.data.testCases?.length || 0;
      setMyProgress({ passed: 0, total: totalTests });
      setOpponentProgress({ passed: 0, total: totalTests });
    }).catch(() => addLog('ERROR', 'Failed to load problem.', 'error'));

    const client = new Client({
      webSocketFactory: () => new SockJS(`${ARENA_WS_URL}/ws-arena`),
      onConnect: () => {
        addLog('NETWORK', 'Connected to match server.', 'network');
        client.subscribe(`/topic/match/${matchId}`, (msg) => {
          const event = JSON.parse(msg.body);

          // Update progress bars for both players
          const progress = { passed: event.passedCount || 0, total: event.totalCount || 0 };

          if (event.playerId === currentUser) {
            // My submission result
            setMyProgress(progress);

            if (event.status === 'SUCCESS') {
              addLog('SANDBOX', `✓ ALL TESTS PASSED — YOU WIN (${progress.passed}/${progress.total})`, 'success');
              setMatchFinished(true);
              setResult({ isWinner: true, opponentId: null });
              setTimeout(() => setShowResult(true), 1500);
            } else if (event.status === 'LEFT') {
              // I left — shouldn't happen since we navigate away, but handle it
              addLog('SYSTEM', 'You left the match.', 'warning');
            } else {
              addLog('SANDBOX', `✗ ${event.status} (${progress.passed}/${progress.total} passed)`, 'error');
            }
          } else {
            // Opponent's submission result
            setOpponentProgress(progress);

            if (event.status === 'SUCCESS') {
              addLog('ARENA', `Opponent passed all tests — MATCH OVER (${progress.passed}/${progress.total})`, 'warning');
              setMatchFinished(true);
              setResult({ isWinner: false, opponentId: event.playerId });
              setTimeout(() => setShowResult(true), 1500);
            } else if (event.status === 'LEFT') {
              addLog('ARENA', 'Opponent left the match — YOU WIN!', 'success');
              setMatchFinished(true);
              setResult({ isWinner: true, opponentId: event.playerId, opponentLeft: true });
              setTimeout(() => setShowResult(true), 1000);
            } else {
              addLog('ARENA', `Opponent submission: ${progress.passed}/${progress.total} tests passed`, 'dim');
            }
          }
        });
      },
      onStompError: () => addLog('ERROR', 'WebSocket connection error.', 'error'),
    });

    client.activate();
    return () => client.deactivate();
  }, [matchId, problemId, currentUser, navigate]);

  useEffect(() => { logsEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [logs]);

  // Subtle particle background
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
      r: Math.random() * 1.2 + 0.3,
      dx: (Math.random() - 0.5) * 0.3,
      dy: (Math.random() - 0.5) * 0.3,
      alpha: Math.random() * 0.15 + 0.03,
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

  const handleSubmit = async () => {
    if (isSubmitting || matchFinished) return;
    setIsSubmitting(true);
    addLog('CLIENT', 'Dispatching to sandbox...', 'warning');
    try {
      await axios.post(
        `${GATEWAY_URL}/api/match/${matchId}/submit`,
        { code, language: 'java' },
        { headers: { Authorization: `Bearer ${getToken()}` } }
      );
    } catch (err) {
      addLog('ERROR', `Submission failed: ${err.message}`, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLeaveMatch = async () => {
    if (isLeaving || matchFinished) return;
    setIsLeaving(true);
    try {
      await axios.post(
        `${GATEWAY_URL}/api/match/${matchId}/leave`,
        {},
        { headers: { Authorization: `Bearer ${getToken()}` } }
      );
      addLog('SYSTEM', 'You left the match.', 'warning');
      navigate('/lobby');
    } catch (err) {
      addLog('ERROR', `Failed to leave: ${err.message}`, 'error');
      setIsLeaving(false);
      setShowLeaveConfirm(false);
    }
  };

  const logColors = {
    system: '#ff7a00',
    network: '#a78bfa',
    success: '#27c93f',
    error: '#ef4444',
    warning: '#ffbd2e',
    dim: '#555',
    info: '#888',
  };

  // Progress bar component
  const ProgressBar = ({ label, passed, total, isOpponent }) => {
    const pct = total > 0 ? (passed / total) * 100 : 0;
    const isComplete = total > 0 && passed === total;
    const barColor = isComplete
      ? '#27c93f'
      : isOpponent
        ? 'rgba(239, 68, 68, 0.8)'
        : '#ff7a00';

    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: '180px' }}>
        <span style={{
          color: isOpponent ? '#888' : '#ccc',
          fontSize: '0.68rem',
          letterSpacing: '1.5px',
          fontFamily: "'Share Tech Mono', monospace",
          minWidth: '55px',
          textAlign: 'right',
        }}>
          {label}
        </span>
        <div style={{
          flex: 1,
          height: '6px',
          background: 'rgba(255,255,255,0.06)',
          borderRadius: '3px',
          overflow: 'hidden',
          minWidth: '80px',
          position: 'relative',
        }}>
          <div style={{
            width: `${pct}%`,
            height: '100%',
            background: barColor,
            borderRadius: '3px',
            transition: 'width 0.6s cubic-bezier(0.34, 1.56, 0.64, 1)',
            boxShadow: pct > 0 ? `0 0 8px ${barColor}` : 'none',
          }} />
        </div>
        <span style={{
          color: isComplete ? '#27c93f' : (pct > 0 ? barColor : '#444'),
          fontSize: '0.72rem',
          fontFamily: "'Share Tech Mono', monospace",
          fontWeight: isComplete ? 'bold' : 'normal',
          minWidth: '30px',
          textShadow: isComplete ? '0 0 8px #27c93f' : 'none',
        }}>
          {passed}/{total}
        </span>
      </div>
    );
  };

  if (!problem) return (
    <>
      <style>{`
        @keyframes arena-spin { 100% { transform: rotate(360deg); } }
        @keyframes arena-pulse { 0%, 100% { opacity: 0.4; } 50% { opacity: 1; } }
      `}</style>
      <div style={{
        height: '100vh', display: 'flex', alignItems: 'center',
        justifyContent: 'center', background: '#0a0503',
        color: '#888', fontFamily: "'Share Tech Mono', monospace",
        letterSpacing: '3px', fontSize: '0.9rem',
      }}>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '20px' }}>
          <div style={{
            width: '48px', height: '48px',
            border: '3px solid rgba(255,122,0,0.1)',
            borderTopColor: '#ff7a00',
            borderRadius: '50%',
            animation: 'arena-spin 1s linear infinite',
          }} />
          <span style={{ animation: 'arena-pulse 2s ease infinite' }}>INITIALIZING ARENA...</span>
        </div>
      </div>
    </>
  );

  const diffColor = { EASY: '#27c93f', MEDIUM: '#ffbd2e', HARD: '#ef4444' }[problem.difficulty] || '#888';

  return (
    <>
      <style>{`
        @keyframes arena-spin { 100% { transform: rotate(360deg); } }
        @keyframes arena-pulse-dot { 0%, 100% { opacity: 0.3; box-shadow: 0 0 4px rgba(255,122,0,0.2); } 50% { opacity: 1; box-shadow: 0 0 12px rgba(255,122,0,0.6); } }
        @keyframes arena-slide-up { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
        @keyframes arena-fade-in { from { opacity: 0; } to { opacity: 1; } }
        @keyframes arena-glow-pulse { 0%, 100% { box-shadow: 0 0 30px rgba(255,122,0,0.1); } 50% { box-shadow: 0 0 60px rgba(255,122,0,0.25); } }

        .arena-container {
          display: flex;
          height: 100vh;
          background: #0a0503;
          overflow: hidden;
          position: relative;
          font-family: 'Inter', sans-serif;
        }

        .arena-canvas {
          position: fixed;
          inset: 0;
          width: 100vw;
          height: 100vh;
          pointer-events: none;
          z-index: 0;
        }

        .arena-bg-gradient {
          position: fixed;
          inset: 0;
          background: radial-gradient(ellipse at top left, rgba(255, 122, 0, 0.04) 0%, transparent 50%),
                      radial-gradient(ellipse at bottom right, rgba(255, 61, 0, 0.03) 0%, transparent 50%);
          pointer-events: none;
          z-index: 0;
        }

        /* Problem Panel */
        .arena-problem-panel {
          width: 35%;
          min-width: 320px;
          max-width: 520px;
          display: flex;
          flex-direction: column;
          background: rgba(15, 8, 3, 0.6);
          backdrop-filter: blur(16px);
          -webkit-backdrop-filter: blur(16px);
          border-right: 1px solid rgba(255, 122, 0, 0.1);
          z-index: 10;
          overflow: hidden;
        }

        .arena-problem-header {
          padding: 20px 24px;
          border-bottom: 1px solid rgba(255, 122, 0, 0.1);
          background: rgba(10, 5, 3, 0.8);
        }

        .arena-problem-title {
          margin: 0;
          color: #fff;
          font-size: 1.35rem;
          font-weight: 700;
          font-family: 'Outfit', sans-serif;
          line-height: 1.3;
          letter-spacing: -0.3px;
        }

        .arena-difficulty-badge {
          display: inline-block;
          padding: 4px 12px;
          border-radius: 20px;
          font-size: 0.7rem;
          letter-spacing: 2px;
          font-family: 'Share Tech Mono', monospace;
          font-weight: bold;
          border: 1px solid;
          white-space: nowrap;
        }

        .arena-match-id {
          color: #555;
          font-size: 0.72rem;
          letter-spacing: 2px;
          font-family: 'Share Tech Mono', monospace;
        }

        .arena-problem-body {
          flex: 1;
          overflow-y: auto;
          padding: 24px;
        }

        .arena-problem-description {
          color: #b0a99f;
          font-size: 0.95rem;
          line-height: 1.85;
          margin-bottom: 32px;
        }

        .arena-test-case {
          background: rgba(255, 122, 0, 0.03);
          border: 1px solid rgba(255, 122, 0, 0.1);
          border-radius: 12px;
          padding: 16px;
          margin-bottom: 14px;
          font-family: 'Share Tech Mono', monospace;
          font-size: 0.85rem;
          transition: border-color 0.3s;
        }
        .arena-test-case:hover {
          border-color: rgba(255, 122, 0, 0.3);
        }

        .arena-test-label {
          color: #ff7a00;
          font-size: 0.68rem;
          letter-spacing: 2.5px;
          margin-bottom: 12px;
          font-weight: bold;
          opacity: 0.8;
        }

        .arena-test-key { color: #666; margin-right: 8px; }
        .arena-test-value { color: #ddd; }
        .arena-test-expected { color: #ff7a00; text-shadow: 0 0 8px rgba(255,122,0,0.15); }

        /* Right pane: Editor + Terminal */
        .arena-editor-pane {
          flex: 1;
          display: flex;
          flex-direction: column;
          min-width: 0;
          overflow: hidden;
          z-index: 10;
        }

        /* Toolbar */
        .arena-toolbar {
          padding: 10px 24px;
          background: rgba(10, 5, 3, 0.85);
          backdrop-filter: blur(12px);
          -webkit-backdrop-filter: blur(12px);
          border-bottom: 1px solid rgba(255, 122, 0, 0.1);
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 16px;
        }

        .arena-toolbar-left {
          display: flex;
          align-items: center;
          gap: 16px;
        }

        .arena-toolbar-center {
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 4px;
          max-width: 380px;
        }

        .arena-toolbar-right {
          display: flex;
          align-items: center;
          gap: 10px;
        }

        .arena-lang-badge {
          color: #888;
          font-size: 0.72rem;
          letter-spacing: 2px;
          font-family: 'Share Tech Mono', monospace;
          padding: 5px 12px;
          border-radius: 8px;
          background: rgba(255, 122, 0, 0.06);
          border: 1px solid rgba(255, 122, 0, 0.12);
          white-space: nowrap;
        }

        .arena-timer {
          color: #ff7a00;
          font-size: 1rem;
          font-weight: bold;
          font-family: 'Share Tech Mono', monospace;
          letter-spacing: 2px;
          text-shadow: 0 0 12px rgba(255,122,0,0.3);
          display: flex;
          align-items: center;
          gap: 6px;
          white-space: nowrap;
        }

        .arena-timer-dot {
          width: 7px;
          height: 7px;
          border-radius: 50%;
          background: #ff7a00;
          animation: arena-pulse-dot 2s ease infinite;
          flex-shrink: 0;
        }

        .arena-status-label {
          font-size: 0.75rem;
          letter-spacing: 2px;
          font-family: 'Share Tech Mono', monospace;
          font-weight: bold;
          white-space: nowrap;
        }

        .arena-submit-btn {
          background: linear-gradient(135deg, #ff7a00 0%, #ff3d00 100%);
          border: none;
          color: #fff;
          padding: 9px 22px;
          border-radius: 10px;
          font-weight: 700;
          cursor: pointer;
          font-size: 0.82rem;
          letter-spacing: 1.5px;
          font-family: 'Inter', sans-serif;
          transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
          box-shadow: 0 4px 20px rgba(255,122,0,0.3);
          white-space: nowrap;
        }
        .arena-submit-btn:hover:not(:disabled) {
          transform: translateY(-2px);
          box-shadow: 0 8px 30px rgba(255,122,0,0.5);
        }
        .arena-submit-btn:disabled {
          background: rgba(255,255,255,0.05);
          color: #555;
          border: 1px solid rgba(255,255,255,0.08);
          box-shadow: none;
          cursor: not-allowed;
        }

        .arena-leave-btn {
          background: none;
          border: 1px solid rgba(239, 68, 68, 0.25);
          color: #888;
          padding: 9px 16px;
          border-radius: 10px;
          font-size: 0.78rem;
          letter-spacing: 1px;
          font-family: 'Inter', sans-serif;
          cursor: pointer;
          transition: all 0.3s;
          white-space: nowrap;
          font-weight: 600;
        }
        .arena-leave-btn:hover {
          background: rgba(239, 68, 68, 0.1);
          border-color: rgba(239, 68, 68, 0.5);
          color: #fca5a5;
        }

        /* Battle terminal */
        .arena-terminal {
          flex: 4;
          min-height: 0;
          display: flex;
          flex-direction: column;
          background: rgba(5, 2, 1, 0.95);
          border-top: 1px solid rgba(255, 122, 0, 0.1);
        }

        .arena-terminal-header {
          padding: 10px 20px;
          border-bottom: 1px solid rgba(255, 122, 0, 0.08);
          display: flex;
          align-items: center;
          gap: 10px;
          background: rgba(10, 5, 3, 0.9);
        }

        .arena-terminal-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          transition: all 0.4s;
        }

        .arena-terminal-title {
          color: #888;
          font-size: 0.73rem;
          letter-spacing: 3px;
          font-family: 'Share Tech Mono', monospace;
          font-weight: bold;
        }

        .arena-terminal-count {
          margin-left: auto;
          color: #444;
          font-size: 0.7rem;
          font-family: 'Share Tech Mono', monospace;
        }

        .arena-terminal-body {
          flex: 1;
          overflow-y: auto;
          padding: 16px 20px;
          font-family: 'Share Tech Mono', monospace;
          font-size: 0.84rem;
        }

        .arena-log-entry {
          margin-bottom: 6px;
          display: flex;
          gap: 12px;
          line-height: 1.6;
          animation: arena-slide-up 0.3s ease forwards;
        }

        .arena-log-ts { color: #333; flex-shrink: 0; }
        .arena-log-sender { color: #666; min-width: 72px; flex-shrink: 0; font-size: 0.78rem; }
        .arena-log-message { white-space: pre-wrap; word-break: break-word; }

        /* Leave confirm modal */
        .arena-leave-overlay {
          position: fixed;
          inset: 0;
          background: rgba(5, 2, 1, 0.7);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 999;
          backdrop-filter: blur(8px);
          -webkit-backdrop-filter: blur(8px);
          animation: arena-fade-in 0.2s ease;
        }

        .arena-leave-card {
          background: rgba(15, 8, 3, 0.95);
          border: 1px solid rgba(239, 68, 68, 0.3);
          border-radius: 20px;
          padding: 40px;
          text-align: center;
          max-width: 400px;
          width: 90%;
          animation: arena-slide-up 0.3s ease;
          box-shadow: 0 20px 60px rgba(0,0,0,0.5), inset 0 0 20px rgba(239,68,68,0.03);
        }

        /* Result Modal */
        .arena-modal-overlay {
          position: fixed;
          inset: 0;
          background: rgba(5, 2, 1, 0.88);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 1000;
          backdrop-filter: blur(16px);
          -webkit-backdrop-filter: blur(16px);
          animation: arena-fade-in 0.4s ease;
        }

        .arena-modal-card {
          background: rgba(15, 8, 3, 0.95);
          border-radius: 24px;
          padding: 56px 48px;
          text-align: center;
          max-width: 480px;
          width: 90%;
          animation: arena-slide-up 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
          position: relative;
          overflow: hidden;
        }

        .arena-modal-card::before {
          content: '';
          position: absolute;
          inset: 0;
          background-image:
            linear-gradient(rgba(255, 122, 0, 0.02) 1px, transparent 1px),
            linear-gradient(90deg, rgba(255, 122, 0, 0.02) 1px, transparent 1px);
          background-size: 24px 24px;
          pointer-events: none;
        }

        .arena-modal-emoji { font-size: 4rem; margin-bottom: 20px; }

        .arena-modal-title {
          font-size: 3.2rem;
          font-weight: 800;
          letter-spacing: 6px;
          font-family: 'Outfit', sans-serif;
          margin-bottom: 12px;
          position: relative;
          z-index: 1;
        }

        .arena-modal-subtitle {
          color: #888;
          margin-bottom: 40px;
          font-size: 0.95rem;
          font-family: 'Share Tech Mono', monospace;
          line-height: 1.6;
          position: relative;
          z-index: 1;
        }

        .arena-modal-elo-box {
          background: rgba(0,0,0,0.4);
          border: 1px solid rgba(255,255,255,0.06);
          border-radius: 14px;
          padding: 24px;
          margin-bottom: 32px;
          position: relative;
          z-index: 1;
        }

        .arena-modal-elo-label {
          color: #555;
          font-size: 0.72rem;
          letter-spacing: 3px;
          font-family: 'Share Tech Mono', monospace;
          margin-bottom: 10px;
        }

        .arena-modal-elo-value {
          font-size: 2.8rem;
          font-weight: bold;
          font-family: 'Outfit', sans-serif;
        }

        .arena-modal-elo-unit {
          font-size: 1.1rem;
          color: #888;
          margin-left: 8px;
        }

        .arena-modal-btn {
          width: 100%;
          padding: 16px;
          border-radius: 14px;
          background: linear-gradient(135deg, #ff7a00 0%, #ff3d00 100%);
          color: #fff;
          border: none;
          font-weight: 700;
          font-size: 0.95rem;
          letter-spacing: 1px;
          cursor: pointer;
          transition: all 0.3s;
          box-shadow: 0 4px 24px rgba(255,122,0,0.3);
          position: relative;
          z-index: 1;
          font-family: 'Inter', sans-serif;
        }
        .arena-modal-btn:hover {
          transform: translateY(-2px);
          box-shadow: 0 8px 32px rgba(255,122,0,0.5);
        }

        /* Scrollbar theming */
        .arena-problem-body::-webkit-scrollbar,
        .arena-terminal-body::-webkit-scrollbar { width: 5px; }
        .arena-problem-body::-webkit-scrollbar-track,
        .arena-terminal-body::-webkit-scrollbar-track { background: transparent; }
        .arena-problem-body::-webkit-scrollbar-thumb,
        .arena-terminal-body::-webkit-scrollbar-thumb { background: rgba(255,122,0,0.15); border-radius: 4px; }
        .arena-problem-body::-webkit-scrollbar-thumb:hover,
        .arena-terminal-body::-webkit-scrollbar-thumb:hover { background: rgba(255,122,0,0.3); }
      `}</style>

      <canvas ref={canvasRef} className="arena-canvas" />
      <div className="arena-bg-gradient" />

      <div className="arena-container">

        {/* ═══════════ LEFT PANE — Problem Panel ═══════════ */}
        <div className="arena-problem-panel">
          {/* Problem Header */}
          <div className="arena-problem-header">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '12px', marginBottom: '12px' }}>
              <h2 className="arena-problem-title">{problem.title}</h2>
              <span
                className="arena-difficulty-badge"
                style={{ color: diffColor, borderColor: diffColor, background: `${diffColor}12` }}
              >
                {problem.difficulty}
              </span>
            </div>
            <span className="arena-match-id">
              MATCH: {matchId?.slice(-8).toUpperCase()}
            </span>
          </div>

          {/* Scrollable content */}
          <div className="arena-problem-body">
            <p className="arena-problem-description">
              {problem.description}
            </p>

            {Array.isArray(problem.testCases) &&
              problem.testCases
                .filter(tc => !tc.hidden)
                .slice(0, 3)
                .map((tc, i) => (
                  <div key={i} className="arena-test-case">
                    <div className="arena-test-label">TEST CASE {i + 1}</div>
                    <div style={{ marginBottom: '6px' }}>
                      <span className="arena-test-key">INPUT:</span>
                      <span className="arena-test-value">{Array.isArray(tc.inputs) ? tc.inputs.join(', ') : String(tc.inputs || '')}</span>
                    </div>
                    <div>
                      <span className="arena-test-key">EXPECTED:</span>
                      <span className="arena-test-expected">{String(tc.expectedOutput || '')}</span>
                    </div>
                  </div>
                ))}
          </div>
        </div>

        {/* ═══════════ RIGHT PANE — Editor + Battle Log ═══════════ */}
        <div className="arena-editor-pane">

          {/* ── Toolbar with Progress Bars ── */}
          <div className="arena-toolbar">
            <div className="arena-toolbar-left">
              <span className="arena-lang-badge">JAVA 17</span>
              <div className="arena-timer">
                <div className="arena-timer-dot" style={
                  matchFinished
                    ? {
                        background: result?.isWinner ? '#27c93f' : '#ef4444',
                        boxShadow: `0 0 12px ${result?.isWinner ? '#27c93f' : '#ef4444'}`,
                        animation: 'none',
                      }
                    : {}
                } />
                <span>{formatTime(elapsed)}</span>
              </div>

              {matchFinished && (
                <span
                  className="arena-status-label"
                  style={{
                    color: result?.isWinner ? '#27c93f' : '#ef4444',
                    textShadow: `0 0 10px ${result?.isWinner ? '#27c93f' : '#ef4444'}`,
                  }}
                >
                  {result?.isWinner ? '● VICTORY' : '● DEFEAT'}
                </span>
              )}
            </div>

            {/* ── Competitive Progress Bars ── */}
            <div className="arena-toolbar-center">
              <ProgressBar label="YOU" passed={myProgress.passed} total={myProgress.total} isOpponent={false} />
              <ProgressBar label="ENEMY" passed={opponentProgress.passed} total={opponentProgress.total} isOpponent={true} />
            </div>

            <div className="arena-toolbar-right">
              {!matchFinished && (
                <button
                  onClick={() => setShowLeaveConfirm(true)}
                  className="arena-leave-btn"
                >
                  LEAVE
                </button>
              )}
              <button
                onClick={handleSubmit}
                disabled={isSubmitting || matchFinished}
                className="arena-submit-btn"
              >
                {isSubmitting ? 'EXECUTING...' : 'DEPLOY →'}
              </button>
            </div>
          </div>

          {/* ── Code Editor ── */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <div style={{ flex: 6, minHeight: 0 }}>
              <Editor
                height="100%"
                defaultLanguage="java"
                theme="vs-dark"
                value={code}
                onChange={v => setCode(v)}
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  lineHeight: 24,
                  fontFamily: "'Share Tech Mono', 'Fira Code', monospace",
                  padding: { top: 20, bottom: 20 },
                  scrollBeyondLastLine: false,
                  renderLineHighlight: 'all',
                  overviewRulerLanes: 0,
                  hideCursorInOverviewRuler: true,
                  scrollbar: { verticalScrollbarSize: 6, horizontalScrollbarSize: 6 },
                  cursorBlinking: 'smooth',
                  cursorSmoothCaretAnimation: 'on',
                  smoothScrolling: true,
                }}
              />
            </div>

            {/* ── Battle Terminal ── */}
            <div className="arena-terminal">
              <div className="arena-terminal-header">
                <div
                  className="arena-terminal-dot"
                  style={{
                    background: matchFinished
                      ? (result?.isWinner ? '#27c93f' : '#ef4444')
                      : '#ff7a00',
                    boxShadow: `0 0 10px ${matchFinished
                      ? (result?.isWinner ? '#27c93f' : '#ef4444')
                      : '#ff7a00'
                    }`,
                  }}
                />
                <span className="arena-terminal-title">BATTLE TERMINAL</span>
                <span className="arena-terminal-count">{logs.length} EVENTS</span>
              </div>

              <div className="arena-terminal-body">
                {logs.length === 0 && (
                  <div style={{ color: '#71717a', fontStyle: 'italic' }}>Awaiting system events...</div>
                )}
                {logs.map((log, i) => (
                  <div key={i} className="arena-log-entry">
                    <span className="arena-log-ts">[{log.ts}]</span>
                    <span className="arena-log-sender">{log.sender}</span>
                    <span
                      className="arena-log-message"
                      style={{
                        color: logColors[log.type] || '#888',
                        fontWeight: log.sender === 'SANDBOX' ? 'bold' : 'normal',
                      }}
                    >
                      {log.message}
                    </span>
                  </div>
                ))}
                <div ref={logsEndRef} />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ═══════════ LEAVE CONFIRMATION MODAL ═══════════ */}
      {showLeaveConfirm && (
        <div className="arena-leave-overlay" onClick={() => setShowLeaveConfirm(false)}>
          <div className="arena-leave-card" onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>⚠️</div>
            <h2 style={{
              color: '#fff',
              fontSize: '1.4rem',
              fontFamily: "'Outfit', sans-serif",
              fontWeight: 700,
              marginBottom: '12px',
            }}>
              Leave Match?
            </h2>
            <p style={{
              color: '#888',
              fontSize: '0.9rem',
              lineHeight: 1.6,
              marginBottom: '32px',
              fontFamily: "'Share Tech Mono', monospace",
            }}>
              You will forfeit this match.<br />
              Your opponent wins and Elo will be adjusted.
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button
                onClick={() => setShowLeaveConfirm(false)}
                style={{
                  flex: 1,
                  padding: '12px',
                  borderRadius: '10px',
                  background: 'rgba(255,255,255,0.05)',
                  color: '#ccc',
                  border: '1px solid rgba(255,255,255,0.1)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  transition: 'all 0.2s',
                  fontFamily: "'Inter', sans-serif",
                }}
              >
                Stay in Match
              </button>
              <button
                onClick={handleLeaveMatch}
                disabled={isLeaving}
                style={{
                  flex: 1,
                  padding: '12px',
                  borderRadius: '10px',
                  background: isLeaving ? 'rgba(239,68,68,0.1)' : 'rgba(239, 68, 68, 0.15)',
                  color: '#fca5a5',
                  border: '1px solid rgba(239, 68, 68, 0.4)',
                  fontWeight: 700,
                  fontSize: '0.85rem',
                  cursor: isLeaving ? 'not-allowed' : 'pointer',
                  transition: 'all 0.2s',
                  fontFamily: "'Inter', sans-serif",
                }}
              >
                {isLeaving ? 'Leaving...' : 'Forfeit & Leave'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ═══════════ RESULT MODAL ═══════════ */}
      {showResult && result && (
        <div className="arena-modal-overlay">
          <div
            className="arena-modal-card"
            style={{
              border: `1px solid ${result.isWinner ? 'rgba(255,122,0,0.4)' : 'rgba(239,68,68,0.4)'}`,
              boxShadow: result.isWinner
                ? '0 30px 80px rgba(255,122,0,0.2), inset 0 0 30px rgba(255,122,0,0.05), 0 0 0 1px rgba(255,122,0,0.1)'
                : '0 30px 80px rgba(239,68,68,0.15), inset 0 0 30px rgba(239,68,68,0.05), 0 0 0 1px rgba(239,68,68,0.1)',
            }}
          >
            <div className="arena-modal-emoji" style={{
              filter: `drop-shadow(0 0 24px ${result.isWinner ? 'rgba(255,122,0,0.6)' : 'rgba(239,68,68,0.6)'})`,
            }}>
              {result.isWinner ? '🏆' : '💀'}
            </div>
            <h1
              className="arena-modal-title"
              style={{
                color: result.isWinner ? '#ff7a00' : '#ef4444',
                textShadow: `0 0 40px ${result.isWinner ? 'rgba(255,122,0,0.4)' : 'rgba(239,68,68,0.4)'}`,
              }}
            >
              {result.isWinner ? 'VICTORY' : 'DEFEAT'}
            </h1>
            <p className="arena-modal-subtitle">
              {result.opponentLeft
                ? 'Your opponent fled the battlefield. Victory by forfeit.'
                : result.isWinner
                  ? 'Target neutralized. You outcoded your opponent.'
                  : `System compromised. ${result.opponentId} solved it faster.`}
            </p>

            <div className="arena-modal-elo-box">
              <div className="arena-modal-elo-label">RATING DELTA</div>
              <div
                className="arena-modal-elo-value"
                style={{ color: result.isWinner ? '#ff7a00' : '#ef4444' }}
              >
                {result.isWinner ? '+15' : '−15'}
                <span className="arena-modal-elo-unit">ELO</span>
              </div>
            </div>

            <button onClick={() => navigate('/dashboard')} className="arena-modal-btn">
              PROCEED TO DASHBOARD →
            </button>
          </div>
        </div>
      )}
    </>
  );
}
