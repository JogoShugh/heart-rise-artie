const http = require('http');

// 1. THE GAME STATE (In-Memory DB)
const game = {
    score: { home: 0, away: 0 },
    clock: "12:00",
    period: 1,
    possession: "HOME", // "HOME" or "AWAY"
    status: "LIVE",     // "LIVE", "PAUSED", "FINISHED"
    lastAction: "Game started."
};

const teams = {
    HOME: "67 Eagles",
    AWAY: "Marietta Marauders"
};

// 2. FORM BUILDER (The Hypermedia Engine)
const getForms = () => {
    const forms = {};

    if (game.status === "FINISHED") return forms;

    // Transition: Pause/Resume
    forms[game.status === "LIVE" ? "pauseGame" : "resumeGame"] = {
        method: "POST",
        _links: { target: { href: "/game/status" } },
        schema: {
            type: "object",
            properties: { 
                action: { type: "string", enum: ["PAUSE", "RESUME"] } 
            }
        }
    };

    if (game.status === "LIVE") {
        // Transition: Score (Offense Only)
        forms["recordScore"] = {
            method: "POST",
            _links: { target: { href: "/game/score" } },
            schema: {
                type: "object",
                required: ["points", "playerNumber"],
                properties: {
                    points: { type: "integer", enum: [1, 2, 3] },
                    playerNumber: { type: "integer" }
                }
            }
        };

        // Transition: Turnover (Switch Possession)
        forms["turnover"] = {
            method: "POST",
            _links: { target: { href: "/game/possession" } },
            schema: {
                type: "object",
                required: ["reason"],
                properties: {
                    reason: { type: "string", enum: ["STEAL", "OUT_OF_BOUNDS", "TRAVELING"] }
                }
            }
        };
    }

    return forms;
};

const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/hal+json');
    res.setHeader('Access-Control-Allow-Origin', '*');

    const url = new URL(req.url, `http://${req.headers.host}`);
    console.log(`\n[COURTSIDE] ${req.method} ${url.pathname}`);

    // ROOT / GAME STATE
    if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/game')) {
        const gameState = {
            "_links": {
                "self": { "href": "/game" },
                "homeTeam": { "href": "/teams/home" },
                "awayTeam": { "href": "/teams/away" }
            },
            "scoreboard": {
                "period": game.period,
                "clock": game.clock,
                [teams.HOME]: game.score.home,
                [teams.AWAY]: game.score.away
            },
            "possession": teams[game.possession],
            "status": game.status,
            "lastEvent": game.lastAction,
            "_forms": getForms()
        };
        res.writeHead(200);
        return res.end(JSON.stringify(gameState, null, 2));
    }

    // POST TRANSITIONS
    if (req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk.toString(); });
        req.on('end', () => {
            const data = JSON.parse(body || '{}');
            
            if (url.pathname === '/game/score') {
                const pts = parseInt(data.points);
                if (game.possession === "HOME") game.score.home += pts;
                else game.score.away += pts;
                game.lastAction = `${teams[game.possession]} player #${data.playerNumber} scores ${pts} points!`;
                // Automatic possession switch after score
                game.possession = game.possession === "HOME" ? "AWAY" : "HOME";
            }

            if (url.pathname === '/game/possession') {
                game.possession = game.possession === "HOME" ? "AWAY" : "HOME";
                game.lastAction = `Turnover! ${data.reason}. Possession: ${teams[game.possession]}`;
            }

            if (url.pathname === '/game/status') {
                if (data.action === "PAUSE") game.status = "PAUSED";
                if (data.action === "RESUME") game.status = "LIVE";
                game.lastAction = `Game ${game.status.toLowerCase()}`;
            }

            res.writeHead(200);
            return res.end(JSON.stringify({
                "_links": { "self": { "href": "/game" } },
                "message": "Play recorded."
            }, null, 2));
        });
        return;
    }

    res.writeHead(404);
    res.end(JSON.stringify({ error: "Whistle blown. Not found." }));
});

const PORT = 3000;
server.listen(PORT, () => console.log(`🏀 Basketball API Live on http://localhost:${PORT}`));