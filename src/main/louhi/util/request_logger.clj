(ns louhi.util.request-logger
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))


(def wrap-request-logger
  {:name    :louhi/request-logger
   :compile (fn [route-data _]
              (let [route-id   (-> route-data :name (or "?"))
                    route-name (str \[ route-id \])]
                (fn [handler]
                  (fn [req]
                    (let [start (System/currentTimeMillis)]
                      (try
                        (let [resp (handler req)
                              end  (System/currentTimeMillis)]
                          (log/debug (-> req :request-method (name) (str/upper-case))
                                     (-> req :uri)
                                     "=>"
                                     route-name
                                     (-> resp :status)
                                     (str ": " (- end start) "ms")
                                     (if-let [{:keys [id role]} (-> req :session/session)]
                                       (format "account=%d, role=%s" id role)
                                       "")
                                     (if (-> req :headers (contains? "hx-request"))
                                       (->> req
                                            :headers
                                            (keep (fn [[k v]]
                                                    (when (and (not= k "hx-request")
                                                               (str/starts-with? k "hx-"))
                                                      (format "\n     %-15s  %s" k v))))
                                            (str/join))
                                       ""))
                          resp)
                        (catch Exception e
                          (let [end (System/currentTimeMillis)]
                            (if-let [resp (let [data (ex-data e)]
                                            (when (-> data :type (= :ring.util.http-response/response))
                                              (-> data :response)))]
                              (log/warn (-> req :request-method (name) (str/upper-case))
                                        (-> req :uri)
                                        "=>"
                                        route-name
                                        (-> resp :status)
                                        (str ": " (- end start) "ms"))
                              (log/error e
                                         (-> req :request-method (name) (str/upper-case))
                                         (-> req :uri)
                                         "=>"
                                         (-> e (.getClass) (.getName))
                                         (-> e (.getMessage) (or ""))
                                         ":"
                                         (- end start)
                                         "ms")))
                          (throw e))))))))})
