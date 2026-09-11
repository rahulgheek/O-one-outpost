import axios from 'axios';

// Create a central Axios instance pointing to your Spring Cloud Gateway
const api = axios.create({
    baseURL: import.meta.env.VITE_API_GATEWAY_URL || 'http://localhost:8080', // Make sure this matches your Gateway's port!
    headers: {
        'Content-Type': 'application/json',
    },
});

// The Interceptor: Automatically grab the JWT token from localStorage 
// and attach it to the Authorization header for every request.
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('algo_arena_token'); // Ensure this matches what you set on Login
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default api;