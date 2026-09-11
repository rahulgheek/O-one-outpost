import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const SERVICES = [
  { key: 'gateway-service', label: 'Gateway', icon: '🛡️', color: '#6366f1' },
  { key: 'auth-service', label: 'Auth', icon: '🔐', color: '#8b5cf6' },
  { key: 'arena-service', label: 'Arena', icon: '⚔️', color: '#ff7a00' },
  { key: 'assessment-service', label: 'Assessment', icon: '📋', color: '#3b82f6' },
  { key: 'code-sandbox', label: 'Sandbox', icon: '📦', color: '#10b981' },
  { key: 'analytics-service', label: 'Analytics', icon: '📊', color: '#f59e0b' },
];

const NAV_ITEMS = [
  { id: 'monitor', label: 'Monitor', icon: '⬡' },
  { id: 'library', label: 'Problem Library', icon: '◈' },
  { id: 'daily', label: 'Daily Scheduler', icon: '◷' },
  { id: 'inject', label: 'Inject Problem', icon: '⊕' },
  { id: 'operators', label: 'Operators', icon: '◉' },
];

export default function AdminDashboard({ getToken }) {
  const [mode, setMode] = useState('create');
  const [availableProblems, setAvailableProblems] = useState([]);
  const [isDataLoaded, setIsDataLoaded] = useState(false);
  const [status, setStatus] = useState('');
  const [analytics, setAnalytics] = useState(null);
  const [systemLogs, setSystemLogs] = useState([]);
  const [logServiceFilter, setLogServiceFilter] = useState('Frontend Events');
  const [backendLogs, setBackendLogs] = useState([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const prevHealthRef = useRef({});
  const searchTimeoutRef = useRef(null);
  const canvasRef = useRef(null);
  const navigate = useNavigate();
  const [currentUser] = useState(localStorage.getItem('algo_arena_user'));
  const [clock, setClock] = useState(new Date());

  const [activeTab, setActiveTab] = useState('monitor');

  const [problemLibrary, setProblemLibrary] = useState([]);
  const [libraryLoading, setLibraryLoading] = useState(false);
  const [librarySearch, setLibrarySearch] = useState('');

  const [dailySchedules, setDailySchedules] = useState([]);
  const [dailyForm, setDailyForm] = useState({ problemId: '', activatesAt: '', label: '' });
  const [dailyStatus, setDailyStatus] = useState('');
  const [dailyLoading, setDailyLoading] = useState(false);

  const defaultProblemState = {
    title: '', difficulty: 'EASY', description: '', timeLimitMs: 5000, memoryLimitKb: 256000,
    methodName: '', returnType: 'int', parameterTypes: ['int[]', 'int'],
    startingCodeJava: 'class Solution {\n    public int solve(int[] nums, int target) {\n        return 0;\n    }\n}',
    testCases: [{ inputs: ['[2,7,11,15]', '9'], expectedOutput: '[0,1]', hidden: false }]
  };
  const [problem, setProblem] = useState(defaultProblemState);
  const [newAdmin, setNewAdmin] = useState({ email: '', password: '' });
  const [adminStatus, setAdminStatus] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(null);

  // Live clock
  useEffect(() => {
    const t = setInterval(() => setClock(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  const appendLog = (msg, type = 'info') => {
    const entry = { msg, type, time: new Date().toLocaleTimeString('en-US', { hour12: false }) };
    setSystemLogs(prev => [entry, ...prev].slice(0, 50));
  };

  // === SSE Analytics Stream ===
  useEffect(() => {
    const abortController = new AbortController();
    const fetchStream = async () => {
      try {
        const response = await fetch(`${import.meta.env.VITE_API_GATEWAY_URL}/api/analytics/dashboard/stream`, {
          headers: { Authorization: `Bearer ${getToken()}`, Accept: 'text/event-stream' },
          signal: abortController.signal
        });
        if (!response.ok) throw new Error('Stream failed');
        appendLog('SSE stream connected to analytics engine', 'success');
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop();
          for (const line of lines) {
            if (line.startsWith('data:')) {
              try {
                const data = JSON.parse(line.substring(5).trim());
                if (data.health) {
                  Object.entries(data.health).forEach(([svc, newStatus]) => {
                    const oldStatus = prevHealthRef.current[svc];
                    if (oldStatus !== undefined && oldStatus !== newStatus) {
                      appendLog(`[${svc}] status changed: ${oldStatus} → ${newStatus}`, newStatus === 'UP' ? 'success' : 'error');
                    }
                  });
                  prevHealthRef.current = data.health;
                }
                setAnalytics(data);
              } catch (e) {}
            }
          }
        }
      } catch (err) {
        if (err.name !== 'AbortError') {
          appendLog('SSE stream disconnected — retrying in 5s...', 'warn');
          setTimeout(() => { if (!abortController.signal.aborted) fetchStream(); }, 5000);
        }
      }
    };
    fetchStream();
    return () => abortController.abort();
  }, [getToken]);

  // === Backend Audit Logs ===
  const fetchBackendLogs = useCallback(async (serviceName) => {
    try {
      const url = serviceName === 'All Services'
        ? `${import.meta.env.VITE_API_GATEWAY_URL}/api/analytics/audit`
        : `${import.meta.env.VITE_API_GATEWAY_URL}/api/analytics/audit?serviceName=${serviceName}`;
      const res = await axios.get(url, { headers: { Authorization: `Bearer ${getToken()}` } });
      setBackendLogs(res.data);
    } catch (err) {
      appendLog(`Failed to fetch audit logs: ${err.message}`, 'error');
    }
  }, [getToken]);

  useEffect(() => {
    let interval;
    if (logServiceFilter !== 'Frontend Events') {
      fetchBackendLogs(logServiceFilter);
      interval = setInterval(() => fetchBackendLogs(logServiceFilter), 5000);
    }
    return () => clearInterval(interval);
  }, [logServiceFilter, fetchBackendLogs]);

  // === Problem Library ===
  const fetchProblemLibrary = async () => {
    setLibraryLoading(true);
    try {
      const res = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/all`, { headers: { Authorization: `Bearer ${getToken()}` } });
      setProblemLibrary(res.data);
    } catch (err) { appendLog(`Library fetch failed: ${err.message}`, 'error'); }
    finally { setLibraryLoading(false); }
  };

  const handleDeleteProblem = async (title) => {
    try {
      await axios.delete(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/admin/delete/${encodeURIComponent(title)}`, { headers: { Authorization: `Bearer ${getToken()}` } });
      appendLog(`Problem deleted: ${title}`, 'warn');
      setProblemLibrary(prev => prev.filter(p => p.title !== title));
      setConfirmDelete(null);
    } catch (err) { appendLog(`Delete failed: ${err.response?.data || err.message}`, 'error'); setConfirmDelete(null); }
  };

  const handleEditProblem = (p) => {
    setProblem({ ...p, parameterTypes: p.parameterTypes || [], testCases: p.testCases?.length > 0 ? p.testCases : defaultProblemState.testCases });
    setMode('update'); setIsDataLoaded(true); setActiveTab('inject');
    setStatus(`Loaded: '${p.title}' — ready to edit`);
  };

  // === Daily Scheduler ===
  const fetchDailySchedules = async () => {
    setDailyLoading(true);
    try {
      const res = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/daily/admin/all`, { headers: { Authorization: `Bearer ${getToken()}` } });
      setDailySchedules(res.data);
    } catch (err) { appendLog(`Daily schedule fetch failed: ${err.message}`, 'error'); }
    finally { setDailyLoading(false); }
  };

  const handleScheduleDaily = async (e) => {
    e.preventDefault();
    if (!dailyForm.problemId || !dailyForm.activatesAt) { setDailyStatus('ERROR: Problem and activation time are required.'); return; }
    try {
      const utcIso = new Date(dailyForm.activatesAt).toISOString();
      await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/daily/admin/schedule`, {
        problemId: dailyForm.problemId, activatesAt: utcIso, label: dailyForm.label || 'Daily Challenge', scheduledBy: currentUser
      }, { headers: { Authorization: `Bearer ${getToken()}` } });
      setDailyStatus('SUCCESS: Daily question scheduled!');
      setDailyForm({ problemId: '', activatesAt: '', label: '' });
      appendLog(`Daily question scheduled for ${dailyForm.activatesAt}`, 'success');
      fetchDailySchedules();
    } catch (err) { setDailyStatus(`ERROR: ${err.response?.data?.error || err.message}`); }
  };

  const handleDeleteDaily = async (id) => {
    try {
      await axios.delete(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/daily/admin/${id}`, { headers: { Authorization: `Bearer ${getToken()}` } });
      setDailySchedules(prev => prev.filter(d => d.id !== id));
      setConfirmDelete(null);
      appendLog('Daily question entry removed', 'warn');
    } catch (err) { appendLog(`Daily delete failed: ${err.message}`, 'error'); setConfirmDelete(null); }
  };

  useEffect(() => {
    if (activeTab === 'library') fetchProblemLibrary();
    if (activeTab === 'daily') { fetchProblemLibrary(); fetchDailySchedules(); }
  }, [activeTab]);

  const handleTitleChange = (e) => {
    const newTitle = e.target.value;
    setProblem({ ...problem, title: newTitle });
    setIsDataLoaded(false);
    if (mode === 'create') return;
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);
    searchTimeoutRef.current = setTimeout(async () => {
      if (newTitle.length < 2) { setAvailableProblems([]); return; }
      try {
        const res = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/admin/search?query=${newTitle}`, { headers: { Authorization: `Bearer ${getToken()}` } });
        setAvailableProblems(res.data);
        const exactMatch = res.data.find(p => p.title === newTitle);
        if (exactMatch) {
          setProblem({ ...exactMatch, parameterTypes: exactMatch.parameterTypes || [], testCases: exactMatch.testCases?.length > 0 ? exactMatch.testCases : defaultProblemState.testCases });
          setIsDataLoaded(true); setStatus(`Loaded: '${exactMatch.title}'`);
        }
      } catch (err) { setStatus(`SEARCH ERROR: ${err.message}`); }
    }, 300);
  };

  const handleModeSwitch = (newMode) => { setMode(newMode); setProblem(defaultProblemState); setIsDataLoaded(false); setStatus(''); setAvailableProblems([]); };
  const handleAddTestCase = () => setProblem({ ...problem, testCases: [...problem.testCases, { inputs: [''], expectedOutput: '', hidden: false }] });
  const handleTestCaseChange = (index, field, value) => {
    const tc = [...problem.testCases];
    tc[index][field] = field === 'inputs' ? value.split('|').map(s => s.trim()) : value;
    setProblem({ ...problem, testCases: tc });
  };

  const handleProblemSubmit = async (e) => {
    e.preventDefault();
    setStatus(mode === 'create' ? 'Deploying to Core...' : 'Overwriting...');
    try {
      if (mode === 'create') {
        await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/admin/create`, problem, { headers: { Authorization: `Bearer ${getToken()}` } });
        setStatus('SUCCESS: Problem locked into the database!');
        setProblem(defaultProblemState);
        appendLog(`Problem created: ${problem.title}`, 'success');
      } else {
        if (!problem.title) throw new Error('Title is required.');
        await axios.put(`${import.meta.env.VITE_API_GATEWAY_URL}/api/problems/admin/update/${encodeURIComponent(problem.title)}`, problem, { headers: { Authorization: `Bearer ${getToken()}` } });
        setStatus(`SUCCESS: '${problem.title}' updated!`);
        appendLog(`Problem updated: ${problem.title}`, 'success');
      }
    } catch (err) { setStatus(`ERROR: ${err.response?.data || err.message}`); }
  };

  const handleCreateAdmin = async (e) => {
    e.preventDefault();
    setAdminStatus('Processing...');
    try {
      await axios.post(`${import.meta.env.VITE_API_GATEWAY_URL}/api/auth/admin/create-admin`, newAdmin, { headers: { Authorization: `Bearer ${getToken()}` } });
      setAdminStatus(`SUCCESS: ${newAdmin.email} granted ADMIN access.`);
      appendLog(`New admin created: ${newAdmin.email}`, 'warn');
      setNewAdmin({ email: '', password: '' });
    } catch (err) { setAdminStatus(`ERROR: ${err.response?.data || err.message}`); }
  };

  const handleLogout = () => {
    localStorage.removeItem('algo_arena_token');
    localStorage.removeItem('algo_arena_user');
    localStorage.removeItem('algo_arena_role');
    window.location.href = '/overseer';
  };

  // Particle canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let animId;
    const resize = () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; };
    resize();
    window.addEventListener('resize', resize);
    const particles = Array.from({ length: 60 }, () => ({
      x: Math.random() * 1400, y: Math.random() * 900,
      r: Math.random() * 1.5 + 0.5,
      dx: (Math.random() - 0.5) * 0.3, dy: (Math.random() - 0.5) * 0.3,
      alpha: Math.random() * 0.15 + 0.03,
      color: Math.random() > 0.7 ? '#ff7a00' : '#ef4444',
    }));
    const draw = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      for (const p of particles) {
        ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fillStyle = p.color.replace(')', `,${p.alpha})`).replace('rgb', 'rgba').replace('#ff7a00', `rgba(255,122,0,${p.alpha})`).replace('#ef4444', `rgba(239,68,68,${p.alpha})`);
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

  const servicesOnline = analytics?.health ? Object.values(analytics.health).filter(v => v === 'UP').length : 0;
  const totalServices = analytics?.health ? Object.keys(analytics.health).length : 6;

  const inputStyle = {
    width: '100%', padding: '13px 16px',
    background: 'rgba(255,255,255,0.03)',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: '10px', color: '#e5e5e5',
    fontSize: '0.9rem', outline: 'none',
    transition: 'all 0.2s', fontFamily: "'Inter', sans-serif",
  };

  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Outfit:wght@400;600;700;800;900&family=Share+Tech+Mono&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        html, body { background: #060302; height: 100%; }
        .adm-root { display: flex; min-height: 100vh; font-family: 'Inter', sans-serif; color: #e5e5e5; background: #060302; position: relative; overflow: hidden; }

        /* ── Sidebar ── */
        .adm-sidebar {
          width: ${sidebarCollapsed ? '72px' : '240px'};
          min-height: 100vh; flex-shrink: 0;
          background: rgba(8,4,2,0.95);
          border-right: 1px solid rgba(255,122,0,0.08);
          display: flex; flex-direction: column;
          position: sticky; top: 0; height: 100vh;
          transition: width 0.3s cubic-bezier(.4,0,.2,1);
          z-index: 50; overflow: hidden;
          backdrop-filter: blur(20px);
        }
        .adm-sidebar-logo {
          padding: 24px 20px 20px;
          border-bottom: 1px solid rgba(255,255,255,0.04);
          display: flex; align-items: center; gap: 12px;
          cursor: pointer; overflow: hidden; flex-shrink: 0;
        }
        .adm-logo-img { width: 34px; height: 34px; object-fit: contain; flex-shrink: 0; }
        .adm-logo-text {
          font-family: 'Outfit', sans-serif; font-size: 1rem; font-weight: 800;
          white-space: nowrap; opacity: ${sidebarCollapsed ? '0' : '1'};
          transition: opacity 0.2s; color: #fff;
        }
        .adm-logo-badge {
          font-size: 0.6rem; font-weight: 700; color: #ff7a00;
          background: rgba(255,122,0,0.12); border: 1px solid rgba(255,122,0,0.25);
          padding: 2px 6px; border-radius: 4px; letter-spacing: 0.8px;
          white-space: nowrap; flex-shrink: 0;
        }
        .adm-nav { flex: 1; padding: 16px 12px; display: flex; flex-direction: column; gap: 4px; overflow-y: auto; }
        .adm-nav-section { font-size: 0.65rem; font-weight: 600; color: #444; letter-spacing: 1.5px; text-transform: uppercase; padding: 8px 8px 4px; white-space: nowrap; overflow: hidden; }
        .adm-nav-item {
          display: flex; align-items: center; gap: 12px;
          padding: 11px 12px; border-radius: 10px; cursor: pointer;
          transition: all 0.2s; color: #666; font-size: 0.875rem; font-weight: 500;
          white-space: nowrap; overflow: hidden; border: 1px solid transparent;
          text-decoration: none;
        }
        .adm-nav-item:hover { color: #ccc; background: rgba(255,255,255,0.04); }
        .adm-nav-item.active {
          color: #ff7a00; background: rgba(255,122,0,0.08);
          border-color: rgba(255,122,0,0.2);
        }
        .adm-nav-icon { font-size: 1rem; flex-shrink: 0; width: 20px; text-align: center; font-style: normal; }
        .adm-nav-label { opacity: ${sidebarCollapsed ? '0' : '1'}; transition: opacity 0.2s; }
        .adm-sidebar-footer {
          padding: 16px 12px;
          border-top: 1px solid rgba(255,255,255,0.04);
          flex-shrink: 0;
        }
        .adm-user-card {
          display: flex; align-items: center; gap: 10px;
          padding: 10px; border-radius: 10px;
          background: rgba(255,255,255,0.02);
          overflow: hidden;
        }
        .adm-avatar {
          width: 32px; height: 32px; border-radius: 8px;
          background: linear-gradient(135deg, #ff7a00, #ef4444);
          display: flex; align-items: center; justify-content: center;
          font-size: 0.8rem; font-weight: 700; color: #fff; flex-shrink: 0;
        }
        .adm-user-info { overflow: hidden; opacity: ${sidebarCollapsed ? '0' : '1'}; transition: opacity 0.2s; }
        .adm-user-name { font-size: 0.82rem; font-weight: 600; color: #ddd; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .adm-user-role { font-size: 0.7rem; color: #ff7a00; font-weight: 600; letter-spacing: 0.5px; }
        .adm-collapse-btn {
          width: 100%; margin-top: 8px; padding: 8px;
          background: none; border: 1px solid rgba(255,255,255,0.06);
          border-radius: 8px; color: #444; cursor: pointer; font-size: 0.8rem;
          transition: all 0.2s; display: flex; align-items: center; justify-content: center;
        }
        .adm-collapse-btn:hover { color: #888; border-color: rgba(255,255,255,0.12); }

        /* ── Main Content ── */
        .adm-main { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow-y: auto; }

        /* ── Topbar ── */
        .adm-topbar {
          padding: 0 32px;
          height: 64px; display: flex; align-items: center;
          justify-content: space-between;
          border-bottom: 1px solid rgba(255,255,255,0.04);
          background: rgba(6,3,2,0.8); backdrop-filter: blur(12px);
          position: sticky; top: 0; z-index: 40; flex-shrink: 0;
        }
        .adm-topbar-left { display: flex; align-items: center; gap: 16px; }
        .adm-page-title { font-family: 'Outfit', sans-serif; font-size: 1.1rem; font-weight: 700; color: #fff; }
        .adm-breadcrumb { font-size: 0.78rem; color: #444; }
        .adm-topbar-right { display: flex; align-items: center; gap: 16px; }
        .adm-clock { font-family: 'Share Tech Mono', monospace; font-size: 0.85rem; color: #555; }
        .adm-stream-pill {
          display: flex; align-items: center; gap: 6px;
          padding: 5px 12px; border-radius: 20px; font-size: 0.75rem; font-weight: 600;
          background: rgba(39,201,63,0.08); border: 1px solid rgba(39,201,63,0.2); color: #4ade80;
        }
        .adm-stream-pill.offline { background: rgba(239,68,68,0.08); border-color: rgba(239,68,68,0.2); color: #f87171; }
        .adm-stream-dot { width: 6px; height: 6px; border-radius: 50%; background: #27c93f; animation: pulse-green 2s infinite; }
        .adm-stream-dot.off { background: #ef4444; animation: pulse-red 1.5s infinite; }
        @keyframes pulse-green { 0%,100% { opacity: 1; box-shadow: 0 0 0 0 rgba(39,201,63,0.4); } 50% { box-shadow: 0 0 0 4px rgba(39,201,63,0); } }
        @keyframes pulse-red { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
        .btn-logout-top {
          padding: 6px 16px; border-radius: 8px;
          background: rgba(255,255,255,0.04); color: #888;
          border: 1px solid rgba(255,255,255,0.08); font-size: 0.8rem;
          font-weight: 600; cursor: pointer; transition: all 0.2s;
        }
        .btn-logout-top:hover { color: #f87171; border-color: rgba(239,68,68,0.3); background: rgba(239,68,68,0.06); }

        /* ── Page Body ── */
        .adm-body { padding: 28px 32px; flex: 1; }

        /* ── Stat Cards ── */
        .adm-stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
        @media (max-width: 1100px) { .adm-stats-grid { grid-template-columns: repeat(2, 1fr); } }
        .stat-c {
          background: rgba(12,6,3,0.8);
          border: 1px solid rgba(255,255,255,0.06);
          border-radius: 16px; padding: 22px 24px;
          position: relative; overflow: hidden;
          transition: transform 0.2s, border-color 0.3s;
          cursor: default;
        }
        .stat-c:hover { transform: translateY(-2px); }
        .stat-c::before {
          content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px;
          background: var(--sc, #ff7a00);
        }
        .stat-c-glow {
          position: absolute; top: -30px; right: -30px; width: 100px; height: 100px;
          border-radius: 50%; background: var(--sc, #ff7a00);
          opacity: 0.04; filter: blur(20px); pointer-events: none;
        }
        .stat-c-label { font-size: 0.72rem; font-weight: 600; color: #555; text-transform: uppercase; letter-spacing: 1.2px; margin-bottom: 10px; }
        .stat-c-value { font-family: 'Outfit', sans-serif; font-size: 2.4rem; font-weight: 800; color: #fff; line-height: 1; }
        .stat-c-sub { font-size: 0.75rem; color: #444; margin-top: 6px; }
        .stat-c-icon { position: absolute; bottom: 16px; right: 18px; font-size: 1.8rem; opacity: 0.12; }

        /* ── Monitor Grid ── */
        .adm-monitor-grid { display: grid; grid-template-columns: 1fr 340px; gap: 20px; }
        @media (max-width: 1200px) { .adm-monitor-grid { grid-template-columns: 1fr; } }

        /* ── Glass Panel ── */
        .gp {
          background: rgba(10,5,2,0.7);
          border: 1px solid rgba(255,255,255,0.06);
          border-radius: 16px;
          backdrop-filter: blur(10px);
        }
        .gp-header {
          padding: 20px 24px 0;
          display: flex; align-items: center; justify-content: space-between;
          margin-bottom: 20px;
        }
        .gp-title { font-family: 'Outfit', sans-serif; font-size: 1rem; font-weight: 700; color: #ddd; display: flex; align-items: center; gap: 8px; }
        .gp-title-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--td, #ff7a00); flex-shrink: 0; }
        .gp-body { padding: 0 24px 24px; }

        /* ── Service Health Cards ── */
        .svc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
        .svc-card {
          display: flex; align-items: center; gap: 12px;
          padding: 14px 16px; border-radius: 12px;
          background: rgba(255,255,255,0.02);
          border: 1px solid rgba(255,255,255,0.04);
          transition: all 0.3s;
        }
        .svc-card.up { border-color: rgba(39,201,63,0.15); }
        .svc-card.down { border-color: rgba(239,68,68,0.2); background: rgba(239,68,68,0.03); }
        .svc-indicator { flex-shrink: 0; }
        .svc-dot-wrap { position: relative; width: 10px; height: 10px; }
        .svc-dot-core { width: 10px; height: 10px; border-radius: 50%; position: relative; z-index: 1; }
        .svc-dot-ping {
          position: absolute; inset: -4px; border-radius: 50%;
          animation: svc-ping 2s ease-out infinite;
        }
        @keyframes svc-ping { 0% { transform: scale(1); opacity: 0.6; } 100% { transform: scale(2.5); opacity: 0; } }
        .svc-dot-core.up { background: #27c93f; box-shadow: 0 0 8px #27c93f; }
        .svc-dot-ping.up { background: rgba(39,201,63,0.4); }
        .svc-dot-core.down { background: #ef4444; }
        .svc-dot-ping.down { display: none; }
        .svc-dot-core.unknown { background: #444; }
        .svc-dot-ping.unknown { display: none; }
        .svc-name { font-size: 0.82rem; font-weight: 600; color: #ccc; font-family: 'Share Tech Mono', monospace; }
        .svc-status-txt { font-size: 0.68rem; font-weight: 700; letter-spacing: 0.8px; margin-top: 1px; }
        .svc-status-txt.up { color: #27c93f; } .svc-status-txt.down { color: #ef4444; } .svc-status-txt.unknown { color: #444; }
        .svc-svc-icon { font-size: 0.9rem; margin-left: auto; opacity: 0.5; }

        /* ── Log Panel ── */
        .log-panel { font-family: 'Share Tech Mono', monospace; max-height: 240px; overflow-y: auto; display: flex; flex-direction: column; gap: 5px; }
        .log-panel::-webkit-scrollbar { width: 3px; }
        .log-panel::-webkit-scrollbar-track { background: transparent; }
        .log-panel::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 2px; }
        .log-entry { font-size: 0.78rem; padding: 6px 10px; border-radius: 6px; display: flex; gap: 10px; align-items: flex-start; border-left: 2px solid transparent; }
        .log-entry.info { color: #555; border-left-color: #333; }
        .log-entry.success { color: #4ade80; background: rgba(39,201,63,0.04); border-left-color: #27c93f; }
        .log-entry.error { color: #f87171; background: rgba(239,68,68,0.05); border-left-color: #ef4444; }
        .log-entry.warn { color: #fbbf24; background: rgba(245,158,11,0.04); border-left-color: #f59e0b; }
        .log-time { color: #333; flex-shrink: 0; font-size: 0.72rem; padding-top: 1px; }
        .log-svc { color: #555; }
        .log-msg { word-break: break-word; flex: 1; }

        /* ── Log filter select ── */
        .log-select {
          background: rgba(255,255,255,0.03);
          border: 1px solid rgba(255,255,255,0.08);
          border-radius: 8px; color: #888; font-size: 0.78rem;
          padding: 5px 8px; outline: none; cursor: pointer; font-family: 'Inter', sans-serif;
        }

        /* ── Traffic Panel ── */
        .traffic-row { display: flex; align-items: center; gap: 16px; padding: 14px 0; border-bottom: 1px solid rgba(255,255,255,0.04); }
        .traffic-row:last-child { border-bottom: none; }
        .traffic-label { font-size: 0.82rem; color: #666; flex: 1; }
        .traffic-val { font-family: 'Outfit', sans-serif; font-size: 1.4rem; font-weight: 800; color: #fff; }
        .traffic-unit { font-size: 0.7rem; color: #555; margin-left: 4px; }
        .traffic-bar-wrap { width: 80px; height: 4px; background: rgba(255,255,255,0.05); border-radius: 2px; overflow: hidden; }
        .traffic-bar { height: 100%; border-radius: 2px; background: linear-gradient(90deg, #ff7a00, #ef4444); transition: width 1s; }

        /* ── Buttons ── */
        .btn-primary { background: linear-gradient(135deg, #ff7a00, #e06000); color: #fff; border: none; padding: 11px 22px; border-radius: 10px; font-weight: 600; font-size: 0.875rem; cursor: pointer; transition: all 0.2s; font-family: 'Inter', sans-serif; }
        .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(255,122,0,0.3); }
        .btn-outline { background: transparent; border: 1px solid rgba(255,255,255,0.1); color: #888; padding: 11px 22px; border-radius: 10px; font-weight: 600; font-size: 0.875rem; cursor: pointer; transition: all 0.2s; font-family: 'Inter', sans-serif; }
        .btn-outline:hover { border-color: rgba(255,122,0,0.4); color: #ff7a00; background: rgba(255,122,0,0.04); }
        .btn-sm { padding: 5px 12px; border-radius: 7px; font-size: 0.75rem; font-weight: 600; cursor: pointer; border: none; transition: all 0.2s; font-family: 'Inter', sans-serif; }
        .btn-edit { background: rgba(59,130,246,0.12); color: #60a5fa; border: 1px solid rgba(59,130,246,0.2); }
        .btn-edit:hover { background: rgba(59,130,246,0.25); }
        .btn-del { background: rgba(239,68,68,0.08); color: #f87171; border: 1px solid rgba(239,68,68,0.15); }
        .btn-del:hover { background: rgba(239,68,68,0.2); }

        /* ── Alerts ── */
        .alert-box { padding: 14px 18px; border-radius: 10px; margin-bottom: 20px; font-size: 0.875rem; font-weight: 600; }
        .alert-success { background: rgba(34,197,94,0.08); border: 1px solid rgba(34,197,94,0.25); color: #4ade80; }
        .alert-error { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.25); color: #f87171; }

        /* ── Table ── */
        .prob-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
        .prob-table th { text-align: left; color: #444; font-size: 0.7rem; letter-spacing: 1.2px; text-transform: uppercase; padding: 0 14px 14px; border-bottom: 1px solid rgba(255,255,255,0.05); }
        .prob-table td { padding: 13px 14px; border-bottom: 1px solid rgba(255,255,255,0.03); color: #bbb; vertical-align: middle; }
        .prob-table tr:hover td { background: rgba(255,122,0,0.02); }
        .diff-badge { display: inline-block; padding: 2px 9px; border-radius: 20px; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.5px; }
        .diff-EASY { background: rgba(34,197,94,0.1); color: #4ade80; }
        .diff-MEDIUM { background: rgba(245,158,11,0.1); color: #fbbf24; }
        .diff-HARD { background: rgba(239,68,68,0.1); color: #f87171; }
        .daily-status-badge { display: inline-block; padding: 2px 9px; border-radius: 20px; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.5px; }
        .daily-ACTIVE { background: rgba(39,201,63,0.12); color: #4ade80; }
        .daily-UPCOMING { background: rgba(59,130,246,0.12); color: #60a5fa; }
        .daily-EXPIRED { background: rgba(255,255,255,0.04); color: #444; }

        /* ── Modal ── */
        .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.8); backdrop-filter: blur(8px); z-index: 1000; display: flex; align-items: center; justify-content: center; }
        .modal-box { background: #0d0704; border: 1px solid rgba(255,122,0,0.2); border-radius: 18px; padding: 32px; max-width: 420px; width: 90%; box-shadow: 0 0 0 1px rgba(255,255,255,0.03), 0 30px 60px rgba(0,0,0,0.8); }
        .modal-box h3 { font-family: 'Outfit', sans-serif; font-size: 1.2rem; color: #fff; margin-bottom: 10px; }
        .modal-box p { color: #666; margin-bottom: 24px; line-height: 1.6; font-size: 0.875rem; }
        .modal-actions { display: flex; gap: 10px; justify-content: flex-end; }

        /* ── Scrollbar global ── */
        ::-webkit-scrollbar { width: 5px; height: 5px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.07); border-radius: 3px; }
        ::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.12); }

        /* ── Section heading ── */
        .section-heading { font-family: 'Outfit', sans-serif; font-size: 1.4rem; font-weight: 800; color: #fff; margin-bottom: 6px; }
        .section-sub { font-size: 0.82rem; color: #444; margin-bottom: 24px; }

        /* ── Input focus glow ── */
        input:focus, textarea:focus, select:focus { border-color: rgba(255,122,0,0.4) !important; box-shadow: 0 0 0 3px rgba(255,122,0,0.06); }

        /* ── Divider ── */
        .divider { height: 1px; background: rgba(255,255,255,0.04); margin: 24px 0; }

        /* ── Progress ring ── */
        .progress-ring-wrap { position: relative; width: 64px; height: 64px; flex-shrink: 0; }
        .progress-ring-wrap svg { transform: rotate(-90deg); }
        .progress-ring-label { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
        .progress-ring-val { font-family: 'Outfit', sans-serif; font-size: 1rem; font-weight: 800; color: #fff; line-height: 1; }
        .progress-ring-sub { font-size: 0.55rem; color: #555; margin-top: 1px; }

        /* ── Tag ── */
        .tag { display: inline-block; padding: 2px 8px; border-radius: 5px; font-size: 0.7rem; font-weight: 600; background: rgba(255,122,0,0.1); color: #ff7a00; border: 1px solid rgba(255,122,0,0.2); }
      `}</style>

      <canvas ref={canvasRef} style={{ position: 'fixed', inset: 0, width: '100vw', height: '100vh', pointerEvents: 'none', zIndex: 0 }} />
      <div style={{ position: 'fixed', inset: 0, background: 'radial-gradient(ellipse at 20% 50%, rgba(255,122,0,0.04) 0%, transparent 60%), radial-gradient(ellipse at 80% 20%, rgba(239,68,68,0.03) 0%, transparent 50%)', zIndex: 1, pointerEvents: 'none' }} />

      <div className="adm-root" style={{ position: 'relative', zIndex: 2 }}>

        {/* ── SIDEBAR ── */}
        <aside className="adm-sidebar">
          <div className="adm-sidebar-logo" onClick={() => navigate('/overseer')}>
            <img src="/O1.png" alt="O1 Outpost" className="adm-logo-img" />
            {!sidebarCollapsed && (
              <span className="adm-logo-badge">OVERSEER</span>
            )}
          </div>

          <nav className="adm-nav">
            <div className="adm-nav-section">{sidebarCollapsed ? '·' : 'Navigation'}</div>
            {NAV_ITEMS.map(item => (
              <div key={item.id}
                className={`adm-nav-item ${activeTab === item.id ? 'active' : ''}`}
                onClick={() => setActiveTab(item.id)}>
                <i className="adm-nav-icon">{item.icon}</i>
                <span className="adm-nav-label">{item.label}</span>
              </div>
            ))}
          </nav>

          <div className="adm-sidebar-footer">
            <div className="adm-user-card">
              <div className="adm-avatar">{currentUser?.[0]?.toUpperCase() || 'A'}</div>
              <div className="adm-user-info">
                <div className="adm-user-name">{currentUser || 'Admin'}</div>
                <div className="adm-user-role">OVERSEER</div>
              </div>
            </div>
            <button className="adm-collapse-btn" onClick={() => setSidebarCollapsed(!sidebarCollapsed)}>
              {sidebarCollapsed ? '→' : '← Collapse'}
            </button>
          </div>
        </aside>

        {/* ── MAIN ── */}
        <main className="adm-main">

          {/* Topbar */}
          <header className="adm-topbar">
            <div className="adm-topbar-left">
              <div>
                <div className="adm-page-title">{NAV_ITEMS.find(n => n.id === activeTab)?.label}</div>
                <div className="adm-breadcrumb">O One Outpost / Admin / {NAV_ITEMS.find(n => n.id === activeTab)?.label}</div>
              </div>
            </div>
            <div className="adm-topbar-right">
              <div className="adm-clock">{clock.toLocaleTimeString('en-US', { hour12: false })}</div>
              <div className={`adm-stream-pill ${analytics?.health ? '' : 'offline'}`}>
                <div className={`adm-stream-dot ${analytics?.health ? '' : 'off'}`} />
                {analytics?.health ? 'Stream Live' : 'Connecting'}
              </div>
              <button className="btn-logout-top" onClick={handleLogout}>Sign Out</button>
            </div>
          </header>

          {/* Body */}
          <div className="adm-body">

            {/* ==================== MONITOR ==================== */}
            {activeTab === 'monitor' && (<>
              {/* Stat cards */}
              <div className="adm-stats-grid">
                {[
                  { label: 'Requests / sec', value: analytics?.traffic?.requestsPerSecond ?? '—', sub: 'via gateway', color: '#6366f1', icon: '⚡' },
                  { label: 'Active WebSockets', value: analytics?.traffic?.activeWebSockets ?? '—', sub: 'live connections', color: '#8b5cf6', icon: '⬡' },
                  { label: 'Services Online', value: `${servicesOnline}/${totalServices}`, sub: `${totalServices - servicesOnline} offline`, color: servicesOnline === totalServices ? '#27c93f' : '#ef4444', icon: '◈' },
                  { label: 'Pending Alerts', value: analytics?.alerts?.length ?? 0, sub: 'system events', color: '#ff7a00', icon: '◉' },
                ].map((s, i) => (
                  <div key={i} className="stat-c" style={{ '--sc': s.color }}>
                    <div className="stat-c-glow" />
                    <div className="stat-c-label">{s.label}</div>
                    <div className="stat-c-value" style={{ color: s.value === '—' ? '#333' : '#fff' }}>{s.value}</div>
                    <div className="stat-c-sub">{s.sub}</div>
                    <div className="stat-c-icon">{s.icon}</div>
                  </div>
                ))}
              </div>

              <div className="adm-monitor-grid">
                {/* Left column */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

                  {/* Health Monitor */}
                  <div className="gp">
                    <div className="gp-header">
                      <div className="gp-title" style={{ '--td': '#6366f1' }}>
                        <div className="gp-title-dot" />
                        Microservice Health
                      </div>
                      {analytics?.health && (
                        <span style={{ fontSize: '0.75rem', color: '#555' }}>
                          Updated {new Date().toLocaleTimeString()}
                        </span>
                      )}
                    </div>
                    <div className="gp-body">
                      {analytics?.health ? (
                        <div className="svc-grid">
                          {SERVICES.map(({ key, label, icon, color }) => {
                            const s = analytics.health[key];
                            const cls = s === 'UP' ? 'up' : s === 'DOWN' ? 'down' : 'unknown';
                            return (
                              <div key={key} className={`svc-card ${cls}`}>
                                <div className="svc-indicator">
                                  <div className="svc-dot-wrap">
                                    <div className={`svc-dot-ping ${cls}`} style={cls === 'up' ? { background: `${color}55` } : {}} />
                                    <div className={`svc-dot-core ${cls}`} style={cls === 'up' ? { background: color, boxShadow: `0 0 8px ${color}` } : {}} />
                                  </div>
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div className="svc-name">{icon} {label}</div>
                                  <div className={`svc-status-txt ${cls}`}>{s || 'UNKNOWN'}</div>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        <p style={{ color: '#333', fontFamily: "'Share Tech Mono', monospace", fontSize: '0.85rem', padding: '20px 0' }}>Awaiting telemetry stream...</p>
                      )}
                    </div>
                  </div>

                  {/* System Event Log */}
                  <div className="gp">
                    <div className="gp-header">
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div className="gp-title" style={{ '--td': '#f59e0b' }}>
                          <div className="gp-title-dot" />
                          Audit Logs
                        </div>
                        <select className="log-select" value={logServiceFilter} onChange={e => setLogServiceFilter(e.target.value)}>
                          <option value="Frontend Events">Frontend</option>
                          <option value="All Services">All Backend</option>
                          {SERVICES.map(s => <option key={s.key} value={s.key}>{s.label}</option>)}
                        </select>
                      </div>
                      <button onClick={() => logServiceFilter === 'Frontend Events' ? setSystemLogs([]) : fetchBackendLogs(logServiceFilter)}
                        style={{ background: 'none', border: '1px solid rgba(255,255,255,0.06)', color: '#444', padding: '4px 10px', borderRadius: '6px', cursor: 'pointer', fontSize: '0.75rem' }}>
                        {logServiceFilter === 'Frontend Events' ? 'Clear' : '↻'}
                      </button>
                    </div>
                    <div className="gp-body">
                      <div className="log-panel">
                        {logServiceFilter === 'Frontend Events' ? (
                          systemLogs.length === 0 ? (
                            <span style={{ color: '#2a2a2a', fontFamily: "'Share Tech Mono', monospace", fontSize: '0.8rem' }}>No events. System monitoring active...</span>
                          ) : systemLogs.map((log, i) => (
                            <div key={i} className={`log-entry ${log.type}`}>
                              <span className="log-time">{log.time}</span>
                              <span className="log-msg">{log.msg}</span>
                            </div>
                          ))
                        ) : (
                          backendLogs.length === 0 ? (
                            <span style={{ color: '#2a2a2a', fontFamily: "'Share Tech Mono', monospace", fontSize: '0.8rem' }}>No warnings or errors found.</span>
                          ) : backendLogs.map(log => (
                            <div key={log.id} className={`log-entry ${log.logLevel === 'ERROR' ? 'error' : 'warn'}`}>
                              <span className="log-time" style={{ minWidth: '80px' }}>{new Date(log.timestamp).toLocaleTimeString()}</span>
                              <span className="log-svc">[{log.serviceName}]</span>
                              <span className="log-msg">{log.message}</span>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Right column */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>

                  {/* Traffic Panel */}
                  <div className="gp">
                    <div className="gp-header">
                      <div className="gp-title" style={{ '--td': '#ff7a00' }}>
                        <div className="gp-title-dot" />
                        Traffic
                      </div>
                    </div>
                    <div className="gp-body">
                      <div className="traffic-row">
                        <div className="traffic-label">Requests/sec</div>
                        <div>
                          <span className="traffic-val">{analytics?.traffic?.requestsPerSecond ?? 0}</span>
                          <span className="traffic-unit">rps</span>
                        </div>
                      </div>
                      <div className="traffic-row">
                        <div className="traffic-label">WebSocket Connections</div>
                        <div>
                          <span className="traffic-val">{analytics?.traffic?.activeWebSockets ?? 0}</span>
                          <span className="traffic-unit">conn</span>
                        </div>
                      </div>
                      <div className="traffic-row" style={{ borderBottom: 'none' }}>
                        <div className="traffic-label">Services Up</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                          <div className="progress-ring-wrap">
                            <svg width="64" height="64" viewBox="0 0 64 64">
                              <circle cx="32" cy="32" r="26" fill="none" stroke="rgba(255,255,255,0.05)" strokeWidth="5" />
                              <circle cx="32" cy="32" r="26" fill="none"
                                stroke={servicesOnline === totalServices ? '#27c93f' : '#ff7a00'}
                                strokeWidth="5" strokeLinecap="round"
                                strokeDasharray={`${2 * Math.PI * 26}`}
                                strokeDashoffset={`${2 * Math.PI * 26 * (1 - servicesOnline / Math.max(totalServices, 1))}`}
                                style={{ transition: 'stroke-dashoffset 1s ease' }}
                              />
                            </svg>
                            <div className="progress-ring-label">
                              <div className="progress-ring-val">{servicesOnline}</div>
                              <div className="progress-ring-sub">of {totalServices}</div>
                            </div>
                          </div>
                          <div>
                            <div style={{ fontSize: '0.8rem', color: servicesOnline === totalServices ? '#27c93f' : '#ff7a00', fontWeight: 700 }}>
                              {servicesOnline === totalServices ? 'All Online' : `${totalServices - servicesOnline} Down`}
                            </div>
                            <div style={{ fontSize: '0.7rem', color: '#444', marginTop: '2px' }}>System health</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Quick Actions */}
                  <div className="gp">
                    <div className="gp-header">
                      <div className="gp-title" style={{ '--td': '#8b5cf6' }}>
                        <div className="gp-title-dot" />
                        Quick Actions
                      </div>
                    </div>
                    <div className="gp-body" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {[
                        { label: 'Inject New Problem', tab: 'inject', color: '#ff7a00' },
                        { label: 'Schedule Daily Challenge', tab: 'daily', color: '#3b82f6' },
                        { label: 'View Problem Library', tab: 'library', color: '#10b981' },
                        { label: 'Manage Operators', tab: 'operators', color: '#8b5cf6' },
                      ].map(a => (
                        <button key={a.tab} onClick={() => setActiveTab(a.tab)}
                          style={{
                            width: '100%', padding: '11px 14px', borderRadius: '10px',
                            background: 'rgba(255,255,255,0.02)', border: `1px solid rgba(255,255,255,0.05)`,
                            color: '#888', textAlign: 'left', cursor: 'pointer', fontSize: '0.85rem',
                            fontFamily: "'Inter', sans-serif", fontWeight: 500,
                            transition: 'all 0.2s', display: 'flex', alignItems: 'center', gap: '10px',
                          }}
                          onMouseEnter={e => { e.currentTarget.style.borderColor = `${a.color}40`; e.currentTarget.style.color = a.color; e.currentTarget.style.background = `${a.color}08`; }}
                          onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.05)'; e.currentTarget.style.color = '#888'; e.currentTarget.style.background = 'rgba(255,255,255,0.02)'; }}
                        >
                          <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: a.color, flexShrink: 0 }} />
                          {a.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </>)}

            {/* ==================== PROBLEM LIBRARY ==================== */}
            {activeTab === 'library' && (
              <div>
                <div className="section-heading">Problem Library</div>
                <div className="section-sub">{problemLibrary.length} problems in the system</div>
                <div className="gp">
                  <div className="gp-header">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <input placeholder="Search by title..." value={librarySearch} onChange={e => setLibrarySearch(e.target.value)}
                        style={{ ...inputStyle, width: '240px', padding: '9px 14px', fontSize: '0.85rem' }} />
                    </div>
                    <button className="btn-outline" onClick={fetchProblemLibrary} style={{ padding: '9px 18px' }}>
                      {libraryLoading ? 'Loading...' : '↻ Refresh'}
                    </button>
                  </div>
                  <div className="gp-body">
                    {problemLibrary.length === 0 ? (
                      <p style={{ color: '#333', textAlign: 'center', padding: '40px 0', fontFamily: "'Share Tech Mono', monospace" }}>No problems found. Inject your first problem!</p>
                    ) : (
                      <div style={{ overflowX: 'auto' }}>
                        <table className="prob-table">
                          <thead><tr>
                            <th>Title</th><th>Difficulty</th><th>Method</th><th>Tests</th><th>Actions</th>
                          </tr></thead>
                          <tbody>
                            {problemLibrary.filter(p => p.title?.toLowerCase().includes(librarySearch.toLowerCase())).map(p => (
                              <tr key={p.id}>
                                <td style={{ fontWeight: 600, color: '#e5e5e5', maxWidth: '260px' }}>{p.title}</td>
                                <td><span className={`diff-badge diff-${p.difficulty}`}>{p.difficulty}</span></td>
                                <td style={{ fontFamily: "'Share Tech Mono', monospace", fontSize: '0.8rem', color: '#ff7a00' }}>{p.methodName}()</td>
                                <td style={{ color: '#555' }}>{p.testCases?.length ?? 0}</td>
                                <td><div style={{ display: 'flex', gap: '8px' }}>
                                  <button className="btn-sm btn-edit" onClick={() => handleEditProblem(p)}>Edit</button>
                                  <button className="btn-sm btn-del" onClick={() => setConfirmDelete({ title: p.title, type: 'problem' })}>Delete</button>
                                </div></td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* ==================== DAILY SCHEDULER ==================== */}
            {activeTab === 'daily' && (<>
              <div className="section-heading">Daily Challenge Scheduler</div>
              <div className="section-sub">Set the activation window for today's problem. Access is enforced server-side.</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
                <div className="gp">
                  <div className="gp-header">
                    <div className="gp-title" style={{ '--td': '#3b82f6' }}><div className="gp-title-dot" />Schedule Challenge</div>
                  </div>
                  <div className="gp-body">
                    {dailyStatus && <div className={`alert-box ${dailyStatus.includes('ERROR') ? 'alert-error' : 'alert-success'}`}>{dailyStatus}</div>}
                    <form onSubmit={handleScheduleDaily} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.72rem', color: '#555', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>Problem</label>
                        <select value={dailyForm.problemId} onChange={e => setDailyForm({ ...dailyForm, problemId: e.target.value })} style={inputStyle} required>
                          <option value="">— Select a Problem —</option>
                          {problemLibrary.map(p => <option key={p.id} value={p.id}>{p.title} ({p.difficulty})</option>)}
                        </select>
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.72rem', color: '#555', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>Activation Date & Time</label>
                        <input type="datetime-local" value={dailyForm.activatesAt} onChange={e => setDailyForm({ ...dailyForm, activatesAt: e.target.value })} style={{ ...inputStyle, colorScheme: 'dark' }} required />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.72rem', color: '#555', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>Label (optional)</label>
                        <input placeholder="e.g. Day 42 — Hard Mode" value={dailyForm.label} onChange={e => setDailyForm({ ...dailyForm, label: e.target.value })} style={inputStyle} />
                      </div>
                      <button type="submit" className="btn-primary" style={{ marginTop: '4px' }}>Schedule Challenge</button>
                    </form>
                  </div>
                </div>

                <div className="gp">
                  <div className="gp-header">
                    <div className="gp-title" style={{ '--td': '#ff7a00' }}><div className="gp-title-dot" />Scheduled Challenges</div>
                    <button className="btn-outline" onClick={fetchDailySchedules} style={{ padding: '7px 14px', fontSize: '0.8rem' }}>{dailyLoading ? '...' : '↻'}</button>
                  </div>
                  <div className="gp-body">
                    {dailySchedules.length === 0 ? (
                      <p style={{ color: '#333', textAlign: 'center', padding: '30px 0', fontFamily: "'Share Tech Mono', monospace", fontSize: '0.85rem' }}>No challenges scheduled.</p>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        {dailySchedules.map(d => (
                          <div key={d.id} style={{ padding: '14px 16px', borderRadius: '10px', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)', display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <div style={{ flex: 1, minWidth: 0 }}>
                              <div style={{ fontWeight: 600, color: '#ddd', fontSize: '0.875rem', marginBottom: '4px' }}>{d.problemTitle || d.problemId}</div>
                              <div style={{ fontSize: '0.75rem', color: '#555', fontFamily: "'Share Tech Mono', monospace" }}>{new Date(d.activatesAt).toLocaleString()} → {new Date(d.expiresAt).toLocaleString()}</div>
                            </div>
                            <span className={`daily-status-badge daily-${d.windowStatus}`}>{d.windowStatus}</span>
                            <button className="btn-sm btn-del" onClick={() => setConfirmDelete({ title: d.label || d.problemTitle, type: 'daily', id: d.id })}>✕</button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </>)}

            {/* ==================== INJECT ==================== */}
            {activeTab === 'inject' && (
              <div>
                <div className="section-heading">{mode === 'create' ? 'Inject New Problem' : 'Edit Problem'}</div>
                <div className="section-sub">Add or update algorithmic challenges in the database.</div>
                <div style={{ display: 'flex', gap: '10px', marginBottom: '24px' }}>
                  <button className={mode === 'create' ? 'btn-primary' : 'btn-outline'} onClick={() => handleModeSwitch('create')}>Inject New</button>
                  <button className={mode === 'update' ? 'btn-primary' : 'btn-outline'} onClick={() => handleModeSwitch('update')}>Update Existing</button>
                </div>
                <div className="gp">
                  <div className="gp-body" style={{ paddingTop: '24px' }}>
                    {status && <div className={`alert-box ${status.includes('ERROR') ? 'alert-error' : 'alert-success'}`}>{status}</div>}
                    <form onSubmit={handleProblemSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '16px' }}>
                        <div style={{ position: 'relative' }}>
                          <input list={mode === 'update' ? 'problem-list' : undefined}
                            placeholder={mode === 'update' ? 'Search existing problem...' : 'Problem Title (e.g., Two Sum)'}
                            value={problem.title} onChange={handleTitleChange} style={inputStyle} required autoComplete="off" />
                          {mode === 'update' && <datalist id="problem-list">{availableProblems.map(p => <option key={p.id || p.title} value={p.title} />)}</datalist>}
                        </div>
                        <select value={problem.difficulty} onChange={e => setProblem({ ...problem, difficulty: e.target.value })} style={inputStyle}>
                          <option value="EASY">EASY</option>
                          <option value="MEDIUM">MEDIUM</option>
                          <option value="HARD">HARD</option>
                        </select>
                      </div>
                      <textarea placeholder="Problem Description..." value={problem.description} onChange={e => setProblem({ ...problem, description: e.target.value })} style={{ ...inputStyle, minHeight: '110px', resize: 'vertical' }} required />
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 2fr', gap: '16px' }}>
                        <input placeholder="Method Name" value={problem.methodName} onChange={e => setProblem({ ...problem, methodName: e.target.value })} style={inputStyle} required />
                        <input placeholder="Return Type" value={problem.returnType} onChange={e => setProblem({ ...problem, returnType: e.target.value })} style={inputStyle} required />
                        <input placeholder="Param Types (int[],int)" value={problem.parameterTypes?.join(',')} onChange={e => setProblem({ ...problem, parameterTypes: e.target.value.split(',') })} style={inputStyle} required />
                      </div>
                      <textarea placeholder="Starting Java Code..." value={problem.startingCodeJava} onChange={e => setProblem({ ...problem, startingCodeJava: e.target.value })} style={{ ...inputStyle, minHeight: '160px', fontFamily: "'Share Tech Mono', monospace", color: '#ff7a00', resize: 'vertical' }} required />
                      <div style={{ padding: '20px', background: 'rgba(0,0,0,0.2)', border: '1px solid rgba(255,255,255,0.04)', borderRadius: '12px' }}>
                        <div style={{ marginBottom: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <div style={{ fontSize: '0.75rem', color: '#555', textTransform: 'uppercase', letterSpacing: '1px', fontWeight: 600 }}>Test Cases</div>
                          <button type="button" onClick={handleAddTestCase} className="btn-outline" style={{ padding: '5px 12px', fontSize: '0.78rem' }}>+ Add</button>
                        </div>
                        {problem.testCases.map((tc, index) => (
                          <div key={index} style={{ display: 'grid', gridTemplateColumns: '2fr 2fr auto', gap: '12px', alignItems: 'center', marginBottom: '12px' }}>
                            <input placeholder="Inputs (e.g. [1,2] | 3)" value={tc.inputs?.join(' | ')} onChange={e => handleTestCaseChange(index, 'inputs', e.target.value)} style={inputStyle} required />
                            <input placeholder="Expected Output" value={tc.expectedOutput} onChange={e => handleTestCaseChange(index, 'expectedOutput', e.target.value)} style={inputStyle} required />
                            <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', cursor: 'pointer', color: '#555', whiteSpace: 'nowrap' }}>
                              <input type="checkbox" checked={tc.hidden} onChange={e => handleTestCaseChange(index, 'hidden', e.target.checked)} /> Hidden
                            </label>
                          </div>
                        ))}
                      </div>
                      <button type="submit" className="btn-primary" style={{ alignSelf: 'flex-start', padding: '13px 32px' }}>
                        {mode === 'create' ? 'Deploy to Database' : 'Update Problem'}
                      </button>
                    </form>
                  </div>
                </div>
              </div>
            )}

            {/* ==================== OPERATORS ==================== */}
            {activeTab === 'operators' && (
              <div>
                <div className="section-heading">Operator Management</div>
                <div className="section-sub">Grant administrative access to new personnel.</div>
                <div className="gp" style={{ maxWidth: '600px' }}>
                  <div className="gp-header">
                    <div className="gp-title" style={{ '--td': '#8b5cf6' }}><div className="gp-title-dot" />Grant Access</div>
                  </div>
                  <div className="gp-body">
                    {adminStatus && <div className={`alert-box ${adminStatus.includes('ERROR') ? 'alert-error' : 'alert-success'}`}>{adminStatus}</div>}
                    <form onSubmit={handleCreateAdmin} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.72rem', color: '#555', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>Email Address</label>
                        <input type="email" placeholder="admin@o1outpost.dev" required value={newAdmin.email} onChange={e => setNewAdmin({ ...newAdmin, email: e.target.value })} style={inputStyle} />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '0.72rem', color: '#555', textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: '8px' }}>Password</label>
                        <input type="password" placeholder="••••••••••••" required value={newAdmin.password} onChange={e => setNewAdmin({ ...newAdmin, password: e.target.value })} style={inputStyle} />
                      </div>
                      <button type="submit" className="btn-primary" style={{ marginTop: '4px' }}>Grant Admin Access</button>
                    </form>
                  </div>
                </div>
              </div>
            )}

          </div>
        </main>
      </div>

      {/* CONFIRM DELETE MODAL */}
      {confirmDelete && (
        <div className="modal-overlay" onClick={() => setConfirmDelete(null)}>
          <div className="modal-box" onClick={e => e.stopPropagation()}>
            <h3>⚠ Confirm Deletion</h3>
            <p>
              You are about to permanently {confirmDelete.type === 'daily' ? 'cancel' : 'delete'}{' '}
              <strong style={{ color: '#fff' }}>"{confirmDelete.title}"</strong>.
              This action <strong style={{ color: '#f87171' }}>cannot be undone</strong>.
            </p>
            <div className="modal-actions">
              <button className="btn-outline" onClick={() => setConfirmDelete(null)} style={{ padding: '9px 18px' }}>Cancel</button>
              <button className="btn-primary" onClick={() => {
                if (confirmDelete.type === 'problem') handleDeleteProblem(confirmDelete.title);
                else handleDeleteDaily(confirmDelete.id);
              }} style={{ padding: '9px 18px', background: 'linear-gradient(135deg,#dc2626,#991b1b)' }}>
                Delete Permanently
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}