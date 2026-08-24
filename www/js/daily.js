const DailyChallenge = {
    getSeed(date) {
        let hash = 0;
        for (let i = 0; i < date.length; i++) {
            hash = ((hash << 5) - hash) + date.charCodeAt(i);
            hash |= 0;
        }
        return Math.abs(hash);
    },

    seededRandom(seed) {
        let s = seed;
        return function() {
            s = (s * 16807 + 0) % 2147483647;
            return (s - 1) / 2147483646;
        };
    },

    getTodayString() {
        const d = new Date();
        return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
    },

    generateLevel() {
        const dateStr = this.getTodayString();
        const seed = this.getSeed(dateStr);
        const rng = this.seededRandom(seed);
        const numColors = Math.floor(rng() * 3) + 4;
        const rows = Math.floor(rng() * 3) + 6;
        const colors = ThemeManager.getColors().slice(0, numColors);

        const grid = [];
        const cols = 10;
        for (let r = 0; r < rows; r++) {
            const row = [];
            const offset = r % 2 === 1;
            const maxCols = offset ? cols - 1 : cols;
            for (let c = 0; c < maxCols; c++) {
                if (rng() > 0.15) {
                    row.push(Math.floor(rng() * numColors));
                } else {
                    row.push(-1);
                }
            }
            grid.push(row);
        }

        return {
            grid,
            colors,
            cols,
            seed,
            date: dateStr
        };
    },

    isAvailable() {
        return !Storage.getDailyPlayed(this.getTodayString());
    },

    recordPlay() {
        Storage.setDailyPlayed(this.getTodayString());
    }
};
