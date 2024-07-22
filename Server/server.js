const net = require('net');
const http = require('http');
const WebSocket = require('ws');


var fs = require('fs');
var client = fs.readFileSync('client.html');


const server = http.createServer((req, res) => {
    // Serve the HTML file for WebSocket client
    if (req.method === 'GET' && req.url === '/') {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(client);
    }
});
server.listen(3000);



// WebSocket Server
const wss = new WebSocket.Server({ port: 8080 });

wss.on('connection', (ws) => {
    console.log('WebSocket Client connected');

    ws.on('message', (message) => {
        
       
            for (let client of wss.clients){
            if(client===ws.client){
                continue;
            }
            if (client.readyState === WebSocket.OPEN) {
                client.send(message);
            }
          }
        
    });

    ws.on('close', () => {
        console.log('WebSocket Client disconnected');
    });

    ws.on('error', (err) => {
        console.error(`WebSocket Error: ${err.message}`);
    });
});


console.log('WebSocket Server listening on ws://127.0.0.1:8080');
