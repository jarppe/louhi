(ns louhi.util.async
  (:import (java.util.function Supplier
                               Function)
           (java.util.concurrent Executors
                                 CompletableFuture)))


(set! *warn-on-reflection* true)


(defonce executor (doto (Executors/newVirtualThreadPerTaskExecutor)
                    (set-agent-send-executor!)
                    (set-agent-send-off-executor!)))


(defn async ^CompletableFuture [^Supplier f]
  (CompletableFuture/supplyAsync f executor))


(defn then ^CompletableFuture [^CompletableFuture fut ^Function f]
  (.thenApply fut f))
