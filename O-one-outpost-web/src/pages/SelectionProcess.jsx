import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import CompanyNav from '../components/CompanyNav';

export default function SelectionProcess() {
  const [assessments, setAssessments] = useState([]);
  const [selectedAssessmentId, setSelectedAssessmentId] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const navigate = useNavigate();
  const getToken = () => localStorage.getItem('algo_arena_token');
  const currentUser = localStorage.getItem('algo_arena_user');

  useEffect(() => {
    fetchAssessments();
  }, []);

  useEffect(() => {
    if (selectedAssessmentId) {
      fetchCandidates(selectedAssessmentId);
    } else {
      setCandidates([]);
    }
  }, [selectedAssessmentId]);

  const fetchAssessments = async () => {
    setLoading(true);
    try {
      const res = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company/assessments`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      setAssessments(res.data);
    } catch (err) {
      setError('Failed to fetch tournaments.');
    } finally {
      setLoading(false);
    }
  };

  const fetchCandidates = async (assessmentId) => {
    try {
      const res = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/assessment/company/${assessmentId}/candidates`, {
        headers: { Authorization: `Bearer ${getToken()}` }
      });
      // Sort candidates by totalPoints descending, then timePenalty ascending
      const sorted = res.data.sort((a, b) => {
        if (b.totalPoints !== a.totalPoints) return b.totalPoints - a.totalPoints;
        return a.timePenaltySeconds - b.timePenaltySeconds;
      });
      setCandidates(sorted);
    } catch (err) {
      setError('Failed to fetch candidates for this tournament.');
    }
  };

  const formatTime = (seconds) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h > 0 ? `${h}h ` : ''}${m}m ${s}s`;
  };

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #0a0503; margin: 0; color: #fff; font-family: 'Inter', sans-serif; }

        .nav-container {
          position: sticky; top: 0; width: 100%; padding: 20px 40px; display: flex; justify-content: space-between; align-items: center;
          z-index: 100; backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px); border-bottom: 1px solid rgba(255,255,255,0.05); background: rgba(10, 5, 3, 0.8);
        }

        .nav-logo { font-family: 'Outfit', sans-serif; font-size: 1.5rem; font-weight: 800; letter-spacing: -0.5px; cursor: pointer; }
        .nav-links { display: flex; gap: 32px; font-size: 0.95rem; color: #a1a1aa; font-weight: 500; }
        .nav-links span { cursor: pointer; transition: color 0.2s; }
        .nav-links span:hover, .nav-links span.active { color: #fff; }
        .nav-profile { display: flex; align-items: center; gap: 20px; }

        .glass-panel {
          background: rgba(15, 8, 3, 0.4); backdrop-filter: blur(12px); border: 1px solid rgba(255, 122, 0, 0.15); border-radius: 24px; padding: 40px;
          box-shadow: 0 20px 40px rgba(0,0,0,0.4), inset 0 0 20px rgba(255,255,255,0.02);
        }

        .select-style {
          width: 100%; padding: 16px; background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff;
          outline: none; font-family: 'Inter', sans-serif; border-radius: 12px; font-size: 1rem; transition: all 0.3s;
          appearance: none; cursor: pointer;
        }
        .select-style:focus { border-color: #ff7a00; }
        
        .candidate-table { width: 100%; border-collapse: collapse; margin-top: 24px; }
        .candidate-table th { text-align: left; padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.1); color: #888; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 1px; }
        .candidate-table td { padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.03); color: #fff; font-size: 0.95rem; }
        .candidate-table tr:hover td { background: rgba(255,122,0,0.05); }

        .status-badge { padding: 6px 12px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; display: inline-block; }
        .status-completed { background: rgba(39,201,63,0.1); color: #27c93f; border: 1px solid rgba(39,201,63,0.3); }
        .status-inprogress { background: rgba(255,122,0,0.1); color: #ff7a00; border: 1px solid rgba(255,122,0,0.3); }
        .status-terminated { background: rgba(239,68,68,0.1); color: #ef4444; border: 1px solid rgba(239,68,68,0.3); }
        .status-registered { background: rgba(255,255,255,0.05); color: #aaa; border: 1px solid rgba(255,255,255,0.1); }
      `}</style>

      <div style={{ minHeight: '100vh', position: 'relative' }}>
        <div style={{ position: 'fixed', inset: 0, background: 'radial-gradient(ellipse at top right, rgba(255, 122, 0, 0.08) 0%, transparent 60%)', pointerEvents: 'none', zIndex: 0 }} />

        <CompanyNav />

        <div style={{ position: 'relative', zIndex: 10, maxWidth: '1200px', margin: '60px auto', padding: '0 40px' }}>
          
          <div style={{ marginBottom: '40px' }}>
            <h1 style={{ fontFamily: 'Outfit', fontSize: '2.5rem', fontWeight: 800, marginBottom: '8px' }}>Candidate Selection</h1>
            <p style={{ color: '#888', fontSize: '1.1rem' }}>Evaluate performance metrics and select the best engineers for your company.</p>
          </div>

          <div className="glass-panel" style={{ marginBottom: '40px' }}>
            <div style={{ marginBottom: '16px', color: '#888', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '1px', fontWeight: 700 }}>Select Tournament</div>
            
            {loading ? (
              <div style={{ padding: '16px', color: '#ff7a00' }}>Loading tournaments...</div>
            ) : error ? (
              <div style={{ padding: '16px', background: 'rgba(239,68,68,0.1)', color: '#ef4444', borderRadius: '12px' }}>{error}</div>
            ) : (
              <div style={{ position: 'relative' }}>
                <select 
                  className="select-style" 
                  value={selectedAssessmentId || ''} 
                  onChange={(e) => setSelectedAssessmentId(e.target.value)}
                >
                  <option value="" disabled>-- Select a Tournament to View Candidates --</option>
                  {assessments.map(a => (
                    <option key={a.id} value={a.id}>
                      {a.title} ({new Date(a.startTime).toLocaleDateString()}) - Code: {a.testCode}
                    </option>
                  ))}
                </select>
                <div style={{ position: 'absolute', right: '20px', top: '16px', pointerEvents: 'none', color: '#888' }}>▼</div>
              </div>
            )}
          </div>

          {selectedAssessmentId && (
            <div className="glass-panel">
              <h2 style={{ fontFamily: 'Outfit', fontSize: '1.5rem', fontWeight: 700, marginBottom: '24px' }}>Leaderboard & Performance</h2>
              
              {candidates.length === 0 ? (
                <div style={{ padding: '40px', textAlign: 'center', color: '#888' }}>
                  No candidates have registered for this tournament yet.
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="candidate-table">
                    <thead>
                      <tr>
                        <th>Rank</th>
                        <th>Candidate ID</th>
                        <th>Status</th>
                        <th>Total Score</th>
                        <th>Time Penalty</th>
                      </tr>
                    </thead>
                    <tbody>
                      {candidates.map((c, idx) => (
                        <tr key={c.id}>
                          <td style={{ fontWeight: 700, color: idx < 3 ? '#ff7a00' : '#888' }}>#{idx + 1}</td>
                          <td style={{ fontFamily: "'Share Tech Mono', monospace" }}>{c.candidateId}</td>
                          <td>
                            <span className={`status-badge status-${c.status.toLowerCase().replace('_', '')}`}>
                              {c.status}
                            </span>
                          </td>
                          <td style={{ fontWeight: 700, fontSize: '1.2rem', color: '#fff' }}>{c.totalPoints}</td>
                          <td style={{ color: '#aaa', fontFamily: "'Share Tech Mono', monospace" }}>
                            {c.totalPoints > 0 ? formatTime(c.timePenaltySeconds) : '--'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

        </div>
      </div>
    </>
  );
}
