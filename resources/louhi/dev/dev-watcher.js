//
// Detect CSS changes and server resets.
//
// On CSS changes reloads the CSS files.
// On server resets waits for server to become available and reloads page.
//

const reconnect = () => {
  try {
    const socket = new WebSocket("ws://localhost:8080/dev/watch")
    socket.addEventListener("open", () => window.location.reload())
    socket.addEventListener("error", (_) => setTimeout(reconnect, 500))
  } catch (e) {
    setTimeout(reconnect, 1000)
  }
}

const reportErrorAndReconnect = () => {
  const message = document.createElement("div")
  message.appendChild(document.createTextNode("Reconnecting..."))
  message.style = `
    position: absolute; 
    left: 0; 
    right: 0; 
    bottom: 0; 
    background-color: red; 
    color: white; 
    font-size: 2rem;
    padding: 0.5em;
  `
  document.body.appendChild(message)
  console.log("dev-watcher: reconnecting...")
  reconnect()
}

const watch = () => {
  console.log("dev-watcher: starting watch...")
  const socket = new WebSocket("ws://localhost:8080/dev/watch")
  socket.addEventListener("close", reportErrorAndReconnect)
  socket.addEventListener("error", reportErrorAndReconnect)
  socket.addEventListener("message", (message) => {
    const event = JSON.parse(message.data)
    const file = event.file
    if (file.endsWith(".css")) {
      console.log("dev-watcher: css reset", file)
      document.querySelectorAll("link[rel=stylesheet]").forEach((link) => {
        const [href] = link.href.split("?")
        if (href.endsWith(file)) {
          link.href = href + "?" + Date.now()
        }
      })
    }
  })
}

watch()
