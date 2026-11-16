import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import { formatAssessmentDateTime } from '../utils/datetime';
import { getApiErrorMessage } from '../utils/errors';
import EngineerNav from './EngineerNav';

const SESSION_LABEL = {
    REGISTERED: 'Registered',
    IN_PROGRESS: 'In progress',
    COMPLETED: 'Completed',
    TERMINATED: 'Terminated',
};

const phaseClass = (phase) => {
    if (phase === 'ONGOING') return 'ONGOING';
    if (phase === 'ENDED') return 'COMPLETED';
    return 'UPCOMING';
};

export default function CandidateDashboard() {
    const [tournaments, setTournaments] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const [activeTab, setActiveTab] = useState('ACTIVE');
    const navigate = useNavigate();

    useEffect(() => {
        let active = true;
        candidateApi
            .getMyTournaments()
            .then((data) => {
                if (active) setTournaments(Array.isArray(data) ? data : []);
            })
            .catch((requestError) => {
                if (!active) return;
                setError(
                    getApiErrorMessage(requestError, 'Unable to load your tournaments.'),
                );
            })
            .finally(() => {
                if (active) setIsLoading(false);
            });

        return () => {
            active = false;
        };
    }, []);

    const activeTournaments = tournaments.filter((tournament) => tournament.phase !== 'ENDED');
    const completedTournaments = tournaments.filter((tournament) => tournament.phase === 'ENDED');
    const visibleTournaments = activeTab === 'ACTIVE' ? activeTournaments : completedTournaments;

    const renderAction = (tournament) => {
        const sessionFinished = ['COMPLETED', 'TERMINATED'].includes(tournament.registrationStatus);

        if (tournament.phase === 'ENDED' || sessionFinished) {
            return (
                <button
                    type="button"
                    onClick={() => navigate(`/workspace/${tournament.id}/leaderboard`)}
                    className="btn-action btn-COMPLETED"
                >
                    <i className="bi bi-bar-chart-line" aria-hidden="true" />
                    View leaderboard
                </button>
            );
        }

        if (tournament.phase === 'UPCOMING') {
            return (
                <button type="button" className="btn-action btn-UPCOMING" disabled>
                    <i className="bi bi-hourglass-split" aria-hidden="true" />
                    Opens {formatAssessmentDateTime(tournament.startTime)}
                </button>
            );
        }

        if (tournament.canEnter) {
            return (
                <button
                    type="button"
                    onClick={() => navigate(`/workspace/${tournament.id}`)}
                    className="btn-action btn-ONGOING"
                >
                    <i className="bi bi-box-arrow-in-right" aria-hidden="true" />
                    Enter arena now
                </button>
            );
        }

        return (
            <button type="button" className="btn-action btn-COMPLETED" disabled>
                <i className="bi bi-slash-circle" aria-hidden="true" />
                Entry window closed
            </button>
        );
    };

    if (isLoading) {
        return (
            <div className="dash-loading">
                <style>{`
                    .dash-loading {
                        min-height: 100vh; background: #0a0503; display: flex;
                        align-items: center; justify-content: center;
                        color: #ff7a00; font-family: 'Share Tech Mono', monospace;
                        letter-spacing: 3px; font-size: 0.9rem;
                    }
                `}</style>
                LOADING DASHBOARD...
            </div>
        );
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
                    padding: 60px 40px 80px;
                    position: relative;
                    z-index: 10;
                }
                .dash-header {
                    margin-bottom: 36px;
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-end;
                    gap: 24px;
                    flex-wrap: wrap;
                    border-bottom: 1px solid rgba(255,122,0,0.15);
                    padding-bottom: 28px;
                }
                .dash-title {
                    font-family: 'Outfit', sans-serif;
                    font-size: 2.75rem;
                    font-weight: 800;
                    letter-spacing: -1px;
                    margin-bottom: 8px;
                }
                .dash-subtitle {
                    color: #a1a1aa;
                    font-size: 1rem;
                    font-family: 'Share Tech Mono', monospace;
                }
                .dash-stats {
                    display: flex;
                    gap: 12px;
                    flex-wrap: wrap;
                    margin-bottom: 28px;
                }
                .stat-chip {
                    display: inline-flex;
                    align-items: center;
                    gap: 9px;
                    padding: 10px 16px;
                    border-radius: 12px;
                    background: rgba(255,255,255,0.03);
                    border: 1px solid rgba(255,255,255,0.08);
                    color: #a1a1aa;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.78rem;
                    letter-spacing: 1px;
                }
                .stat-chip i { color: #ff7a00; }
                .stat-chip strong { color: #fff; }
                .tabs-container {
                    display: flex;
                    gap: 10px;
                    background: rgba(15,8,3,0.6);
                    padding: 6px;
                    border-radius: 12px;
                    border: 1px solid rgba(255,122,0,0.1);
                }
                .tab-btn {
                    padding: 10px 22px;
                    border-radius: 8px;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.82rem;
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
                .tab-btn.inactive:hover { color: #aaa; background: rgba(255,255,255,0.02); }
                .dash-error {
                    padding: 18px;
                    margin-bottom: 26px;
                    border-radius: 12px;
                    background: rgba(239,68,68,0.08);
                    border: 1px solid rgba(239,68,68,0.3);
                    color: #fca5a5;
                    font-size: 0.9rem;
                }
                .dash-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
                    gap: 26px;
                }
                .dash-card {
                    background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 20px;
                    padding: 28px;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    transition: transform 0.3s, border-color 0.3s, box-shadow 0.3s;
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
                .dash-card.completed {
                    opacity: 0.72;
                    border-color: rgba(255,255,255,0.1);
                    filter: grayscale(0.35);
                }
                .dash-card.completed::before { background: #444; }
                .dash-card:hover {
                    transform: translateY(-6px);
                    border-color: rgba(255, 122, 0, 0.4);
                    box-shadow: 0 18px 36px rgba(255, 122, 0, 0.12);
                }
                .status-badge {
                    position: absolute;
                    top: 0;
                    right: 0;
                    font-size: 0.7rem;
                    font-weight: bold;
                    padding: 6px 12px;
                    border-bottom-left-radius: 12px;
                    font-family: 'Share Tech Mono', monospace;
                    letter-spacing: 1px;
                }
                .status-UPCOMING { color: #eab308; background: rgba(234,179,8,0.1); border-bottom: 1px solid rgba(234,179,8,0.2); border-left: 1px solid rgba(234,179,8,0.2); }
                .status-ONGOING { color: #00ffb4; background: rgba(0,255,180,0.1); border-bottom: 1px solid rgba(0,255,180,0.3); border-left: 1px solid rgba(0,255,180,0.3); }
                .status-COMPLETED { color: #a1a1aa; background: rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.1); border-left: 1px solid rgba(255,255,255,0.1); }
                .card-title {
                    font-size: 1.35rem;
                    font-weight: 800;
                    color: #fff;
                    font-family: 'Outfit', sans-serif;
                    letter-spacing: -0.5px;
                    margin-bottom: 10px;
                    padding-right: 90px;
                    line-height: 1.3;
                }
                .card-desc {
                    color: #a1a1aa;
                    font-size: 0.93rem;
                    line-height: 1.6;
                    margin-bottom: 20px;
                    display: -webkit-box;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;
                }
                .session-line {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    color: #71717a;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.75rem;
                    letter-spacing: 1px;
                    margin-bottom: 16px;
                }
                .session-line i { color: #ff7a00; }
                .card-times {
                    font-size: 0.8rem;
                    font-family: 'Share Tech Mono', monospace;
                    color: #ff7a00;
                    margin-bottom: 24px;
                    background: rgba(255,122,0,0.03);
                    padding: 14px 16px;
                    border-radius: 12px;
                    border: 1px dashed rgba(255,122,0,0.12);
                }
                .card-times.completed {
                    color: #a1a1aa;
                    background: rgba(255,255,255,0.02);
                    border-color: rgba(255,255,255,0.05);
                }
                .card-times div {
                    display: flex;
                    justify-content: space-between;
                    gap: 12px;
                    margin-bottom: 8px;
                }
                .card-times div:last-child { margin-bottom: 0; }
                .card-times span:last-child { color: #e4e4e7; text-align: right; }
                .btn-action {
                    width: 100%;
                    padding: 14px;
                    border-radius: 12px;
                    font-weight: 700;
                    font-family: 'Inter', sans-serif;
                    letter-spacing: 0.5px;
                    cursor: pointer;
                    transition: all 0.3s;
                    text-transform: uppercase;
                    font-size: 0.85rem;
                    border: none;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                }
                .btn-action:disabled { cursor: not-allowed; }
                .btn-ONGOING {
                    background: linear-gradient(45deg, #059669, #10b981);
                    color: #fff;
                    box-shadow: 0 8px 20px rgba(16,185,129,0.25);
                }
                .btn-ONGOING:hover { transform: translateY(-2px); box-shadow: 0 12px 25px rgba(16,185,129,0.4); }
                .btn-UPCOMING {
                    background: rgba(234,179,8,0.08);
                    border: 1px solid rgba(234,179,8,0.3);
                    color: #eab308;
                }
                .btn-UPCOMING:disabled { opacity: 1; }
                .btn-COMPLETED {
                    background: transparent;
                    border: 1px solid rgba(255,255,255,0.2);
                    color: #a1a1aa;
                }
                .btn-COMPLETED:hover:not(:disabled) {
                    background: rgba(255,255,255,0.05);
                    color: #fff;
                    border-color: #fff;
                }
                .btn-COMPLETED:disabled { opacity: 0.6; }
                .dash-empty {
                    grid-column: 1 / -1;
                    text-align: center;
                    color: #a1a1aa;
                    font-family: 'Share Tech Mono', monospace;
                    padding: 70px 20px;
                    background: rgba(255,255,255,0.02);
                    border: 1px dashed rgba(255,255,255,0.1);
                    border-radius: 16px;
                    letter-spacing: 1px;
                }
                .dash-empty i { display: block; font-size: 2.4rem; color: #3f3f46; margin-bottom: 14px; }
                .browse-link {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    margin-top: 20px;
                    color: #ff7a00;
                    text-decoration: none;
                    border-bottom: 1px solid #ff7a00;
                    padding-bottom: 2px;
                    transition: all 0.2s;
                    cursor: pointer;
                    background: none;
                    border-top: none;
                    border-left: none;
                    border-right: none;
                    font-family: inherit;
                    font-size: 0.9rem;
                }
                .browse-link:hover { color: #ff3d00; border-color: #ff3d00; }
                @media (max-width: 640px) {
                    .dash-container { padding: 40px 20px 60px; }
                    .dash-title { font-size: 2rem; }
                    .dash-grid { grid-template-columns: 1fr; }
                }
            `}</style>

            <EngineerNav />
            <div className="dash-container">
                <header className="dash-header">
                    <div>
                        <h1 className="dash-title">
                            My <span style={{ color: '#ff7a00' }}>Tournaments</span>
                        </h1>
                        <p className="dash-subtitle">Manage your matches and review past performance.</p>
                    </div>
                    <div className="tabs-container">
                        <button
                            type="button"
                            onClick={() => setActiveTab('ACTIVE')}
                            className={`tab-btn ${activeTab === 'ACTIVE' ? 'active' : 'inactive'}`}
                        >
                            ACTIVE ({activeTournaments.length})
                        </button>
                        <button
                            type="button"
                            onClick={() => setActiveTab('COMPLETED')}
                            className={`tab-btn ${activeTab === 'COMPLETED' ? 'active' : 'inactive'}`}
                        >
                            HISTORY ({completedTournaments.length})
                        </button>
                    </div>
                </header>

                <div className="dash-stats">
                    <span className="stat-chip">
                        <i className="bi bi-collection" aria-hidden="true" />
                        Registered <strong>{tournaments.length}</strong>
                    </span>
                    <span className="stat-chip">
                        <i className="bi bi-broadcast" aria-hidden="true" />
                        Active <strong>{activeTournaments.length}</strong>
                    </span>
                    <span className="stat-chip">
                        <i className="bi bi-check2-circle" aria-hidden="true" />
                        Completed <strong>{completedTournaments.length}</strong>
                    </span>
                </div>

                {error && <div className="dash-error">{error}</div>}

                <div className="dash-grid">
                    {visibleTournaments.length === 0 ? (
                        <div className="dash-empty">
                            <i className="bi bi-calendar2-x" aria-hidden="true" />
                            <p>NO {activeTab} TOURNAMENTS FOUND.</p>
                            {activeTab === 'ACTIVE' && (
                                <button
                                    type="button"
                                    onClick={() => navigate('/candidate/directory')}
                                    className="browse-link"
                                >
                                    <i className="bi bi-compass" aria-hidden="true" />
                                    Browse public directory
                                </button>
                            )}
                        </div>
                    ) : (
                        visibleTournaments.map((tournament) => {
                            const isCompleted = tournament.phase === 'ENDED';
                            return (
                                <div
                                    key={tournament.id}
                                    className={`dash-card ${isCompleted ? 'completed' : ''}`}
                                >
                                    <div className={`status-badge status-${phaseClass(tournament.phase)}`}>
                                        {tournament.phase}
                                    </div>
                                    <div>
                                        <h2 className="card-title">{tournament.title}</h2>
                                        <p className="card-desc">
                                            {tournament.description || 'No description provided.'}
                                        </p>
                                        <div className="session-line">
                                            <i className="bi bi-person-badge" aria-hidden="true" />
                                            {SESSION_LABEL[tournament.registrationStatus] || tournament.registrationStatus}
                                            <span>·</span>
                                            {tournament.problemCount} problem{tournament.problemCount === 1 ? '' : 's'}
                                        </div>
                                        <div className={`card-times ${isCompleted ? 'completed' : ''}`}>
                                            <div>
                                                <span>STARTS</span>
                                                <span>{formatAssessmentDateTime(tournament.startTime)}</span>
                                            </div>
                                            <div>
                                                <span>ENDS</span>
                                                <span>{formatAssessmentDateTime(tournament.endTime)}</span>
                                            </div>
                                            {tournament.deadline && tournament.registrationStatus === 'IN_PROGRESS' && (
                                                <div>
                                                    <span>YOUR DEADLINE</span>
                                                    <span>{formatAssessmentDateTime(tournament.deadline)}</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                    {renderAction(tournament)}
                                </div>
                            );
                        })
                    )}
                </div>
            </div>
        </div>
    );
}
