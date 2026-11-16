import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { candidateApi } from '../services/candidateApi';
import { formatDurationSeconds, shortCandidateId } from '../utils/datetime';
import { getApiErrorMessage } from '../utils/errors';
import EngineerNav from './EngineerNav';

const RANK_STYLES = [
    { icon: 'bi-trophy-fill', className: 'rank-gold', label: '1st' },
    { icon: 'bi-trophy-fill', className: 'rank-silver', label: '2nd' },
    { icon: 'bi-trophy-fill', className: 'rank-bronze', label: '3rd' },
];

export default function AssessmentLeaderboard() {
    const { assessmentId } = useParams();
    const navigate = useNavigate();

    const [leaderboard, setLeaderboard] = useState([]);
    const [isLocked, setIsLocked] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let active = true;
        if (!assessmentId) {
            setError('No assessment was selected.');
            setLoading(false);
            return undefined;
        }

        candidateApi
            .getLeaderboard(assessmentId)
            .then((data) => {
                if (!active) return;
                setLeaderboard(Array.isArray(data) ? data : []);
                setIsLocked(false);
            })
            .catch((requestError) => {
                if (!active) return;
                if (requestError.response?.status === 403 || requestError.message === 'BLACKOUT_ACTIVE') {
                    setIsLocked(true);
                } else {
                    setError(
                        getApiErrorMessage(requestError, 'Unable to load the leaderboard.'),
                    );
                }
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [assessmentId]);

    const pageStyle = `
        * { box-sizing: border-box; margin: 0; padding: 0; }
        .lb-page { min-height: 100vh; background: #0a0503; font-family: 'Inter', sans-serif; color: #fff; }
        .lb-shell { max-width: 940px; margin: 0 auto; padding: 56px 32px 80px; }
        .lb-top { display: flex; justify-content: space-between; align-items: center; gap: 18px; flex-wrap: wrap; margin-bottom: 32px; }
        .lb-title { text-align: center; font-family: 'Outfit', sans-serif; font-size: 2.6rem; font-weight: 800; letter-spacing: -1px; }
        .lb-sub { color: #888; text-align: center; margin-top: 8px; font-size: 0.95rem; }
        .btn-back {
            display: inline-flex; align-items: center; gap: 8px;
            background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.12);
            color: #d4d4d8; padding: 10px 16px; border-radius: 10px;
            cursor: pointer; font-family: 'Inter', sans-serif; font-size: 0.85rem;
            transition: all 0.2s;
        }
        .btn-back:hover { border-color: rgba(255,122,0,0.5); color: #ff7a00; }
        .glass-lock, .glass-table-container {
            background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
            border: 1px solid rgba(255, 122, 0, 0.15);
            border-radius: 20px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
            backdrop-filter: blur(12px);
            overflow: hidden;
        }
        .glass-lock { padding: 50px; text-align: center; max-width: 560px; margin: 40px auto 0; }
        .lock-icon { font-size: 2.6rem; color: rgba(255,122,0,0.7); margin-bottom: 18px; display: block; }
        .lock-title { font-family: 'Outfit', sans-serif; font-size: 1.9rem; font-weight: 800; color: #fff; margin-bottom: 14px; }
        .lock-desc { color: #a1a1aa; font-family: 'Share Tech Mono', monospace; font-size: 0.88rem; line-height: 1.7; }
        .lb-table { width: 100%; border-collapse: collapse; text-align: left; }
        .lb-th {
            background: rgba(255,122,0,0.05); padding: 18px 20px; color: #ff7a00;
            font-family: 'Share Tech Mono', monospace; font-size: 0.76rem;
            text-transform: uppercase; letter-spacing: 2px;
            border-bottom: 1px solid rgba(255,122,0,0.2); white-space: nowrap;
        }
        .lb-th.right { text-align: right; }
        .lb-tr { border-bottom: 1px solid rgba(255,255,255,0.05); transition: background 0.2s; }
        .lb-tr:hover { background: rgba(255,122,0,0.03); }
        .lb-tr:last-child { border-bottom: none; }
        .lb-td { padding: 18px 20px; }
        .rank-cell { font-family: 'Outfit', sans-serif; font-size: 1rem; font-weight: 800; display: inline-flex; align-items: center; gap: 8px; }
        .rank-gold { color: #ffd700; text-shadow: 0 0 10px rgba(255,215,0,0.5); }
        .rank-silver { color: #c0c0c0; text-shadow: 0 0 10px rgba(192,192,192,0.5); }
        .rank-bronze { color: #cd7f32; text-shadow: 0 0 10px rgba(205,127,50,0.5); }
        .rank-other { color: #666; font-family: 'Share Tech Mono', monospace; font-size: 0.9rem; }
        .id-cell { font-family: 'Share Tech Mono', monospace; color: #a1a1aa; font-size: 0.9rem; }
        .pts-cell { text-align: right; font-family: 'Outfit', sans-serif; font-size: 1.15rem; font-weight: 800; color: #00ffb4; }
        .pts-label { font-size: 0.7rem; color: #00ffb4; opacity: 0.6; margin-left: 4px; }
        .time-cell { text-align: right; font-family: 'Share Tech Mono', monospace; color: #a1a1aa; font-size: 0.85rem; }
        .empty-cell { text-align: center; padding: 60px 20px; color: #666; font-family: 'Share Tech Mono', monospace; letter-spacing: 2px; }
        .empty-cell i { display: block; font-size: 2rem; margin-bottom: 12px; color: #3f3f46; }
        .lb-error { max-width: 560px; margin: 40px auto 0; padding: 24px; border-radius: 16px; background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.3); color: #fca5a5; text-align: center; font-size: 0.9rem; }
        .lb-loading { min-height: 60vh; display: flex; align-items: center; justify-content: center; color: #ff7a00; font-family: 'Share Tech Mono', monospace; letter-spacing: 3px; font-size: 0.9rem; }
        @media (max-width: 640px) {
            .lb-shell { padding: 36px 16px 60px; }
            .lb-title { font-size: 1.9rem; }
            .glass-lock { padding: 32px 22px; }
        }
    `;

    if (loading) {
        return (
            <div className="lb-page">
                <style>{pageStyle}</style>
                <EngineerNav />
                <div className="lb-loading">CALCULATING FINAL SCORES...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="lb-page">
                <style>{pageStyle}</style>
                <EngineerNav />
                <div className="lb-shell">
                    <button type="button" className="btn-back" onClick={() => navigate('/candidate/dashboard')}>
                        <i className="bi bi-arrow-left" aria-hidden="true" />
                        Dashboard
                    </button>
                    <div className="lb-error">{error}</div>
                </div>
            </div>
        );
    }

    if (isLocked) {
        return (
            <div className="lb-page">
                <style>{pageStyle}</style>
                <EngineerNav />
                <div className="lb-shell">
                    <div className="lb-top">
                        <button type="button" className="btn-back" onClick={() => navigate('/candidate/dashboard')}>
                            <i className="bi bi-arrow-left" aria-hidden="true" />
                            Dashboard
                        </button>
                    </div>
                    <div className="glass-lock">
                        <i className="bi bi-lock-fill lock-icon" aria-hidden="true" />
                        <h2 className="lock-title">
                            Leaderboard <span style={{ color: '#ff7a00' }}>Locked</span>
                        </h2>
                        <p className="lock-desc">
                            The assessment is still active. Rankings are hidden until the global timer
                            concludes so every candidate competes on the same information.
                        </p>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="lb-page">
            <style>{pageStyle}</style>
            <EngineerNav />
            <div className="lb-shell">
                <div className="lb-top">
                    <button type="button" className="btn-back" onClick={() => navigate('/candidate/dashboard')}>
                        <i className="bi bi-arrow-left" aria-hidden="true" />
                        Dashboard
                    </button>
                </div>

                <div style={{ textAlign: 'center', marginBottom: '32px' }}>
                    <h2 className="lb-title">
                        FINAL <span style={{ color: '#ff7a00' }}>RANKINGS</span>
                    </h2>
                    <p className="lb-sub">Ranked by total points, then by earliest scoring time.</p>
                </div>

                <div className="glass-table-container">
                    <table className="lb-table">
                        <thead>
                            <tr>
                                <th className="lb-th">Rank</th>
                                <th className="lb-th">Candidate</th>
                                <th className="lb-th right">Total points</th>
                                <th className="lb-th right">Time</th>
                            </tr>
                        </thead>
                        <tbody>
                            {leaderboard.length === 0 ? (
                                <tr>
                                    <td colSpan="4" className="empty-cell">
                                        <i className="bi bi-clipboard-x" aria-hidden="true" />
                                        NO SUBMISSIONS RECORDED.
                                    </td>
                                </tr>
                            ) : (
                                leaderboard.map((record, index) => {
                                    const rank = RANK_STYLES[index];
                                    return (
                                        <tr key={record.id || record.candidateId || index} className="lb-tr">
                                            <td className="lb-td">
                                                {rank ? (
                                                    <span className={`rank-cell ${rank.className}`}>
                                                        <i className={`bi ${rank.icon}`} aria-hidden="true" />
                                                        {rank.label}
                                                    </span>
                                                ) : (
                                                    <span className="rank-cell rank-other">#{index + 1}</span>
                                                )}
                                            </td>
                                            <td className="lb-td id-cell" title={record.candidateId || ''}>
                                                {shortCandidateId(record.candidateId)}
                                            </td>
                                            <td className="lb-td pts-cell">
                                                {record.totalPoints ?? 0}
                                                <span className="pts-label">PTS</span>
                                            </td>
                                            <td className="lb-td time-cell">
                                                {formatDurationSeconds(record.totalTimePenaltySeconds ?? 0)}
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
