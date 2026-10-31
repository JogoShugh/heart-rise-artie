const http = require('http');

// 1. THE "DATABASE" (Our source of truth)
const database = {
    "jupiter-cell-4": {
        status: "EMPTY" // We will mutate this
    }
};

const rootEntryPoint = {
    "_links": { "self": { "href": "/" } },
    "message": "Welcome to the StarbornAg API",
    "_forms": {
        "searchBeds": {
            "method": "GET",
            "_links": { "target": { "href": "/api/beds", "templated": false } },
            "schema": {
                "type": "object",
                "required": ["search"],
                "properties": { "search": { "type": "string" } }
            }
        }
    }
};

const server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/hal+json');
    res.setHeader('Access-Control-Allow-Origin', '*');

    const url = new URL(req.url, `http://${req.headers.host}`);
    console.log(`\n[HTTP] ${req.method} ${url.pathname}${url.search}`);

    if (req.method === 'GET' && url.pathname === '/') {
        res.writeHead(200);
        return res.end(JSON.stringify(rootEntryPoint, null, 2));
    }

    if (req.method === 'GET' && url.pathname === '/api/beds') {
        const searchQuery = url.searchParams.get('search');
        if (searchQuery !== 'jupiter') {
            res.writeHead(200);
            return res.end(JSON.stringify({"error": `No bed named ${searchQuery}`}, null, 2));
        }

        // Dynamically read from the DB for the search result summary
        const currentStatus = database["jupiter-cell-4"].status;
        const searchResults = {
            "_links": { "self": { "href": req.url } },
            "_embedded": {
                "results": [
                    {
                        "name": `Jupiter Bed - Cell 4 (${currentStatus})`,
                        "_links": { "details": { "href": "/api/beds/jupiter/cells/4" } }
                    }
                ]
            }
        };
        res.writeHead(200);
        return res.end(JSON.stringify(searchResults, null, 2));
    }

    if (req.method === 'GET' && url.pathname === '/api/beds/jupiter/cells/4') {
        // 2. DYNAMIC STATE GENERATION
        // Read the actual DB state right now
        const currentStatus = database["jupiter-cell-4"].status;

        // Build the object from scratch so it's fresh
        const cellState = {
            "bedId": "jupiter",
            "cellId": "bed-1-cell-4",
            "status": currentStatus,
            "_links": {
                "self": { "href": "/api/beds/jupiter/cells/4" },
                "up": { "href": "/api/beds/jupiter" }
            },
            // The forms physically change based on the fresh status
            "_forms": currentStatus === "EMPTY" ? {
                "sowSeed": {
                    "method": "POST",
                    "_links": { "target": { "href": "/api/beds/jupiter/cells/4/sow" } },
                    "schema": {
                        "type": "object",
                        "required": ["cropType", "seedCount"],
                        "properties": {
                            "cropType": { "type": "string" },
                            "seedCount": { "type": "integer" }
                        }
                    }
                }
            } : {
                "waterCell": {
                    "method": "POST",
                    "_links": { "target": { "href": "/api/beds/jupiter/cells/4/water" } },
                    "schema": {
                        "type": "object",
                        "required": ["volumeMl"],
                        "properties": {
                            "volumeMl": { "type": "integer" }
                        }
                    }
                }
            }
        };

        res.writeHead(200);
        return res.end(JSON.stringify(cellState, null, 2));
    }

    if (req.method === 'POST') {
        let body = '';
        req.on('data', chunk => { body += chunk.toString(); });
        req.on('end', () => {
            console.log(`[HTTP] Body received:`, body);

            // 3. MUTATE THE DATABASE
            if (url.pathname.includes('/sow')) {
                database["jupiter-cell-4"].status = "PLANTED";
            }

            // Tell the client where to go to see the new reality
            const responseHal = {
                "_links": { "self": { "href": "/api/beds/jupiter/cells/4" } },
                "message": "Action executed. Resource state has changed."
            };

            res.writeHead(200);
            return res.end(JSON.stringify(responseHal, null, 2));
        });
        return;
    }

    res.writeHead(404);
    res.end(JSON.stringify({ error: "Not found" }));
});

const PORT = 3000;
server.listen(PORT, () => console.log(`🌱 Mock API on http://localhost:${PORT}`));