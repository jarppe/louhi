(ns louhi.handler
  (:require  [ring.util.http-response :as resp]
             [ring.middleware.params :as params]
             [ring.middleware.cookies :as cookies]
             [muuntaja.core :as m]
             [reitit.ring :as ring]
             [reitit.coercion.malli :as mc]
             [reitit.ring.coercion :as rrc]
             [reitit.ring.middleware.muuntaja :as rm]
             [reitit.ring.middleware.exception :as exception]
             [louhi.htmx]))


(set! *warn-on-reflection* true)


(defn make-handler
  ([routes] (make-handler routes nil))
  ([routes handler-config]
   (let [{:keys [middleware not-found]} handler-config]
     (-> (ring/router routes {:data {:muuntaja   m/instance
                                     :coercion   mc/coercion
                                     :middleware (into [params/wrap-params
                                                        cookies/wrap-cookies
                                                        rm/format-middleware
                                                        exception/exception-middleware
                                                        rrc/coerce-request-middleware
                                                        rrc/coerce-response-middleware
                                                        louhi.htmx/wrap-htmx]
                                                       middleware)}})
         (ring/ring-handler (if (fn? not-found)
                              not-found
                              (constantly (or not-found
                                              (-> (resp/not-found "Not found")
                                                  (update :headers assoc
                                                          "content-type"  "text/plain; charset=utf-8"
                                                          "cache-control" "public, max-age=600, s-max-age=60"))))))))))



(comment
  (def handler (make-handler ["/jiihaa" {:get {:handler (constantly {:status 200
                                                                     :body   [:h1 "Jiihaa"]})}}]
                             {}))

  (handler {:request-method :get
            :uri            "/jiihaa"})
  ;; => {:status 200
  ;;     :body "<h1>Jiihaa</h1>"
  ;;     :headers {"content-type" "text/html; charset=utf-8"}}

  (handler {:request-method :get
            :uri            "/foo"})
  ;; => {:status 404,
  ;;     :headers {"content-type" "text/plain; charset=utf-8"
  ;;               "cache-control" "public, max-age=600, s-max-age=60"},
  ;;     :body "Not found"}
  )
