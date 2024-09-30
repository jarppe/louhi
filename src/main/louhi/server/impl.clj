(ns louhi.server.impl)


(set! *warn-on-reflection* true)


(defprotocol IServer
  (server-port [_])
  (close-server [_]))


(defrecord Server [server-port close-server]
  IServer
  (server-port [_] (server-port))
  (close-server [_] (close-server))

  java.io.Closeable
  (close [_] (close-server)))
