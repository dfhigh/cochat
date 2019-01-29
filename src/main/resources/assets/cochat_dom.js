const MAX_IMG_HEIGHT = 512, MAX_IMG_WIDTH = 512;
let hasWebSocket = false;
let token, selfIdentity;

async function initRoom(msgElementId, dialogElementId, containerElementId, roomNameElementId) {
    const description = await describe();
    hasWebSocket = description.webSocketEnabled === true && !!window.WebSocket;
    token = window.location.pathname.substring(1);

    const showMessage = msg => displayMessage(dialogElementId, msg, () => scroll2Bottom(containerElementId));

    if (hasWebSocket) {
        initWebSocket(window.location.hostname, description.webSocketPort, token, showMessage);
    }

    const room = await getRoom(token);
    document.getElementById(roomNameElementId).innerText = room.name;

    const msgElement = document.getElementById(msgElementId);
    msgElement.addEventListener("keyup", evt => onKeyUpSend(msgElement, evt));

    if (hasWebSocket) {
        syncMessages(token, showMessage).catch(e => {
            console.error(`failed to sync historical messages for room ${token}`, e);
            alert('failed to sync init messages');
        });
    } else {
        syncPeriodically(token, showMessage).catch(e => {
            console.error(`failed to sync messages for room ${token}`, e)
        });
    }
}

async function ensureSession(onPresent = undefined, onAbsent = undefined) {
    const chatter = await getChatter();
    if (!chatter || !chatter.name) {
        return onAbsent && onAbsent();
    }
    selfIdentity = chatter.identity;
    return onPresent && onPresent(chatter);
}

function onKeyUpSend(msgElement, event) {
    if (event.keyCode === 13) {
        if (event.ctrlKey) {
            msgElement.value = msgElement.value + "\n";
        } else {
            event.preventDefault();
            sendTextMessage(msgElement.id);
        }
    }
}

async function sendTextMessage(msgElementId) {
    const msgElement = document.getElementById(msgElementId);
    const message = msgElement.value;
    if (message !== '') {
        if (message.length > 1024) {
            alert("message length can't exceed 1024");
            return false;
        }
        if (hasWebSocket) await sendWsMessage(message);
        else await sendText(token, message);
        msgElement.value = '';
    }
}

async function upload(uploadElementId, onSuccess = undefined, onFail = undefined) {
    const uploadElement = document.getElementById(uploadElementId);
    const file = uploadElement.files[0];
    if (file.size > 10485760) {
        console.error(`${file.name} size too large`);
        alert(`file too large, max allowed size is 10MB`);
        return false;
    }
    try {
        const msg = await sendUpload(token, file);
        uploadElement.value = '';
        return onSuccess && onSuccess(msg);
    } catch (e) {
        return onFail && onFail(e);
    }
}

function scroll2Bottom(containerElementId) {
    const element = document.getElementById(containerElementId);
    element.scrollTop = element.scrollHeight;
}

function displayMessage(dialogElementId, message, callback = undefined) {
    const element = message.type === 'Notification' ? createNotificationElement(message) : createMessageElement(message);
    document.getElementById(dialogElementId).appendChild(element);
    if (callback) callback();
}

/*
<div class="notification">
    <p class="header">
        <span class="hint">2019-01-09T22:58:37.465</span>
    </p>
    <p class="hint"></p>
</div>
*/
function createNotificationElement(message) {
    if (!message) return document.createTextNode('');

    const timeElement = document.createElement("span");
    timeElement.classList.add("hint");
    timeElement.innerHTML = new Date(message.timestamp).toISOString();

    const headerElement = document.createElement("p");
    headerElement.classList.add("header");
    headerElement.appendChild(timeElement);

    const contentElement = document.createElement("p");
    contentElement.classList.add("hint");
    contentElement.appendChild(document.createTextNode(message.content));

    const element = document.createElement("div");
    element.classList.add("notification");
    element.appendChild(headerElement);
    element.appendChild(contentElement);
    return element;
}

/*
<div class="msg">
    <p class="header">
        <span class="hint">2019-01-09T22:58:37.465</span>
        <span class="chatter">messi</span>
    </p>
    <p class="content"></p>
</div>
*/
function createMessageElement(message) {
    if (!message) return document.createTextNode('');

    const timeElement = document.createElement("span");
    timeElement.classList.add("hint");
    timeElement.innerHTML = new Date(message.timestamp).toISOString();

    const chatterElement = document.createElement("span");
    chatterElement.classList.add("chatter");
    chatterElement.innerHTML = message.author.name;

    const headerElement = document.createElement("p");
    headerElement.classList.add("header");
    headerElement.appendChild(timeElement);
    headerElement.appendChild(chatterElement);

    if (message.author.identity === selfIdentity) {
        const selfElement = document.createElement("span");
        selfElement.classList.add("hint");
        selfElement.innerHTML = "(self)";
        headerElement.appendChild(selfElement);
    }

    const contentElement = document.createElement("p");
    contentElement.classList.add("content");
    contentElement.appendChild(createContentElement(message));

    const msgElement = document.createElement("div");
    msgElement.classList.add("msg");
    msgElement.appendChild(headerElement);
    msgElement.appendChild(contentElement);
    return msgElement;
}

function createContentElement(message) {
    if (message.type === 'Text') {
        const content = message.text.trimRight().replace(/\n/g, "<br/>");
        return document.createTextNode(content);
    } else if (message.type === 'RawFile') {
        const linkElement = document.createElement("a");
        linkElement.href = `/api/files/${message.token}`;
        linkElement.innerHTML = message.name;
        return linkElement;
    } else if (message.type === 'Image') {
        const imgElement = document.createElement("img");
        imgElement.src = `/api/files/${message.token}`;
        const imgDimensions = getFixedImageDimensions(message.height, message.width);
        imgElement.height = imgDimensions.scaledHeight;
        imgElement.width = imgDimensions.scaledWidth;
        return imgElement;
    } else {
        console.error(`unknown message type ${message.type}`);
        return document.createTextNode("");
    }
}

function getFixedImageDimensions(height, width) {
    let scaledHeight = height, scaledWidth = width;
    if (scaledHeight > MAX_IMG_HEIGHT && scaledWidth > MAX_IMG_WIDTH) {
        const hScaleRate = MAX_IMG_HEIGHT*1.0/scaledHeight, wScaleRate = MAX_IMG_WIDTH*1.0/scaledWidth;
        if (hScaleRate >= wScaleRate) {
            scaledHeight = Math.round( scaledHeight*wScaleRate);
            scaledWidth = MAX_IMG_WIDTH;
        } else {
            scaledHeight = MAX_IMG_HEIGHT;
            scaledWidth = Math.round(scaledWidth*hScaleRate);
        }
    } else if (scaledHeight > MAX_IMG_HEIGHT) {
        scaledHeight = MAX_IMG_HEIGHT;
        scaledWidth = Math.round(MAX_IMG_HEIGHT*1.0/scaledHeight);
    } else if (scaledWidth > MAX_IMG_WIDTH) {
        scaledHeight = Math.round(MAX_IMG_WIDTH*1.0/scaledWidth);
        scaledWidth = MAX_IMG_WIDTH;
    }
    return { scaledHeight, scaledWidth };
}
