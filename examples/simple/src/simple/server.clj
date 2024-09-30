(ns simple.server
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]

            [integrant.core :as ig]
            [ring.util.http-response :as resp]
            [jsonista.core :as json]

            [louhi.server :as server]
            [louhi.handler :as handler]

            [louhi.util.cache :as cache]
            [louhi.util.request-logger :as request-logger]
            [louhi.util.resources :as resources]
            [louhi.util.cookie-session :as cookie-session]

            [simple.app.core :as core]
            [simple.app.login :as login]))


(defn make-routes [env]
  (let [resources-handler (resources/resources-handler (-> env :resources-repo))]
    ["" {:middleware [[cookie-session/wrap-cookie-session (-> env :config :cookie)]
                      [cookie-session/wrap-require-session {:session-required-resp (login/session-missing-hander env)}]]}
     ["/s/**" {:get  {:name    :resources/get
                      :public  true
                      :htmx    false
                      :handler resources-handler}
               :head {:name    :resources/head
                      :public  true
                      :htmx    false
                      :handler resources-handler}}]
     (login/login-routes env)
     (core/app-routes env)
     (when (-> env :config :mode (= :dev))
       (log/warn "including dev routes")
       ["/dev"
        ["/ping" {:get {:name    :dev/ping
                        :public  true
                        :htmx    false
                        :handler (constantly {:status  200
                                              :headers {"content-type" "application/json; charset=utf-8"}
                                              :body    (-> {:message "pong"
                                                            :mode    (-> env :config :mode)}
                                                           (json/write-value-as-string))})}}]])]))


(def not-found
  (let [not-found-resp (-> (resp/not-found "The will is strong, but the path is lost")
                           (update :headers assoc
                                   "content-type"  "text/plain; charset=utf-8"
                                   "cache-control" "public, max-age=600, s-max-age=60"))]
    (fn [req]
      (log/warn "route miss:"
                (-> req :request-method (name) (str/upper-case))
                (-> req :uri))
      not-found-resp)))


(defmethod ig/init-key :simple/server [_ {:keys [env]}]
  (-> (make-routes env)
      (handler/make-handler {:middleware [[request-logger/wrap-request-logger]
                                          [cache/wrap-cache-headers]]
                             :not-found  not-found})
      (server/make-server (-> env :config :http))))


(defmethod ig/halt-key! :simple/server [_ server]
  (server/stop-server server))
