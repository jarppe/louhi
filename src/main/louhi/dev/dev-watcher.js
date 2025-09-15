//
// Detect CSS changes and server resets.
//
// On CSS changes reloads the CSS files. On server resets waits for the 
// server to become available and then reloads the page.
//


// These are filled by the watch-service:

const eventType = "$EVENT_TYPE$"
const eventUrl  = "$EVENT_URL$"


// Open event source and set the event listeners:

const openSource = (listeners) => {
  const source = (eventType === "sse") ? new EventSource(eventUrl) : new WebSocket(eventUrl)
  for (const [event, listener] of Object.entries(listeners)) {
    if (listener) source.addEventListener(event, (e) => listener(source, e))
  }
  return source
}


// Try to reconnect. When reconnect succeeds do the whole page reload. If the reconnect
// fails keep re-trying every 500ms.

const reconnect = () => {
  openSource({
    open: () => window.location.reload(),
    error: (source) => { 
      source.close(); // Close source immediatelly to prevent automatic retry done on SSE EventSources.
      setTimeout(reconnect, 500) 
    }
  })
}


// Report connection lost and start the reconnecting process:

const reportErrorAndReconnect = (source) => {
  console.log("dev-watcher: start reconnecting...")
  source.close()
  const message = document.createElement("div")
  message.appendChild(document.createTextNode("Reconnecting..."))
  message.style = `
    position:         absolute; 
    left:             0; 
    right:            0; 
    bottom:           0; 
    background-color: red; 
    color:            white; 
    font-size:        2rem;
    padding:          0.5em;
  `
  document.body.appendChild(message)
  reconnect()
}

// Process incoming message:

const handleFileEvent = ({ file }) => {
  if (file.endsWith(".css")) {
    console.log("dev-watcher: css reset", file)
    document.querySelectorAll("link[rel=stylesheet]").forEach((link) => {
      const [href] = link.href.split("?")
      if (href.endsWith(file)) {
        link.href = href + "?" + Date.now()
      }
    })
  }
}

const handleMessage = (_, message) => {
  const event = JSON.parse(message.data)
  switch (event.type) {
    case "file":
      handleFileEvent(event)
      break
    case "keep-alive":
      break
    default:
      console.log("dev-watcher: unknown message:", message)
  }
}

// Start the watcher:

console.log("dev-watcher: starting watch...")
openSource({
  message: handleMessage,
  close: reportErrorAndReconnect,
  error: reportErrorAndReconnect,
})
