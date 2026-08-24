const LEVELS = {
    getLevel(num) {
        const base = Math.min(num, 50);
        const rows = Math.min(4 + Math.floor(base / 3), 15);
        const numColors = Math.min(3 + Math.floor(base / 8), 6);
        const speed = Math.max(0.3, 1 - (base * 0.012));
        const colors = ThemeManager.getColors().slice(0, numColors);

        const grid = [];
        const cols = 10;
        for (let r = 0; r < rows; r++) {
            const row = [];
            const offset = r % 2 === 1;
            const maxCols = offset ? cols - 1 : cols;
            for (let c = 0; c < maxCols; c++) {
                if (Math.random() > 0.12) {
                    row.push(Math.floor(Math.random() * numColors));
                } else {
                    row.push(-1);
                }
            }
            grid.push(row);
        }

        return { grid, colors, cols, speed, rows };
    },

    getMaxRows() { return 15; },
    getCols() { return 10; }
};
