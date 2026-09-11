import axios from 'axios';

const gatewayUrl = import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080';

// 1. Setup the Axios Instance internally
const apiClient = axios.create({
    baseURL: `${gatewayUrl}/api/candidate`,
    headers: { 'Content-Type': 'application/json' }
});

// 2. Automatically attach the JWT token to every request
apiClient.interceptors.request.use((config) => {
    const token = localStorage.getItem('algo_arena_token');
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
});

// 3. Export all frontend API calls
export const candidateApi = {
    joinAssessment: async (testCode) => {
        const response = await apiClient.post('/join', { testCode });
        return response.data;
    },
    getLeaderboard: async (assessmentId) => {
        try {
            const response = await apiClient.get(`/workspace/${assessmentId}/leaderboard`);
            return response.data;
        } catch (error) {
            if (error.response?.status === 403) throw new Error('BLACKOUT_ACTIVE');
            throw error;
        }
    },
    submitCode: async (assessmentId, problemId, code) => {
        const response = await apiClient.post(`/workspace/${assessmentId}/problem/${problemId}/submit`, { code });
        return response.data;
    },
    getAssessmentDirectory: async (page = 0, size = 20, search = '') => {
        const response = await apiClient.get(`/workspace/directory?page=${page}&size=${size}&search=${encodeURIComponent(search)}`);
        return response.data;
    },
    registerForTournament: async (assessmentId, registrationData) => {
        const response = await apiClient.post(`/workspace/${assessmentId}/register`, registrationData);
        return response.data;
    },
    getMyTournaments: async () => {
        const response = await apiClient.get('/workspace/my-tournaments');
        return response.data;
    },
    startWorkspace: async (assessmentId) => {
        const response = await apiClient.post(`/workspace/${assessmentId}/start`);
        return response.data;
    },
    getAssessmentProblems: async (assessmentId) => {
        const response = await apiClient.get(`/workspace/${assessmentId}/problems`);
        return response.data;
    },
    finishAssessment: async (assessmentId, status) => {
        const response = await apiClient.post(`/workspace/${assessmentId}/finish?reason=${status}`);
        return response.data;
    },
    getAssessmentDetails: async (assessmentId) => {
        const response = await apiClient.get(`/workspace/${assessmentId}`);
        return response.data;
    }
};