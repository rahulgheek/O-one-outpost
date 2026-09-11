import { useParams } from 'react-router-dom';
import { useState, useEffect } from 'react';
import axios from 'axios';
import { candidateApi } from '../services/candidateApi';

export default function Profile({ getToken }) {
  const { username } = useParams();
  const [profile, setProfile] = useState(null);
  const [tournaments, setTournaments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        const profileRes = await axios.get(`${import.meta.env.VITE_API_GATEWAY_URL}/api/profiles/${username}`, {
          headers: { Authorization: `Bearer ${getToken()}` },
        });
        setProfile(profileRes.data);

        try {
          const tournamentData = await candidateApi.getMyTournaments();
          setTournaments(tournamentData);
        } catch (tErr) {
          console.warn("Could not fetch tournaments (viewing another user's profile).");
        }
      } catch (error) {
        console.error("Failed to load profile:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchProfileData();
  }, [username, getToken]);

  if (loading) return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-tertiary)', fontFamily: 'var(--font-mono)', letterSpacing: '4px', fontSize: '1rem' }}>
      <div className="animate-fade-in" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}>
        <div style={{ width: '40px', height: '40px', border: '3px solid var(--border-glass-strong)', borderTopColor: 'var(--accent-purple)', borderRadius: '50%', animation: 'spin 1s linear infinite' }} />
        RETRIEVING DOSSIER...
      </div>
      <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
    </div>
  );

  if (!profile) return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-red)', fontFamily: 'var(--font-mono)', letterSpacing: '4px', fontSize: '1.2rem', textShadow: '0 0 20px rgba(239,68,68,0.5)' }}>
      OPERATOR NOT FOUND
    </div>
  );

  const winRate = profile.matchesPlayed > 0 ? ((profile.wins / profile.matchesPlayed) * 100).toFixed(1) : '0.0';

  return (
    <div style={{ minHeight: 'calc(100vh - 64px)', padding: '60px', background: 'var(--bg-primary)' }}>
      <div className="animate-slide-up" style={{ maxWidth: '800px', margin: '0 auto' }}>

        {/* --- 1v1 ARENA HEADER --- */}
        <div className="glass-panel" style={{ padding: '40px', textAlign: 'center', marginBottom: '40px', background: 'radial-gradient(circle at center, rgba(139,92,246,0.1), transparent 70%)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <div style={{ marginBottom: '16px', color: 'var(--text-tertiary)', fontSize: '0.8rem', letterSpacing: '4px', fontFamily: 'var(--font-mono)', fontWeight: 'bold' }}>OPERATOR DOSSIER</div>
          <h1 style={{ fontSize: '3.5rem', fontWeight: '800', color: 'var(--text-primary)', marginBottom: '12px', letterSpacing: '-0.5px' }}>
            {profile.username.split('@')[0]}
          </h1>
          <div style={{ color: 'var(--accent-purple)', fontFamily: 'var(--font-mono)', fontSize: '1.5rem', fontWeight: 'bold', textShadow: 'var(--glow-purple)' }}>
            {profile.currentElo} <span style={{ color: 'var(--text-secondary)', fontSize: '1rem' }}>ELO RATING</span>
          </div>
        </div>

        {/* --- 1v1 ARENA STATS --- */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '24px', marginBottom: '60px' }}>
          {[
            { label: 'WIN RATE', value: `${winRate}%`, color: parseFloat(winRate) >= 50 ? 'var(--accent-cyan)' : 'var(--accent-red)' },
            { label: 'MATCHES', value: profile.matchesPlayed, color: 'var(--accent-purple)' },
            { label: 'WINS', value: profile.wins, color: 'var(--accent-cyan)' },
            { label: 'LOSSES', value: profile.losses, color: 'var(--accent-red)' },
            { label: 'BEST STREAK', value: `${profile.highestWinStreak} 🔥`, color: 'var(--accent-yellow)' },
            { label: 'CURRENT STREAK', value: profile.currentWinStreak, color: 'var(--accent-yellow)' },
          ].map(({ label, value, color }) => (
            <div key={label} className="glass-panel" style={{ padding: '24px', borderTop: `3px solid ${color}`, transition: 'transform 0.3s' }} onMouseEnter={e => e.currentTarget.style.transform = 'translateY(-4px)'} onMouseLeave={e => e.currentTarget.style.transform = 'translateY(0)'}>
              <div style={{ color: 'var(--text-tertiary)', fontSize: '0.7rem', letterSpacing: '3px', fontFamily: 'var(--font-mono)', marginBottom: '12px', fontWeight: 'bold' }}>{label}</div>
              <div style={{ color, fontSize: '2.2rem', fontWeight: 'bold', fontFamily: 'var(--font-mono)', textShadow: `0 0 15px ${color}40` }}>{value}</div>
            </div>
          ))}
        </div>

        {/* --- NEW: TOURNAMENT ASSESSMENTS --- */}
        <div className="glass-panel" style={{ padding: '40px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', letterSpacing: '4px', fontFamily: 'var(--font-mono)', fontWeight: 'bold' }}>
              OFFICIAL TOURNAMENT RECORDS
            </div>
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-cyan)', boxShadow: 'var(--glow-cyan)' }} />
          </div>

          {tournaments.length === 0 ? (
            <div style={{ color: 'var(--text-tertiary)', fontSize: '0.9rem', fontFamily: 'var(--font-mono)', padding: '40px', textAlign: 'center', background: 'rgba(0,0,0,0.2)', borderRadius: '12px', border: '1px dashed var(--border-glass-strong)' }}>
              NO ACTIVE TOURNAMENT REGISTRATIONS DETECTED
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {tournaments.map(t => (
                <div key={t.id} style={{
                  background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-glass)',
                  borderRadius: '12px', padding: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  transition: 'border 0.3s, background 0.3s', cursor: 'default'
                }}
                onMouseEnter={e => { e.currentTarget.style.border = '1px solid rgba(0,255,180,0.4)'; e.currentTarget.style.background = 'var(--accent-cyan-dim)'; }}
                onMouseLeave={e => { e.currentTarget.style.border = '1px solid var(--border-glass)'; e.currentTarget.style.background = 'rgba(0,0,0,0.2)'; }}
                >
                  <div>
                    <div style={{ color: 'var(--text-primary)', fontSize: '1.2rem', fontWeight: '700', marginBottom: '8px' }}>{t.title}</div>
                    <div style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', fontFamily: 'var(--font-mono)' }}>
                      DATE: <span style={{ color: 'var(--text-primary)' }}>{new Date(t.startTime).toLocaleDateString()}</span> <span style={{ margin: '0 12px', color: 'var(--border-glass-strong)' }}>|</span> DURATION: <span style={{ color: 'var(--text-primary)' }}>{t.durationMinutes} MINS</span>
                    </div>
                  </div>
                  <div style={{
                    background: 'var(--bg-primary)', color: 'var(--accent-cyan)', border: '1px solid var(--accent-cyan)',
                    padding: '8px 16px', borderRadius: '6px', fontSize: '0.75rem', letterSpacing: '3px', fontFamily: 'var(--font-mono)', fontWeight: 'bold', boxShadow: 'var(--glow-cyan)'
                  }}>
                    {t.status || 'REGISTERED'}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

      </div>
    </div>
  );
}