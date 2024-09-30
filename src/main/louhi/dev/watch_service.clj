(ns louhi.dev.watch-service
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.nio.file Path
                          FileSystems
                          WatchKey
                          WatchEvent
                          WatchEvent$Kind
                          StandardWatchEventKinds)
           (java.util.concurrent.atomic AtomicLong)))


(set! *warn-on-reflection* true)


(defprotocol IWatchService
  (next-listener-key [this])
  (add-listener [this listener-key on-event])
  (remove-listener [this listener-key])
  (close-service [_]))


(defrecord WatchService [watch-services listeners ^AtomicLong atomic-long]
  IWatchService
  (next-listener-key [_]
    (str (.getAndIncrement atomic-long)))

  (add-listener [this listener-key on-event]
    (swap! listeners assoc listener-key on-event)
    this)

  (remove-listener [this listener-key]
    (swap! listeners dissoc listener-key)
    this)

  (close-service [_]
    (doseq [watch-service watch-services]
      (watch-service))
    (doseq [listener (vals @listeners)]
      (listener)))

  java.io.Closeable
  (close [this]
    (close-service this)))


;; calva does not yet support ^java.nio.file.WatchEvent$Kind/1 syntax:
(def ^"[Ljava.nio.file.WatchEvent$Kind;" watch-kinds
  (into-array WatchEvent$Kind [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE]))


(defn- new-watch-service [on-watch-event root dir]
  (let [root          (->> root (io/file) (.toPath))
        dir           (->> dir (io/file) (.toPath) (.resolve root))
        watch-service (-> (FileSystems/getDefault) (.newWatchService))
        thread        (.start (Thread/ofVirtual)
                              (fn []
                                (try
                                  (while true
                                    (let [^WatchKey k (.take watch-service)]
                                      (try
                                        (doseq [^WatchEvent event (.pollEvents k)
                                                :let  [^WatchEvent$Kind kind (.kind event)
                                                       ^Path context (.context event)]]
                                          (on-watch-event {:type  :file
                                                           :event (-> kind
                                                                      (.name)
                                                                      (subs 6) ; strip leading "ENTRY_"
                                                                      (str/lower-case))
                                                           :file  (->> (.resolve dir context)
                                                                       (.relativize root)
                                                                       (str "/"))}))
                                        (finally
                                          (.reset k)))))
                                  (catch java.nio.file.ClosedWatchServiceException _)
                                  (catch InterruptedException _))))]
    (.register dir watch-service watch-kinds)
    (fn []
      (.interrupt thread)
      (.close watch-service))))


(defn make-watch-service [{:keys [root watch-paths]
                           :or   {root        "public"
                                  watch-paths [""]}}]
  (let [listeners      (atom {})
        on-watch-event (fn [event]
                         (doseq [listener (vals @listeners)]
                           (try
                             (listener event)
                             (catch Exception e
                               (log/error e "error while notifying listener")))))
        watch-services (mapv (partial new-watch-service on-watch-event root)
                             watch-paths)]
    (->WatchService watch-services
                    listeners
                    (AtomicLong.))))


(defn close-watch-service [watch-service]
  (when watch-service
    (close-service watch-service)))
