import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import EngineerNav from './EngineerNav';

export default function CandidateDashboard() {
    const [tournaments, setTournaments] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [activeTab, setActiveTab] = useState('ACTIVE'); // 'ACTIVE' or 'COMPLETED'
    const navigate = useNavigate();

    useEffect(() => {
        const fetchDashboard = async () => {
            try {
                const data = await candidateApi.getMyTournaments();
                setTournaments(data);
            } catch (error) {
                console.error("Failed to load dashboard:", error);
            } finally {
                setIsLoading(false);
            }
        };
        fetchDashboard();
    }, []);

    // ─── TIME LOGIC ──────────────────────────────────────────────
    const now = new Date();

    // 1. Filter into Active (Upcoming + Ongoing) vs Completed
    const activeTournaments = tournaments.filter(t => new Date(t.endTime) > now);
    const completedTournaments = tournaments.filter(t => new Date(t.endTime) <= now);

    // 2. Helper to determine precise status for Active ones
    const getTournamentStatus = (t) => {
        const startTime = new Date(t.startTime);
        const endTime = new Date(t.endTime);
        
        if (now < startTime) return { label: 'UPCOMING', color: 'text-yellow-400 bg-yellow-400/10' };
        if (now >= startTime && now <= endTime) return { label: 'ONGOING', color: 'text-green-400 bg-green-400/10 border-green-500/50' };
        return { label: 'COMPLETED', color: 'text-slate-400 bg-slate-800' };
    };

    if (isLoading) {
        return <div className="min-h-screen bg-slate-950 flex items-center justify-center text-white font-mono tracking-widest">LOADING DASHBOARD...</div>;
    }

    return (
        <div className="dash-page">
            <style>{`
                * { box-sizing: border-box; margin: 0; padding: 0; }
                .dash-page {
                    min-height: 100vh;
                    background: #0a0503;
                    font-family: 'Inter', sans-serif;
                    color: #fff;
                    position: relative;
                }
                .dash-container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 60px 40px;
                    position: relative;
                    z-index: 10;
                }
                .dash-header {
                    margin-bottom: 50px;
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-end;
                    border-bottom: 1px solid rgba(255,122,0,0.15);
                    padding-bottom: 30px;
                }
                .dash-title {
                    font-family: 'Outfit', sans-serif;
                    font-size: 3rem;
                    font-weight: 800;
                    letter-spacing: -1px;
                    margin-bottom: 8px;
                }
                .dash-subtitle {
                    color: #a1a1aa;
                    font-size: 1.1rem;
                    font-family: 'Share Tech Mono', monospace;
                }
                .tabs-container {
                    display: flex;
                    gap: 10px;
                    background: rgba(15,8,3,0.6);
                    padding: 6px;
                    border-radius: 12px;
                    border: 1px solid rgba(255,122,0,0.1);
                }
                .tab-btn {
                    padding: 10px 24px;
                    border-radius: 8px;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.9rem;
                    font-weight: bold;
                    letter-spacing: 1px;
                    cursor: pointer;
                    transition: all 0.3s;
                    border: 1px solid transparent;
                }
                .tab-btn.active {
                    background: linear-gradient(45deg, rgba(255,61,0,0.15), rgba(255,122,0,0.15));
                    border-color: rgba(255,122,0,0.4);
                    color: #ff7a00;
                    box-shadow: 0 0 20px rgba(255,122,0,0.1);
                }
                .tab-btn.inactive {
                    background: transparent;
                    color: #666;
                }
                .tab-btn.inactive:hover {
                    color: #aaa;
                    background: rgba(255,255,255,0.02);
                }
                .dash-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
                    gap: 30px;
                }
                .dash-card {
                    background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 20px;
                    padding: 30px;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
                    backdrop-filter: blur(12px);
                    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                    position: relative;
                    overflow: hidden;
                }
                .dash-card::before {
                    content: '';
                    position: absolute;
                    top: 0; left: 0; width: 100%; height: 4px;
                    background: linear-gradient(90deg, #ff3d00, #ff7a00);
                    opacity: 0.8;
                }
                .dash-card:hover {
                    transform: translateY(-8px);
                    border-color: rgba(255, 122, 0, 0.4);
                    box-shadow: 0 20px 40px rgba(255, 122, 0, 0.15), inset 0 0 20px rgba(255,122,0,0.05);
                }
                .dash-card.completed {
                    opacity: 0.6;
                    border-color: rgba(255,255,255,0.1);
                    filter: grayscale(0.5);
                }
                .dash-card.completed::before {
                    background: #444;
                }
                .dash-card.completed:hover {
                    transform: translateY(-4px);
                    border-color: rgba(255,255,255,0.2);
                    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                }
                .status-badge {
                    position: absolute;
                    top: 0;
                    right: 0;
                    font-size: 0.75rem;
                    font-weight: bold;
                    padding: 6px 12px;
                    border-bottom-left-radius: 12px;
                    font-family: 'Share Tech Mono', monospace;
                    letter-spacing: 1px;
                }
                .status-UPCOMING { color: #eab308; background: rgba(234,179,8,0.1); border-bottom: 1px solid rgba(234,179,8,0.2); border-left: 1px solid rgba(234,179,8,0.2); }
                .status-ONGOING { color: #00ffb4; background: rgba(0,255,180,0.1); border-bottom: 1px solid rgba(0,255,180,0.3); border-left: 1px solid rgba(0,255,180,0.3); box-shadow: 0 0 10px rgba(0,255,180,0.2); animation: pulse 2s infinite; }
                .status-COMPLETED { color: #a1a1aa; background: rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.1); border-left: 1px solid rgba(255,255,255,0.1); }
                .card-title {
                    font-size: 1.4rem;
                    font-weight: 800;
                    color: #fff;
                    font-family: 'Outfit', sans-serif;
                    letter-spacing: -0.5px;
                    margin-bottom: 12px;
                    padding-right: 80px;
                }
                .card-desc {
                    color: #a1a1aa;
                    font-size: 0.95rem;
                    line-height: 1.6;
                    margin-bottom: 30px;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                }
                .card-times {
                    font-size: 0.85rem;
                    font-family: 'Share Tech Mono', monospace;
                    color: #ff7a00;
                    margin-bottom: 30px;
                    background: rgba(255,122,0,0.03);
                    padding: 15px;
                    border-radius: 12px;
                    border: 1px dashed rgba(255,122,0,0.1);
                }
                .card-times.completed {
                    color: #a1a1aa;
                    background: rgba(255,255,255,0.02);
                    border-color: rgba(255,255,255,0.05);
                }
                .card-times span {
                    color: #fff;
                    margin-left: 8px;
                }
                .card-times div {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 8px;
                }
                .card-times div:last-child { margin-bottom: 0; }
                .btn-action {
                    width: 100%;
                    padding: 16px;
                    border-radius: 12px;
                    font-weight: 700;
                    font-family: 'Inter', sans-serif;
                    letter-spacing: 1px;
                    cursor: pointer;
                    transition: all 0.3s;
                    text-transform: uppercase;
                    font-size: 0.95rem;
                    border: none;
                }
                .btn-ONGOING {
                    background: linear-gradient(45deg, #059669, #10b981);
                    color: #fff;
                    box-shadow: 0 8px 20px rgba(16,185,129,0.25);
                }
                .btn-ONGOING:hover { transform: translateY(-2px); box-shadow: 0 12px 25px rgba(16,185,129,0.4); }
                .btn-UPCOMING {
                    background: linear-gradient(45deg, #ff3d00, #ff7a00);
                    color: #fff;
                    box-shadow: 0 8px 20px rgba(255,122,0,0.25);
                }
                .btn-UPCOMING:hover { transform: translateY(-2px); box-shadow: 0 12px 25px rgba(255,122,0,0.4); }
                .btn-COMPLETED {
                    background: transparent;
                    border: 1px solid rgba(255,255,255,0.2);
                    color: #a1a1aa;
                }
                .btn-COMPLETED:hover {
                    background: rgba(255,255,255,0.05);
                    color: #fff;
                    border-color: #fff;
                }
                .dash-empty {
                    grid-column: 1 / -1;
                    text-align: center;
                    color: #a1a1aa;
                    font-family: 'Share Tech Mono', monospace;
                    padding: 80px 20px;
                    background: rgba(255,255,255,0.02);
                    border: 1px dashed rgba(255,255,255,0.1);
                    border-radius: 16px;
                    letter-spacing: 2px;
                }
                .browse-link {
                    display: inline-block;
                    margin-top: 20px;
                    color: #ff7a00;
                    text-decoration: none;
                    border-bottom: 1px solid #ff7a00;
                    padding-bottom: 2px;
                    transition: all 0.2s;
                    cursor: pointer;
                }
                .browse-link:hover {
                    color: #ff3d00;
                    border-color: #ff3d00;
                }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.6; }
                }
            `}</style>
            <EngineerNav />
            <div className="dash-container">
                <header className="dash-header">
                    <div>
                        <h1 className="dash-title">My <span style={{ color: '#ff7a00' }}>Tournaments</span></h1>
                        <p className="dash-subtitle">Manage your upcoming matches and view past performance.</p>
                    </div>
                    <div className="tabs-container">
                        <button 
                            onClick={() => setActiveTab('ACTIVE')}
                            className={`tab-btn ${activeTab === 'ACTIVE' ? 'active' : 'inactive'}`}
                        >
                            ACTIVE ({activeTournaments.length})
                        </button>
                        <button 
                            onClick={() => setActiveTab('COMPLETED')}
                            className={`tab-btn ${activeTab === 'COMPLETED' ? 'active' : 'inactive'}`}
                        >
                            HISTORY ({completedTournaments.length})
                        </button>
                    </div>
                </header>

                <div className="dash-grid">
                    {(activeTab === 'ACTIVE' ? activeTournaments : completedTournaments).length === 0 ? (
                        <div className="dash-empty">
                            <p>NO {activeTab} TOURNAMENTS FOUND.</p>
                            {activeTab === 'ACTIVE' && (
                                <div onClick={() => navigate('/candidate/directory')} className="browse-link">
                                    Browse Public Directory
                                </div>
                            )}
                        </div>
                    ) : (
                        (activeTab === 'ACTIVE' ? activeTournaments : completedTournaments).map(t => {
                            const status = getTournamentStatus(t);
                            const isCompleted = activeTab === 'COMPLETED';

                            return (
                                <div key={t.id} className={`dash-card ${isCompleted ? 'completed' : ''}`}>
                                    <div className={`status-badge status-${status.label}`}>
                                        {status.label}
                                    </div>
                                    <div>
                                        <h2 className="card-title">{t.title}</h2>
                                        <p className="card-desc">{t.description}</p>
                                        <div className={`card-times ${isCompleted ? 'completed' : ''}`}>
                                            <div>
                                                <span>STARTS</span>
                                                <span style={{ color: '#fff', marginLeft: 'auto' }}>{new Date(t.startTime).toLocaleString()}</span>
                                            </div>
                                            <div>
                                                <span>ENDS</span>
                                                <span style={{ color: '#fff', marginLeft: 'auto' }}>{new Date(t.endTime).toLocaleString()}</span>
                                            </div>
                                        </div>
                                    </div>

                                    {!isCompleted ? (
                                        <button 
                                            onClick={() => navigate('/candidate/join', { state: { prefilledCode: t.testCode } })}
                                            className={`btn-action btn-${status.label}`}
                                        >
                                            {status.label === 'ONGOING' ? 'ENTER ARENA NOW' : 'VIEW INVITE CODE'}
                                        </button>
                                    ) : (
                                        <button 
                                            onClick={() => navigate(`/workspace/${t.id}/leaderboard`)}
                                            className="btn-action btn-COMPLETED"
                                        >
                                            VIEW LEADERBOARD
                                        </button>
                                    )}
                                </div>
                            );
                        })
                    )}
                </div>
            </div>
        </div>
    );
}