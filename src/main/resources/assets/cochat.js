async function describe() {
    try {
        const response = await fetch('/api/description');
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch (e) {
        console.error('failed to get description', e);
        throw e;
    }
}

async function getChatter() {
    try {
        const response = await fetch('/api/sessions', { credentials: "include" });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch(e) {
        console.error('failed to get chatter', e);
        throw e;
    }
}

async function createChatter(name) {
    try {
        const response = await fetch('/api/sessions', {
            method: "POST",
            body: JSON.stringify({name}),
            headers: {"Content-Type": "application/json; charset=utf-8"},
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch(e) {
        console.error('failed to create chatter', e);
        throw e;
    }
}

async function createRoom(name) {
    try {
        const response = await fetch('/api/rooms', {
            method: 'POST',
            body: JSON.stringify({name}),
            headers: {"Content-Type": "application/json; charset=utf-8"},
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch(e) {
        console.error(`failed to create room ${name}`, e);
        throw e;
    }
}

async function getRoom(token) {
    try {
        const response = await fetch(`/api/rooms/${token}`, { credentials: "include" });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch (e) {
        console.error(`failed to retrieve room ${token}`, e);
        throw e;
    }
}

async function deleteRoom(token) {
    try {
        const response = await fetch(`/api/rooms/${token}`, {
            method: 'DELETE',
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
    } catch(e) {
        console.error(`failed to delete room ${token}`, e);
        throw e;
    }
}

async function sendText(token, message) {
    try {
        const response = await fetch(`/api/rooms/${token}/messages`, {
            method: 'POST',
            body: message,
            headers: {"Content-type": "text/plain; charset=utf-8"},
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
    } catch (e) {
        console.error(`failed to publish message`, e);
        throw e;
    }
}

async function sendUpload(token, file) {
    const form = new FormData();
    form.append("file", file);
    try {
        const response = await fetch(`/api/rooms/${token}/files`, {
            method: 'POST',
            body: form,
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        return await response.json();
    } catch (e) {
        console.error(`failed to upload file ${file.name}`, e);
        throw e;
    }
}

async function syncMessages(token, displayMessage, lastSyncedAt = 0) {
    try {
        const response = await fetch(`/api/rooms/${token}/messages?_timestamp=${lastSyncedAt}`, { credentials: "include" });
        if (!response.ok) {
            throw new Error(response.statusText);
        }
        const messages = await response.json();
        messages.forEach(message => {
            lastSyncedAt = message.timestamp;
            displayMessage(message);
        });
        return lastSyncedAt;
    } catch (e) {
        console.error(`failed to sync messages for room ${token}`, e);
        throw e;
    }
}

async function syncPeriodically(token, displayMessage, lastSyncedAt = 0) {
    lastSyncedAt = await syncMessages(token, displayMessage, lastSyncedAt);
    setTimeout(() => syncPeriodically(token, displayMessage, lastSyncedAt), 1000);
}
