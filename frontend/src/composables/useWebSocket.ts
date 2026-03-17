import { ref, onUnmounted } from 'vue';

const unreadCount = ref(0);
const latestNotification = ref<any>(null);

let ws: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;

function getWsUrl() {
  const token = localStorage.getItem('token');
  if (!token) return null;
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${location.hostname}:8080/ws/notification?token=${token}`;
}

function connect() {
  const url = getWsUrl();
  if (!url) return;
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;

  ws = new WebSocket(url);

  ws.onopen = () => {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('ping');
    }, 30000);
  };

  ws.onmessage = (event) => {
    if (event.data === 'pong') return;
    try {
      const msg = JSON.parse(event.data);
      if (msg.type === 'notification') {
        latestNotification.value = msg.data;
        unreadCount.value = msg.unreadCount ?? unreadCount.value + 1;
      }
    } catch { /* ignore non-JSON */ }
  };

  ws.onclose = () => {
    cleanup();
    reconnectTimer = setTimeout(connect, 3000);
  };

  ws.onerror = () => { ws?.close(); };
}

function cleanup() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
}

function disconnect() {
  cleanup();
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  if (ws) { ws.onclose = null; ws.close(); ws = null; }
}

export function useWebSocket() {
  return { unreadCount, latestNotification, connect, disconnect };
}

export function setUnreadCount(count: number) {
  unreadCount.value = count;
}
