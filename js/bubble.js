class Bubble {
    constructor(x, y, colorIndex, radius) {
        this.x = x;
        this.y = y;
        this.colorIndex = colorIndex;
        this.radius = radius || 18;
        this.vx = 0;
        this.vy = 0;
        this.alive = true;
        this.popping = false;
        this.popScale = 1;
        this.opacity = 1;
        this.settled = false;
    }

    draw(ctx, colors) {
        if (!this.alive && !this.popping) return;
        if (this.popping && this.popScale <= 0) return;

        ctx.save();
        ctx.globalAlpha = this.opacity;

        const r = this.radius * (this.popping ? this.popScale : 1);
        const color = colors[this.colorIndex] || '#888';

        const grad = ctx.createRadialGradient(
            this.x - r * 0.3, this.y - r * 0.3, r * 0.1,
            this.x, this.y, r
        );
        grad.addColorStop(0, this.lighten(color, 60));
        grad.addColorStop(0.7, color);
        grad.addColorStop(1, this.darken(color, 30));

        ctx.beginPath();
        ctx.arc(this.x, this.y, r, 0, Math.PI * 2);
        ctx.fillStyle = grad;
        ctx.fill();

        ctx.beginPath();
        ctx.arc(this.x - r * 0.25, this.y - r * 0.25, r * 0.25, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(255,255,255,0.4)';
        ctx.fill();

        ctx.restore();
    }

    lighten(hex, pct) {
        const num = parseInt(hex.slice(1), 16);
        const r = Math.min(255, (num >> 16) + pct);
        const g = Math.min(255, ((num >> 8) & 0xff) + pct);
        const b = Math.min(255, (num & 0xff) + pct);
        return `rgb(${r},${g},${b})`;
    }

    darken(hex, pct) {
        const num = parseInt(hex.slice(1), 16);
        const r = Math.max(0, (num >> 16) - pct);
        const g = Math.max(0, ((num >> 8) & 0xff) - pct);
        const b = Math.max(0, (num & 0xff) - pct);
        return `rgb(${r},${g},${b})`;
    }

    distanceTo(other) {
        const dx = this.x - other.x;
        const dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    update() {
        if (this.popping) {
            this.popScale -= 0.08;
            this.opacity -= 0.1;
            if (this.popScale <= 0) this.alive = false;
        }
    }
}
