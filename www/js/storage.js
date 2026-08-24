const Storage = {
    PREFIX: 'bpf_',

    get(key, defaultVal = null) {
        try {
            const val = localStorage.getItem(this.PREFIX + key);
            return val ? JSON.parse(val) : defaultVal;
        } catch { return defaultVal; }
    },

    set(key, val) {
        try { localStorage.setItem(this.PREFIX + key, JSON.stringify(val)); } catch {}
    },

    remove(key) {
        localStorage.removeItem(this.PREFIX + key);
    },

    getHighScore() { return this.get('highscore', 0); },
    setHighScore(s) { if (s > this.getHighScore()) this.set('highscore', s); },

    getTotalScore() { return this.get('totalscore', 0); },
    addTotalScore(s) { this.set('totalscore', this.getTotalScore() + s); },

    getLevel() { return this.get('level', 1); },
    setLevel(l) { this.set('level', l); },

    getUnlockedThemes() { return this.get('themes', ['classic']); },
    unlockTheme(t) {
        const themes = this.getUnlockedThemes();
        if (!themes.includes(t)) { themes.push(t); this.set('themes', themes); }
    },

    getActiveTheme() { return this.get('activeTheme', 'classic'); },
    setActiveTheme(t) { this.set('activeTheme', t); },

    getSoundEnabled() { return this.get('sound', true); },
    toggleSound() { this.set('sound', !this.getSoundEnabled()); },

    getDailyPlayed(date) { return this.get('daily_' + date, false); },
    setDailyPlayed(date) { this.set('daily_' + date, true); },
    getDailyHighScore() { return this.get('dailyHigh', 0); },
    setDailyHighScore(s) { if (s > this.getDailyHighScore()) this.set('dailyHigh', s); },

    getStats() {
        return this.get('stats', { gamesPlayed: 0, totalPops: 0, bestCombo: 0, totalPlayTime: 0 });
    },
    updateStats(data) {
        const s = this.getStats();
        if (data.gamesPlayed) s.gamesPlayed += data.gamesPlayed;
        if (data.totalPops) s.totalPops += data.totalPops;
        if (data.bestCombo && data.bestCombo > s.bestCombo) s.bestCombo = data.bestCombo;
        if (data.totalPlayTime) s.totalPlayTime += data.totalPlayTime;
        this.set('stats', s);
    }
};
