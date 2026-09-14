import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { getCurrentRole, syncAuthRole, startTokenExpiryWatcher } from './utils/auth';
import './App.css';

// --- Existing Core Pages ---
import Landing from './pages/Landing';
import Login from './pages/Login';
import CompanyLogin from './pages/CompanyLogin';
import OverseerLogin from './pages/OverseerLogin';
import Lobby from './pages/Lobby';
import Arena from './pages/Arena';
import Dashboard from './pages/Dashboard';
import Profile from './pages/Profile';
import AdminDashboard from './pages/AdminDashboard';
import ServerDown from './pages/ServerDown';

// --- B2B Company Components ---
import CompanyDashboard from './pages/CompanyDashboard';
import QuestionBank from './components/QuestionBank';
import AssessmentBuilder from './components/AssessmentBuilder';
import SelectionProcess from './pages/SelectionProcess';

// --- B2B Candidate Components ---
import CandidateEntry from './components/CandidateEntry';
import TournamentWorkspace from './components/TournamentWorkspace';
import AssessmentLeaderboard from './components/AssessmentLeaderBoard';
import AssessmentDirectory from './components/AssessmentDirectory';
import TournamentRegistration from './components/TournamentRegistration';
import CandidateDashboard from './components/CandidateDashboard';

// LayoutWrapper has been removed as each route now provides its own Nav component

function RoleBasedRedirect() {
  const role = getCurrentRole();
  if (role === 'COMPANY') return <Navigate to="/company/dashboard" replace />;
  if (role === 'ADMIN') return <Navigate to="/overseer" replace />;
  return <Navigate to="/lobby" replace />;
}

function ProtectedRoute({ children, allowedRoles }) {
  const role = getCurrentRole();
  if (allowedRoles && !allowedRoles.includes(role)) {
    return <RoleBasedRedirect />;
  }
  return children;
}

export const globalEventTarget = new EventTarget();

axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!error.response || (error.response && error.response.status >= 502)) {
      // Catch Network Errors (backend offline) or 502/503/504 server errors
      globalEventTarget.dispatchEvent(new Event('server-down'));
    } else if (error.response && error.response.status === 401) {
      // Secure Software Principle: Fail Securely.
      // Invalidate the compromised/expired session immediately.
      localStorage.clear();
      sessionStorage.setItem('sessionExpired', 'true');
      window.location.href = '/';
    }
    return Promise.reject(error);
  }
);

export default function App() {
  const [currentUser, setCurrentUser] = useState(localStorage.getItem('algo_arena_user'));
  const [isServerDown, setIsServerDown] = useState(false);
  const getToken = useCallback(() => localStorage.getItem('algo_arena_token'), []);
  const handleLogout = useCallback(() => { localStorage.clear(); setCurrentUser(null); }, []);

  useEffect(() => {
    // Re-synchronize role from JWT payload if stored value was corrupted or stale
    syncAuthRole();

    // Start a proactive watcher that auto-signs out when the JWT expires
    const cleanupWatcher = startTokenExpiryWatcher();
    return () => cleanupWatcher();
  }, [currentUser]);

  useEffect(() => {
    const handleServerDown = () => setIsServerDown(true);
    globalEventTarget.addEventListener('server-down', handleServerDown);
    return () => globalEventTarget.removeEventListener('server-down', handleServerDown);
  }, []);

  if (isServerDown) {
    return <ServerDown />;
  }

  return (
    <BrowserRouter future={{ v7_relativeSplatPath: true }}>
        <Routes>
          {/* Core Arena Routes */}
          <Route path="/" element={!currentUser ? <Landing /> : <RoleBasedRedirect />} />
          <Route path="/lobby" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><Lobby currentUser={currentUser} getToken={getToken} /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/dashboard" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><Dashboard currentUser={currentUser} getToken={getToken} /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/arena/:matchId" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><Arena currentUser={currentUser} getToken={getToken} /></ProtectedRoute> : <Navigate to="/" />} />
          
          {/* Engineer (USER) Authentication */}
          <Route path="/login" element={!currentUser ? <Login setAuth={setCurrentUser} initialMode="login" /> : <RoleBasedRedirect />} />
          <Route path="/signup" element={!currentUser ? <Login setAuth={setCurrentUser} initialMode="register" /> : <RoleBasedRedirect />} />
          
          {/* Company (COMPANY) Authentication */}
          <Route path="/company/login" element={!currentUser ? <CompanyLogin setAuth={setCurrentUser} initialMode="login" /> : <RoleBasedRedirect />} />
          <Route path="/company/signup" element={!currentUser ? <CompanyLogin setAuth={setCurrentUser} initialMode="register" /> : <RoleBasedRedirect />} />

          {/* System Overseer (ADMIN) Dedicated Route:
              - When unauthenticated: renders dedicated OverseerLogin terminal
              - When authenticated as ADMIN: renders AdminDashboard
              - When authenticated as USER/COMPANY: blocked and redirected via RoleBasedRedirect
          */}
          <Route 
            path="/overseer" 
            element={
              !currentUser ? (
                <OverseerLogin setAuth={setCurrentUser} />
              ) : (
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <AdminDashboard getToken={getToken} />
                </ProtectedRoute>
              )
            } 
          />
          
          {/* Backward compatibility for /admin */}
          <Route path="/admin" element={<Navigate to="/overseer" replace />} />
          
          {/* Recruiter (COMPANY) Protected Routes */}
          <Route path="/company/dashboard" element={currentUser ? <ProtectedRoute allowedRoles={['COMPANY']}><CompanyDashboard /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/company/question-bank" element={currentUser ? <ProtectedRoute allowedRoles={['COMPANY']}><QuestionBank /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/company/create-assessment" element={currentUser ? <ProtectedRoute allowedRoles={['COMPANY']}><AssessmentBuilder /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/company/selection" element={currentUser ? <ProtectedRoute allowedRoles={['COMPANY']}><SelectionProcess /></ProtectedRoute> : <Navigate to="/" />} />

          {/* Candidate B2B Routes (Typically USER, but could allow COMPANY to view them?) Letting USER only for now */}
          <Route path="/candidate/directory" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><AssessmentDirectory currentUser={currentUser} /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/candidate/register/:assessmentId" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><TournamentRegistration currentUser={currentUser} /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/candidate/dashboard" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><CandidateDashboard /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/candidate/join" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><CandidateEntry /></ProtectedRoute> : <Navigate to="/" />} />
          
          <Route path="/workspace/:assessmentId" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><TournamentWorkspace /></ProtectedRoute> : <Navigate to="/" />} />
          <Route path="/workspace/:assessmentId/leaderboard" element={currentUser ? <ProtectedRoute allowedRoles={['USER']}><AssessmentLeaderboard /></ProtectedRoute> : <Navigate to="/" />} />
          
          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
    </BrowserRouter>
  );
}