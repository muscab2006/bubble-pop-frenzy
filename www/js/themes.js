const THEMES = {
    classic: {
        name: 'Classic',
        unlockScore: 0,
        colors: ['#FF4136', '#FF851B', '#FFDC00', '#2ECC40', '#0074D9', '#B10DC9'],
        bg: '#1a1a2e',
        bgGrad: '#16213e',
        wallColor: '#0f3460',
        textColor: '#ffffff'
    },
    neon: {
        name: 'Neon',
        unlockScore: 5000,
        colors: ['#ff00ff', '#00ffff', '#ff0080', '#80ff00', '#ff8000', '#0080ff'],
        bg: '#0a0a0a',
        bgGrad: '#1a0033',
        wallColor: '#330066',
        textColor: '#00ffcc'
    },
    ocean: {
        name: 'Ocean',
        unlockScore: 15000,
        colors: ['#006994', '#40E0D0', '#FF6B6B', '#FFE66D', '#4ECDC4', '#45B7D1'],
        bg: '#001f3f',
        bgGrad: '#003366',
        wallColor: '#004080',
        textColor: '#7FDBFF'
    },
    sunset: {
        name: 'Sunset',
        unlockScore: 30000,
        colors: ['#FF6B6B', '#FFA07A', '#FFD700', '#FF4500', '#FF1493', '#FF8C00'],
        bg: '#2d1b69',
        bgGrad: '#4a1942',
        wallColor: '#6b2fa0',
        textColor: '#ffcccb'
    },
    galaxy: {
        name: 'Galaxy',
        unlockScore: 50000,
        colors: ['#9b59b6', '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#1abc9c'],
        bg: '#0c0c1d',
        bgGrad: '#1a0a2e',
        wallColor: '#2d1854',
        textColor: '#d4b8ff'
    }
};

const ThemeManager = {
    getCurrent() {
        return THEMES[Storage.getActiveTheme()] || THEMES.classic;
    },

    getColors() {
        return this.getCurrent().colors;
    },

    checkUnlocks(totalScore) {
        const unlocked = Storage.getUnlockedThemes();
        const newUnlocks = [];
        for (const [key, theme] of Object.entries(THEMES)) {
            if (!unlocked.includes(key) && totalScore >= theme.unlockScore) {
                Storage.unlockTheme(key);
                newUnlocks.push(theme.name);
            }
        }
        return newUnlocks;
    },

    getThemeList() {
        const unlocked = Storage.getUnlockedThemes();
        return Object.entries(THEMES).map(([key, theme]) => ({
            key,
            name: theme.name,
            unlockScore: theme.unlockScore,
            unlocked: unlocked.includes(key),
            active: Storage.getActiveTheme() === key
        }));
    }
};
