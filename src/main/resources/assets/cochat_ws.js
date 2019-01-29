let ws;

function initWebSocket(wsHost, wsPort, token, displayMessage) {
    if (!window.WebSocket) {
        throw new Error("Your browser does not support WebSockets. (Use Chrome)");
    }
    ws = new WebSocket(`ws://${wsHost}:${wsPort}/ws/${token}`);
    ws.onmessage = function (event) {
        displayMessage(JSON.parse(event.data));
    };

    window.onbeforeunload = function() {
        ws.onclose = function() {};
        ws.close();
    };
}

function sendWsMessage(message) {
    if (!ws) {
        alert("Your browser does not support WebSockets. (Use Chrome) or web socket connection not initialized");
        return false;
    }
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
    } else {
        alert("web socket connection closed");
    }
    return false;
}