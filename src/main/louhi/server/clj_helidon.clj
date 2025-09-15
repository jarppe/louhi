(ns louhi.server.clj-helidon
  (:require [jarppe.clj-helidon :as helidon]
            [louhi.server.core :as core]))


(defn make-server
  "Create and start an instance of Helidon Web server. Accepts a ring handler and options.
   
   Currently supported options are:
      :host      Host name to use, defaults to \"127.0.0.1\"
      :port      Port to use, or 0 to let server pick any available port number. Defaults to 0
      :on-close  Optional zero arity fn called when server is closed
   
   The returned server object implements `java.io.Closeable`, so you can use it with 
   clojure.core/with-open:

   ```
   (with-open [s (create-server ...)]
     ... do something with server
   )
   ```"
  ^louhi.server.core.Server
  [handler opts]
  (let [server (helidon/create-server handler opts)]
    (core/server {:impl   ::clj-helidon
                  :close  (fn [] 
                            (helidon/close server)
                            (when-let [on-close (:on-close opts)]
                              (on-close))) 
                  :port   (fn [] 
                            (helidon/port server)) 
                  :status (fn [] 
                            (helidon/status server))})))
