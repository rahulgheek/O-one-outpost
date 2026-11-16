import { useState, useEffect } from 'react';
import { parseAssessmentTime } from '../utils/datetime';

const formatClock = (difference) => {
    const hours = Math.floor(difference / (1000 * 60 * 60));
    const minutes = Math.floor((difference % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((difference % (1000 * 60)) / 1000);
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};

/**
 * Counts down to an assessment timestamp. The timestamp may be the global end
 * time or the candidate's personal deadline; both are IST wall-clock values.
 */
const useTournamentTimer = (endTimeIso) => {
    const [timeLeft, setTimeLeft] = useState('');
    const [isExpired, setIsExpired] = useState(false);

    useEffect(() => {
        const targetTime = parseAssessmentTime(endTimeIso)?.getTime();
        setIsExpired(false);

        if (!targetTime || Number.isNaN(targetTime)) {
            setTimeLeft('');
            return undefined;
        }

        const calculateTimeLeft = () => {
            const difference = targetTime - Date.now();
            if (difference <= 0) {
                setIsExpired(true);
                setTimeLeft('00:00:00');
                return;
            }
            setTimeLeft(formatClock(difference));
        };

        calculateTimeLeft();
        const timerId = setInterval(calculateTimeLeft, 1000);
        return () => clearInterval(timerId);
    }, [endTimeIso]);

    return { timeLeft, isExpired };
};

export default useTournamentTimer;
