(ns louhi.server.http-kit
  (:require [clojure.set :as set]
            [org.httpkit.server :as http-kit]
            [louhi.server.core :as core]))


(set! *warn-on-reflection* true)


(defn make-server [handler config]
  (let [server (http-kit/run-server handler (-> (merge {:host "127.0.0.1"
                                                        :port 0}
                                                       config)
                                                (set/rename-keys {:host :ip})
                                                (assoc :legacy-return-value? false)
                                                (update :port (fn [port] (if (string? port) (parse-long port) port)))))]
    (core/server {:impl   ::kttp-kit
                  :close  (fn [] (http-kit/server-stop! server)) 
                  :port   (fn [] (http-kit/server-port server))
                  :status (fn [] (http-kit/server-status server))})))


(comment
  (with-open [server (make-server (constantly {:status 200
                                               :body   "Hey ho!"})
                                  {:host "127.0.0.1"
                                   :port 0})]
    (pr-str server))
  ;
  )