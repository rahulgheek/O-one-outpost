import { useState, useEffect } from 'react';

const useTournamentTimer = (endTimeIso) => {
    const [timeLeft, setTimeLeft] = useState("");
    const [isExpired, setIsExpired] = useState(false);

    useEffect(() => {
        if (!endTimeIso) return;

        // 🔑 Append 'Z' to force UTC parsing.
        // Java's LocalDateTime serializes as "2025-06-23T09:00:00" (no timezone).
        // Without 'Z', JS Date() treats it as local time (IST = UTC+5:30),
        // making the timer 5h30m wrong.
        const targetTime = new Date(endTimeIso).getTime();

        const calculateTimeLeft = () => {
            const now = new Date().getTime();
            const difference = targetTime - now;

            if (difference <= 0) {
                setIsExpired(true);
                setTimeLeft("00:00:00");
                return;
            }

            const hours   = Math.floor((difference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((difference % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((difference % (1000 * 60)) / 1000);

            setTimeLeft(
                String(hours).padStart(2, '0') + ':' +
                String(minutes).padStart(2, '0') + ':' +
                String(seconds).padStart(2, '0')
            );
        };

        calculateTimeLeft();
        const timerId = setInterval(calculateTimeLeft, 1000);
        return () => clearInterval(timerId);
    }, [endTimeIso]);

    return { timeLeft, isExpired };
};

export default useTournamentTimer;