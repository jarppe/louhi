(ns simple.config
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))


(set! *warn-on-reflection* true)


(defn- prop
  ([prop-name] (prop prop-name nil))
  ([prop-name default-value]
   (or (System/getProperty prop-name)
       default-value
       (throw (ex-info "Missing Java property" {:type :prop
                                                :name prop-name})))))


(defn- env
  ([env-name] (env env-name nil))
  ([env-name default-value]
   (or (System/getenv env-name)
       default-value
       (throw (ex-info "Missing env config" {:type :env
                                             :name env-name})))))


(defn- secret
  ([file-name] (secret file-name nil))
  ([file-name default-value]
   (or (some (fn [dir]
               (let [f (io/file dir file-name)]
                 (when (.exists f)
                   (-> (slurp f)
                       (str/trim)))))
             ["./secrets"
              "/var/run/secrets"])
       default-value
       (throw (ex-info "Missing secret" {:type :secret
                                         :name file-name})))))


(defn load-config []
  (let [mode (let [mode (or (prop "MODE")
                            (env "MODE")
                            (throw (ex-info "missing MODE" {})))]
               (-> mode
                   (keyword)
                   #{:dev :prod}
                   (or (throw (ex-info (str "illegal MODE: \"" mode "\"") {:mode mode})))))]
    {:mode      mode
     :resources {:resources-root "public"
                 :uri-prefix     "/s/"}
     :http      {:type        :http-kit
                 :host        (env "HOST" "0.0.0.0")
                 :port        (-> (env "PORT" "8080") (parse-long))
                 :dev-watcher {:enable (= mode :dev)
                               :root   "public"}}
     :cookie    {:jwt-secret    (secret "jwt-secret")
                 :cookie-name   (str "simple-example-session-" (name mode))
                 :max-age-sec   3600
                 :secure?       (not= mode :dev)
                 :renew-ttl-sec 60}}))
