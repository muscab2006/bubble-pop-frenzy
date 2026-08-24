const Game = {
    canvas: null,
    ctx: null,
    width: 0,
    height: 0,
    BUBBLE_RADIUS: 18,
    COLS: 10,
    grid: [],
    gridCols: 10,
    shooting: false,
    aimAngle: -Math.PI / 2,
    currentBubble: null,
    nextBubble: null,
    flyingBubble: null,
    score: 0,
    combo: 0,
    level: 1,
    state: 'menu',
    colors: [],
    wallLeft: 0,
    wallRight: 0,
    ceilingY: 0,
    moveDownAmount: 0,
    canMoveDown: false,
    particles: [],
    floatingTexts: [],
    lastTime: 0,
    gameOverShown: false,
    isDaily: false,
    dailyLevel: null,
    startTime: 0,
    touchStartX: 0,
    touchStartY: 0,
    aimTouchId: null,

    init() {
        this.canvas = document.getElementById('gameCanvas');
        this.ctx = this.canvas.getContext('2d');
        this.resize();
        Audio.init();
        this.bindEvents();
        this.showMenu();
        requestAnimationFrame((t) => this.loop(t));
    },

    resize() {
        const dpr = window.devicePixelRatio || 1;
        const maxW = Math.min(window.innerWidth, 480);
        const maxH = window.innerHeight;
        this.width = maxW;
        this.height = maxH;
        this.canvas.width = maxW * dpr;
        this.canvas.height = maxH * dpr;
        this.canvas.style.width = maxW + 'px';
        this.canvas.style.height = maxH + 'px';
        this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        this.BUBBLE_RADIUS = Math.max(14, Math.min(18, maxW / 26));
        this.COLS = 10;
        this.wallLeft = (maxW - this.COLS * this.BUBBLE_RADIUS * 2) / 2;
        this.wallRight = maxW - this.wallLeft;
        this.ceilingY = 60;
    },

    bindEvents() {
        window.addEventListener('resize', () => this.resize());

        const getAngle = (x, y) => {
            const cannonX = this.width / 2;
            const cannonY = this.height - 70;
            return Math.atan2(y - cannonY, x - cannonX);
        };

        this.canvas.addEventListener('mousemove', (e) => {
            if (this.state === 'playing') {
                const rect = this.canvas.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                const angle = getAngle(x, y);
                if (angle > -Math.PI * 0.95 && angle < -Math.PI * 0.05) {
                    this.aimAngle = angle;
                }
            }
        });

        this.canvas.addEventListener('click', (e) => {
            if (this.state === 'playing' && !this.shooting) {
                const rect = this.canvas.getBoundingClientRect();
                const x = e.clientX - rect.left;
                const y = e.clientY - rect.top;
                const angle = getAngle(x, y);
                if (angle > -Math.PI * 0.95 && angle < -Math.PI * 0.05) {
                    this.shoot(angle);
                }
            }
        });

        this.canvas.addEventListener('touchstart', (e) => {
            e.preventDefault();
            Audio.resume();
            const touch = e.changedTouches[0];
            this.touchStartX = touch.clientX;
            this.touchStartY = touch.clientY;
            this.aimTouchId = touch.identifier;
        }, { passive: false });

        this.canvas.addEventListener('touchmove', (e) => {
            e.preventDefault();
            if (this.state !== 'playing') return;
            for (const touch of e.changedTouches) {
                if (touch.identifier === this.aimTouchId) {
                    const rect = this.canvas.getBoundingClientRect();
                    const x = touch.clientX - rect.left;
                    const y = touch.clientY - rect.top;
                    const angle = getAngle(x, y);
                    if (angle > -Math.PI * 0.95 && angle < -Math.PI * 0.05) {
                        this.aimAngle = angle;
                    }
                }
            }
        }, { passive: false });

        this.canvas.addEventListener('touchend', (e) => {
            e.preventDefault();
            if (this.state !== 'playing') return;
            for (const touch of e.changedTouches) {
                if (touch.identifier === this.aimTouchId) {
                    if (!this.shooting) {
                        const rect = this.canvas.getBoundingClientRect();
                        const x = touch.clientX - rect.left;
                        const y = touch.clientY - rect.top;
                        const angle = getAngle(x, y);
                        if (angle > -Math.PI * 0.95 && angle < -Math.PI * 0.05) {
                            this.shoot(angle);
                        }
                    }
                    this.aimTouchId = null;
                }
            }
        }, { passive: false });

        document.getElementById('btnPlay').addEventListener('click', () => {
            Audio.play('click');
            this.startGame(1);
        });
        document.getElementById('btnDaily').addEventListener('click', () => {
            Audio.play('click');
            this.startDaily();
        });
        document.getElementById('btnContinue').addEventListener('click', () => {
            Audio.play('click');
            this.startGame(this.level);
        });
        document.getElementById('btnRestart').addEventListener('click', () => {
            Audio.play('click');
            this.startGame(1);
        });
        document.getElementById('btnMenu').addEventListener('click', () => {
            Audio.play('click');
            this.showMenu();
        });
        document.getElementById('btnSound').addEventListener('click', () => {
            const on = Audio.toggle();
            document.getElementById('btnSound').textContent = on ? 'Sound: ON' : 'Sound: OFF';
        });
        document.getElementById('btnThemes').addEventListener('click', () => {
            Audio.play('click');
            this.showThemes();
        });
        document.getElementById('btnStats').addEventListener('click', () => {
            Audio.play('click');
            this.showStats();
        });
        document.getElementById('btnBackFromThemes').addEventListener('click', () => {
            Audio.play('click');
            this.showMenu();
        });
        document.getElementById('btnBackFromStats').addEventListener('click', () => {
            Audio.play('click');
            this.showMenu();
        });
        document.getElementById('btnNextLevel').addEventListener('click', () => {
            Audio.play('click');
            this.startGame(this.level + 1);
        });
    },

    showMenu() {
        this.state = 'menu';
        document.getElementById('menuScreen').classList.remove('hidden');
        document.getElementById('gameOverScreen').classList.add('hidden');
        document.getElementById('levelCompleteScreen').classList.add('hidden');
        document.getElementById('themesScreen').classList.add('hidden');
        document.getElementById('statsScreen').classList.add('hidden');
        document.getElementById('hud').classList.add('hidden');
        document.getElementById('btnSound').textContent = Storage.getSoundEnabled() ? 'Sound: ON' : 'Sound: OFF';

        if (DailyChallenge.isAvailable()) {
            document.getElementById('btnDaily').classList.remove('hidden');
        } else {
            document.getElementById('btnDaily').classList.add('hidden');
        }
    },

    showThemes() {
        document.getElementById('menuScreen').classList.add('hidden');
        document.getElementById('themesScreen').classList.remove('hidden');
        const list = document.getElementById('themeList');
        list.innerHTML = '';
        const themes = ThemeManager.getThemeList();
        themes.forEach(t => {
            const div = document.createElement('div');
            div.className = 'theme-item';
            if (t.active) div.classList.add('active');
            if (!t.unlocked) div.classList.add('locked');
            div.innerHTML = `
                <div class="theme-colors">
                    ${THEMES[t.key].colors.slice(0, 4).map(c => `<span class="theme-dot" style="background:${c}"></span>`).join('')}
                </div>
                <div class="theme-info">
                    <span class="theme-name">${t.name}</span>
                    ${!t.unlocked ? `<span class="theme-lock">Locked (${t.unlockScore.toLocaleString()} pts)</span>` : ''}
                </div>
                ${t.unlocked && !t.active ? '<span class="theme-select">TAP</span>' : ''}
                ${t.active ? '<span class="theme-active">ACTIVE</span>' : ''}
            `;
            if (t.unlocked) {
                div.addEventListener('click', () => {
                    Storage.setActiveTheme(t.key);
                    Audio.play('click');
                    this.showThemes();
                });
            }
            list.appendChild(div);
        });
    },

    showStats() {
        document.getElementById('menuScreen').classList.add('hidden');
        document.getElementById('statsScreen').classList.remove('hidden');
        const s = Storage.getStats();
        const el = document.getElementById('statsContent');
        el.innerHTML = `
            <div class="stat-row"><span>Games Played</span><span>${s.gamesPlayed}</span></div>
            <div class="stat-row"><span>Total Pops</span><span>${s.totalPops.toLocaleString()}</span></div>
            <div class="stat-row"><span>Best Combo</span><span>${s.bestCombo}x</span></div>
            <div class="stat-row"><span>Highest Score</span><span>${Storage.getHighScore().toLocaleString()}</span></div>
            <div class="stat-row"><span>Total Score</span><span>${Storage.getTotalScore().toLocaleString()}</span></div>
            <div class="stat-row"><span>Current Level</span><span>${Storage.getLevel()}</span></div>
            <div class="stat-row"><span>Daily High</span><span>${Storage.getDailyHighScore().toLocaleString()}</span></div>
        `;
    },

    startGame(lvl) {
        this.isDaily = false;
        this.level = lvl;
        Storage.setLevel(lvl);
        const levelData = LEVELS.getLevel(lvl);
        this.loadLevel(levelData);
        this.state = 'playing';
        document.getElementById('menuScreen').classList.add('hidden');
        document.getElementById('gameOverScreen').classList.add('hidden');
        document.getElementById('levelCompleteScreen').classList.add('hidden');
        document.getElementById('hud').classList.remove('hidden');
        this.startTime = Date.now();
        this.updateHUD();
    },

    startDaily() {
        this.isDaily = true;
        this.dailyLevel = DailyChallenge.generateLevel();
        this.level = 1;
        this.loadLevel(this.dailyLevel);
        this.state = 'playing';
        document.getElementById('menuScreen').classList.add('hidden');
        document.getElementById('hud').classList.remove('hidden');
        this.startTime = Date.now();
        this.updateHUD();
    },

    loadLevel(data) {
        this.grid = [];
        this.colors = data.colors;
        this.gridCols = data.cols;
        this.score = 0;
        this.combo = 0;
        this.shooting = false;
        this.flyingBubble = null;
        this.particles = [];
        this.floatingTexts = [];
        this.gameOverShown = false;
        this.moveDownAmount = 0;
        this.canMoveDown = false;

        const offsetR = data.grid.length % 2 === 1;
        for (let r = 0; r < data.grid.length; r++) {
            const row = [];
            const offset = r % 2 === 1;
            const maxCols = offset ? this.gridCols - 1 : this.gridCols;
            for (let c = 0; c < maxCols; c++) {
                const colorIdx = data.grid[r][c];
                if (colorIdx >= 0 && colorIdx < this.colors.length) {
                    const x = this.getBubbleX(r, c);
                    const y = this.getBubbleY(r) + this.ceilingY + this.moveDownAmount;
                    row.push(new Bubble(x, y, colorIdx, this.BUBBLE_RADIUS));
                } else {
                    row.push(null);
                }
            }
            this.grid.push(row);
        }

        this.spawnBubble();
    },

    getBubbleX(row, col) {
        const offset = row % 2 === 1;
        const startX = this.wallLeft + this.BUBBLE_RADIUS;
        if (offset) {
            return startX + col * this.BUBBLE_RADIUS * 2 + this.BUBBLE_RADIUS;
        }
        return startX + col * this.BUBBLE_RADIUS * 2;
    },

    getBubbleY(row) {
        return row * this.BUBBLE_RADIUS * 1.73 + this.BUBBLE_RADIUS;
    },

    spawnBubble() {
        const ci = Math.floor(Math.random() * this.colors.length);
        this.currentBubble = new Bubble(this.width / 2, this.height - 70, ci, this.BUBBLE_RADIUS);
        const nci = Math.floor(Math.random() * this.colors.length);
        this.nextBubble = new Bubble(0, 0, nci, this.BUBBLE_RADIUS);
    },

    shoot(angle) {
        if (this.shooting || !this.currentBubble) return;
        this.shooting = true;
        Audio.play('shoot');

        const speed = 12;
        this.flyingBubble = this.currentBubble;
        this.flyingBubble.vx = Math.cos(angle) * speed;
        this.flyingBubble.vy = Math.sin(angle) * speed;

        this.currentBubble = this.nextBubble;
        this.currentBubble.x = this.width / 2;
        this.currentBubble.y = this.height - 70;
        const nci = Math.floor(Math.random() * this.colors.length);
        this.nextBubble = new Bubble(0, 0, nci, this.BUBBLE_RADIUS);
    },

    findSnapPosition(bubble) {
        let bestR = 0, bestC = 0, bestDist = Infinity;
        const bRow = (bubble.y - this.ceilingY - this.moveDownAmount) / (this.BUBBLE_RADIUS * 1.73);
        const bCol = (bubble.x - this.wallLeft - this.BUBBLE_RADIUS) / (this.BUBBLE_RADIUS * 2);

        for (let r = 0; r < 20; r++) {
            const offset = r % 2 === 1;
            const maxC = offset ? this.gridCols - 1 : this.gridCols;
            for (let c = 0; c < maxC; c++) {
                const x = this.getBubbleX(r, c);
                const y = this.getBubbleY(r) + this.ceilingY + this.moveDownAmount;
                const dist = Math.sqrt((bubble.x - x) ** 2 + (bubble.y - y) ** 2);
                if (dist < bestDist && dist < this.BUBBLE_RADIUS * 2.5) {
                    if (r < this.grid.length && this.grid[r] && this.grid[r][c]) continue;
                    bestDist = dist;
                    bestR = r;
                    bestC = c;
                }
            }
        }

        return { row: bestR, col: bestC };
    },

    snapBubble(bubble) {
        const { row, col } = this.findSnapPosition(bubble);

        while (this.grid.length <= row) {
            const r = this.grid.length;
            const offset = r % 2 === 1;
            const maxC = offset ? this.gridCols - 1 : this.gridCols;
            const newRow = new Array(maxC).fill(null);
            this.grid.push(newRow);
        }

        if (!this.grid[row]) {
            const offset = row % 2 === 1;
            const maxC = offset ? this.gridCols - 1 : this.gridCols;
            this.grid[row] = new Array(maxC).fill(null);
        }

        if (col >= 0 && col < this.grid[row].length) {
            const x = this.getBubbleX(row, col);
            const y = this.getBubbleY(row) + this.ceilingY + this.moveDownAmount;
            bubble.x = x;
            bubble.y = y;
            bubble.settled = true;
            this.grid[row][col] = bubble;
        }

        const matches = this.findMatches(row, col, bubble.colorIndex);
        if (matches.length >= 3) {
            this.combo++;
            const points = matches.length * 10 * this.combo;
            this.score += points;

            matches.forEach(([r, c]) => {
                const b = this.grid[r][c];
                if (b) {
                    b.popping = true;
                    this.particles.push(...this.createPopParticles(b.x, b.y, this.colors[b.colorIndex]));
                    this.grid[r][c] = null;
                }
            });

            Audio.play(this.combo > 1 ? 'combo' : 'pop');
            this.floatingTexts.push({
                x: bubble.x, y: bubble.y - 20,
                text: `+${points}${this.combo > 1 ? ' x' + this.combo : ''}`,
                life: 1, vy: -1
            });

            this.removeFloating();
            this.checkWin();
        } else {
            this.combo = 0;
            Audio.play('bounce');
            this.canMoveDown = true;
        }

        this.spawnBubble();
        this.shooting = false;
        this.updateHUD();
        this.checkGameOver();
    },

    findMatches(row, col, colorIndex) {
        const visited = new Set();
        const matches = [];
        const queue = [[row, col]];

        while (queue.length > 0) {
            const [r, c] = queue.shift();
            const key = `${r},${c}`;
            if (visited.has(key)) continue;
            visited.add(key);

            if (r < 0 || r >= this.grid.length) continue;
            if (!this.grid[r] || c < 0 || c >= this.grid[r].length) continue;
            const b = this.grid[r][c];
            if (!b || b.colorIndex !== colorIndex) continue;

            matches.push([r, c]);

            const offset = r % 2 === 1;
            const neighbors = offset
                ? [[r-1,c],[r-1,c+1],[r,c-1],[r,c+1],[r+1,c],[r+1,c+1]]
                : [[r-1,c-1],[r-1,c],[r,c-1],[r,c+1],[r+1,c-1],[r+1,c]];

            neighbors.forEach(n => queue.push(n));
        }

        return matches;
    },

    removeFloating() {
        const connected = new Set();
        for (let c = 0; c < this.gridCols; c++) {
            if (this.grid[0] && this.grid[0][c]) {
                this.bfs(0, c, connected);
            }
        }

        for (let r = 0; r < this.grid.length; r++) {
            if (!this.grid[r]) continue;
            for (let c = 0; c < this.grid[r].length; c++) {
                if (this.grid[r][c] && !connected.has(`${r},${c}`)) {
                    const b = this.grid[r][c];
                    this.score += 5 * (this.combo + 1);
                    b.popping = true;
                    this.particles.push(...this.createPopParticles(b.x, b.y, this.colors[b.colorIndex]));
                    this.grid[r][c] = null;
                }
            }
        }
    },

    bfs(startR, startC, visited) {
        const queue = [[startR, startC]];
        while (queue.length > 0) {
            const [r, c] = queue.shift();
            const key = `${r},${c}`;
            if (visited.has(key)) continue;
            visited.add(key);

            if (r < 0 || r >= this.grid.length) continue;
            if (!this.grid[r] || c < 0 || c >= this.grid[r].length) continue;
            if (!this.grid[r][c]) continue;

            const offset = r % 2 === 1;
            const neighbors = offset
                ? [[r-1,c],[r-1,c+1],[r,c-1],[r,c+1],[r+1,c],[r+1,c+1]]
                : [[r-1,c-1],[r-1,c],[r,c-1],[r,c+1],[r+1,c-1],[r+1,c]];

            neighbors.forEach(n => queue.push(n));
        }
    },

    createPopParticles(x, y, color) {
        const particles = [];
        for (let i = 0; i < 8; i++) {
            const angle = (Math.PI * 2 * i) / 8;
            particles.push({
                x, y,
                vx: Math.cos(angle) * (2 + Math.random() * 3),
                vy: Math.sin(angle) * (2 + Math.random() * 3),
                life: 1,
                color,
                size: 3 + Math.random() * 3
            });
        }
        return particles;
    },

    checkGameOver() {
        for (let r = 0; r < this.grid.length; r++) {
            if (!this.grid[r]) continue;
            for (let c = 0; c < this.grid[r].length; c++) {
                if (this.grid[r][c]) {
                    const y = this.grid[r][c].y;
                    if (y >= this.height - 140) {
                        this.gameOver();
                        return;
                    }
                }
            }
        }
    },

    checkWin() {
        let hasBubbles = false;
        for (const row of this.grid) {
            if (row) {
                for (const b of row) {
                    if (b) { hasBubbles = true; break; }
                }
            }
            if (hasBubbles) break;
        }

        if (!hasBubbles && !this.gameOverShown) {
            this.gameOverShown = true;
            Audio.play('levelup');
            setTimeout(() => this.levelComplete(), 500);
        }
    },

    levelComplete() {
        this.state = 'levelcomplete';
        const elapsed = (Date.now() - this.startTime) / 1000;
        const timeBonus = Math.max(0, Math.floor((120 - elapsed) * 5));
        this.score += timeBonus;

        Storage.setHighScore(this.score);
        Storage.addTotalScore(this.score);
        ThemeManager.checkUnlocks(Storage.getTotalScore());
        Storage.updateStats({ gamesPlayed: 1, totalPops: this.score, bestCombo: this.combo, totalPlayTime: elapsed });

        if (this.isDaily) {
            DailyChallenge.recordPlay();
            Storage.setDailyHighScore(this.score);
        }

        document.getElementById('levelCompleteScreen').classList.remove('hidden');
        document.getElementById('levelScore').textContent = this.score.toLocaleString();
        document.getElementById('levelTimeBonus').textContent = `+${timeBonus} time bonus`;
    },

    gameOver() {
        if (this.gameOverShown) return;
        this.gameOverShown = true;
        this.state = 'gameover';
        Audio.play('gameover');

        const elapsed = (Date.now() - this.startTime) / 1000;
        Storage.setHighScore(this.score);
        Storage.addTotalScore(this.score);
        ThemeManager.checkUnlocks(Storage.getTotalScore());
        Storage.updateStats({ gamesPlayed: 1, totalPops: this.score, bestCombo: this.combo, totalPlayTime: elapsed });

        if (this.isDaily) {
            DailyChallenge.recordPlay();
            Storage.setDailyHighScore(this.score);
        }

        document.getElementById('gameOverScreen').classList.remove('hidden');
        document.getElementById('finalScore').textContent = this.score.toLocaleString();
        document.getElementById('highScore').textContent = Storage.getHighScore().toLocaleString();
    },

    updateHUD() {
        document.getElementById('scoreDisplay').textContent = this.score.toLocaleString();
        document.getElementById('levelDisplay').textContent = this.isDaily ? 'Daily' : `Lv.${this.level}`;
        document.getElementById('comboDisplay').textContent = this.combo > 1 ? `${this.combo}x Combo!` : '';
    },

    loop(time) {
        const dt = Math.min((time - this.lastTime) / 16, 3);
        this.lastTime = time;

        if (this.state === 'playing') {
            this.update(dt);
        }

        this.draw();
        requestAnimationFrame((t) => this.loop(t));
    },

    update(dt) {
        if (this.flyingBubble) {
            this.flyingBubble.x += this.flyingBubble.vx * dt;
            this.flyingBubble.y += this.flyingBubble.vy * dt;

            if (this.flyingBubble.x <= this.wallLeft + this.BUBBLE_RADIUS) {
                this.flyingBubble.x = this.wallLeft + this.BUBBLE_RADIUS;
                this.flyingBubble.vx *= -1;
                Audio.play('bounce');
            }
            if (this.flyingBubble.x >= this.wallRight - this.BUBBLE_RADIUS) {
                this.flyingBubble.x = this.wallRight - this.BUBBLE_RADIUS;
                this.flyingBubble.vx *= -1;
                Audio.play('bounce');
            }

            if (this.flyingBubble.y <= this.ceilingY + this.BUBBLE_RADIUS + this.moveDownAmount) {
                this.snapBubble(this.flyingBubble);
                this.flyingBubble = null;
                return;
            }

            let hit = false;
            for (let r = 0; r < this.grid.length && !hit; r++) {
                if (!this.grid[r]) continue;
                for (let c = 0; c < this.grid[r].length && !hit; c++) {
                    if (this.grid[r][c]) {
                        const b = this.grid[r][c];
                        if (this.flyingBubble.distanceTo(b) < this.BUBBLE_RADIUS * 1.8) {
                            hit = true;
                        }
                    }
                }
            }

            if (hit) {
                this.snapBubble(this.flyingBubble);
                this.flyingBubble = null;
            }
        }

        this.particles.forEach(p => {
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 0.1 * dt;
            p.life -= 0.03 * dt;
        });
        this.particles = this.particles.filter(p => p.life > 0);

        this.floatingTexts.forEach(f => {
            f.y += f.vy * dt;
            f.life -= 0.02 * dt;
        });
        this.floatingTexts = this.floatingTexts.filter(f => f.life > 0);
    },

    draw() {
        const ctx = this.ctx;
        const theme = ThemeManager.getCurrent();

        const grad = ctx.createLinearGradient(0, 0, 0, this.height);
        grad.addColorStop(0, theme.bg);
        grad.addColorStop(1, theme.bgGrad);
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, this.width, this.height);

        if (this.state === 'playing' || this.state === 'gameover' || this.state === 'levelcomplete') {
            this.drawGame(ctx, theme);
        } else {
            this.drawMenuBg(ctx, theme);
        }
    },

    drawMenuBg(ctx, theme) {
        const time = Date.now() / 3000;
        for (let i = 0; i < 15; i++) {
            const x = (Math.sin(time + i * 0.7) * 0.3 + 0.5) * this.width;
            const y = (Math.cos(time + i * 0.5) * 0.3 + 0.5) * this.height;
            const ci = i % theme.colors.length;
            ctx.beginPath();
            ctx.arc(x, y, 15, 0, Math.PI * 2);
            ctx.fillStyle = theme.colors[ci] + '40';
            ctx.fill();
        }
    },

    drawGame(ctx, theme) {
        ctx.fillStyle = theme.wallColor + '40';
        ctx.fillRect(0, this.ceilingY - 5, this.wallLeft, this.height);
        ctx.fillRect(this.wallRight, this.ceilingY - 5, this.width - this.wallRight, this.height);

        ctx.strokeStyle = theme.textColor + '30';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(this.wallLeft, this.ceilingY);
        ctx.lineTo(this.wallLeft, this.height);
        ctx.moveTo(this.wallRight, this.ceilingY);
        ctx.lineTo(this.wallRight, this.height);
        ctx.stroke();

        for (const row of this.grid) {
            if (!row) continue;
            for (const b of row) {
                if (b) b.draw(ctx, this.colors);
            }
        }

        if (this.flyingBubble) {
            this.flyingBubble.draw(ctx, this.colors);
        }

        this.drawCannon(ctx, theme);

        this.particles.forEach(p => {
            ctx.save();
            ctx.globalAlpha = p.life;
            ctx.fillStyle = p.color;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.size * p.life, 0, Math.PI * 2);
            ctx.fill();
            ctx.restore();
        });

        this.floatingTexts.forEach(f => {
            ctx.save();
            ctx.globalAlpha = f.life;
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 16px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(f.text, f.x, f.y);
            ctx.restore();
        });

        if (this.currentBubble) {
            ctx.save();
            ctx.globalAlpha = 0.4;
            const cannonX = this.width / 2;
            const cannonY = this.height - 70;
            const lineLen = 60;
            ctx.strokeStyle = this.colors[this.currentBubble.colorIndex];
            ctx.lineWidth = 2;
            ctx.setLineDash([5, 5]);
            ctx.beginPath();
            ctx.moveTo(cannonX, cannonY);
            ctx.lineTo(
                cannonX + Math.cos(this.aimAngle) * lineLen,
                cannonY + Math.sin(this.aimAngle) * lineLen
            );
            ctx.stroke();
            ctx.restore();
        }
    },

    drawCannon(ctx, theme) {
        const cx = this.width / 2;
        const cy = this.height - 70;

        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(this.aimAngle + Math.PI / 2);

        ctx.fillStyle = theme.wallColor;
        ctx.beginPath();
        ctx.moveTo(-12, 20);
        ctx.lineTo(-8, -5);
        ctx.lineTo(8, -5);
        ctx.lineTo(12, 20);
        ctx.closePath();
        ctx.fill();

        ctx.fillStyle = theme.textColor;
        ctx.beginPath();
        ctx.arc(0, 0, 14, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();

        if (this.currentBubble) {
            this.currentBubble.x = cx;
            this.currentBubble.y = cy;
            this.currentBubble.draw(ctx, this.colors);
        }

        if (this.nextBubble) {
            this.nextBubble.x = cx + 50;
            this.nextBubble.y = this.height - 30;
            this.nextBubble.radius = this.BUBBLE_RADIUS * 0.7;
            this.nextBubble.draw(ctx, this.colors);
            ctx.fillStyle = theme.textColor + '80';
            ctx.font = '10px sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText('NEXT', cx + 50, this.height - 10);
        }
    }
};
