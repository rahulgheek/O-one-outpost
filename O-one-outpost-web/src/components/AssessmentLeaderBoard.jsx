import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom'; // 🚨 NEW: Import useParams
import { candidateApi } from '../services/candidateApi';

// 🚨 REMOVED { assessmentId } from props
export default function AssessmentLeaderboard() {
    const { assessmentId } = useParams(); // 🚨 NEW: Grab ID from the URL!

    const [leaderboard, setLeaderboard] = useState([]);
    const [isLocked, setIsLocked] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchRankings = async () => {
            try {
                const data = await candidateApi.getLeaderboard(assessmentId);
                setLeaderboard(data);
                setIsLocked(false);
            } catch (err) {
                // Check if the backend threw your specific SecurityException for the blackout period
                if (err.response?.status === 403 || err.message === 'BLACKOUT_ACTIVE') {
                    setIsLocked(true);
                } else {
                    setError('Unable to load leaderboard. ' + (err.response?.data || ''));
                }
            } finally {
                setLoading(false);
            }
        };

        if (assessmentId) {
            fetchRankings();
        }
    }, [assessmentId]);

    if (loading) {
        return <div className="text-white text-center p-10 font-mono tracking-widest">CALCULATING FINAL SCORES...</div>;
    }

    if (error) {
        return <div className="text-red-500 text-center p-10 font-mono">{error}</div>;
    }

    // 🛡️ The Blackout UI (If the timer hasn't hit zero yet)
    if (isLocked) {
        return (
            <div className="lb-page">
                <style>{`
                    .lb-page { min-height: 100vh; background: #0a0503; font-family: 'Inter', sans-serif; display: flex; align-items: center; justify-content: center; }
                    .glass-lock {
                        background: linear-gradient(135deg, rgba(20,10,5,0.8), rgba(0,0,0,0.9));
                        border: 1px solid rgba(255,122,0,0.15);
                        border-radius: 20px;
                        padding: 50px;
                        text-align: center;
                        box-shadow: 0 20px 50px rgba(0,0,0,0.5), inset 0 0 20px rgba(255,122,0,0.02);
                        backdrop-filter: blur(10px);
                        max-width: 500px;
                    }
                    .lock-icon { width: 64px; height: 64px; color: rgba(255,122,0,0.5); margin: 0 auto 20px; }
                    .lock-title { font-family: 'Outfit', sans-serif; font-size: 2rem; font-weight: 800; color: #fff; margin-bottom: 16px; }
                    .lock-desc { color: #a1a1aa; font-family: 'Share Tech Mono', monospace; font-size: 0.95rem; line-height: 1.6; }
                `}</style>
                <div className="glass-lock">
                    <svg className="lock-icon" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
                    </svg>
                    <h2 className="lock-title">Leaderboard <span style={{ color: '#ff7a00' }}>Locked</span></h2>
                    <p className="lock-desc">
                        The assessment is still active. To ensure fairness and prevent panic, rankings are hidden until the global timer concludes. Check back soon!
                    </p>
                </div>
            </div>
        );
    }

    // 🏆 The Final Podium UI
    return (
        <div className="lb-page">
            <style>{`
                * { box-sizing: border-box; margin: 0; padding: 0; }
                .lb-page { min-height: 100vh; background: #0a0503; font-family: 'Inter', sans-serif; padding: 60px 20px; color: #fff; }
                .lb-container { max-width: 900px; margin: 0 auto; }
                .lb-title {
                    text-align: center; font-family: 'Outfit', sans-serif; font-size: 3rem; font-weight: 800;
                    margin-bottom: 40px; letter-spacing: -1px;
                }
                .glass-table-container {
                    background: linear-gradient(135deg, rgba(20,10,5,0.9), rgba(0,0,0,0.95));
                    border: 1px solid rgba(255, 122, 0, 0.15);
                    border-radius: 20px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                    backdrop-filter: blur(12px);
                    overflow: hidden;
                }
                .lb-table { width: 100%; border-collapse: collapse; text-align: left; }
                .lb-th {
                    background: rgba(255,122,0,0.05);
                    padding: 20px;
                    color: #ff7a00;
                    font-family: 'Share Tech Mono', monospace;
                    font-size: 0.9rem;
                    text-transform: uppercase;
                    letter-spacing: 2px;
                    border-bottom: 1px solid rgba(255,122,0,0.2);
                }
                .lb-th.right { text-align: right; }
                .lb-tr {
                    border-bottom: 1px solid rgba(255,255,255,0.05);
                    transition: all 0.2s;
                }
                .lb-tr:hover { background: rgba(255,122,0,0.03); }
                .lb-tr:last-child { border-bottom: none; }
                .lb-td { padding: 20px; }
                .rank-cell { font-family: 'Outfit', sans-serif; font-size: 1.2rem; font-weight: 800; }
                .rank-gold { color: #ffd700; text-shadow: 0 0 10px rgba(255,215,0,0.5); }
                .rank-silver { color: #c0c0c0; text-shadow: 0 0 10px rgba(192,192,192,0.5); }
                .rank-bronze { color: #cd7f32; text-shadow: 0 0 10px rgba(205,127,50,0.5); }
                .rank-other { color: #666; font-family: 'Share Tech Mono', monospace; font-size: 1rem; }
                .id-cell { font-family: 'Share Tech Mono', monospace; color: #a1a1aa; font-size: 1rem; }
                .pts-cell { text-align: right; font-family: 'Outfit', sans-serif; font-size: 1.2rem; font-weight: 800; color: #00ffb4; }
                .pts-label { font-size: 0.75rem; color: #00ffb4; opacity: 0.6; margin-left: 4px; }
                .time-cell { text-align: right; font-family: 'Share Tech Mono', monospace; color: #a1a1aa; font-size: 0.95rem; }
                .empty-cell { text-align: center; padding: 60px; color: #666; font-family: 'Share Tech Mono', monospace; letter-spacing: 2px; }
            `}</style>
            
            <div className="lb-container">
                <h2 className="lb-title">
                    FINAL <span style={{ color: '#ff7a00' }}>RANKINGS</span>
                </h2>
                
                <div className="glass-table-container">
                    <table className="lb-table">
                        <thead>
                            <tr>
                                <th className="lb-th">Rank</th>
                                <th className="lb-th">Candidate ID</th>
                                <th className="lb-th right">Total Points</th>
                                <th className="lb-th right">Time Penalty</th>
                            </tr>
                        </thead>
                        <tbody>
                            {leaderboard.length === 0 ? (
                                <tr>
                                    <td colSpan="4" className="empty-cell">
                                        NO SUBMISSIONS RECORDED.
                                    </td>
                                </tr>
                            ) : (
                                leaderboard.map((record, index) => (
                                    <tr key={record.id} className="lb-tr">
                                        <td className="lb-td">
                                            {index === 0 ? <span className="rank-cell rank-gold">🥇 1ST</span> : 
                                             index === 1 ? <span className="rank-cell rank-silver">🥈 2ND</span> : 
                                             index === 2 ? <span className="rank-cell rank-bronze">🥉 3RD</span> : 
                                             <span className="rank-cell rank-other">#{index + 1}</span>}
                                        </td>
                                        <td className="lb-td id-cell">
                                            {record.candidateId.substring(0, 8)}...
                                        </td>
                                        <td className="lb-td pts-cell">
                                            {record.totalPoints} <span className="pts-label">PTS</span>
                                        </td>
                                        <td className="lb-td time-cell">
                                            {Math.floor(record.totalTimePenaltySeconds / 60)}m {record.totalTimePenaltySeconds % 60}s
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}