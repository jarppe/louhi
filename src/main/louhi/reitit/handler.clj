(ns louhi.reitit.handler
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ring.util.http-response :as resp]
            [muuntaja.core :as m]
            [muuntaja.format.core]
            [muuntaja.format.form]
            [malli.error :as me]
            [malli.transform :as mt]
            [reitit.ring :as ring]
            [reitit.coercion.malli :as mc]
            [reitit.ring.coercion]
            [reitit.ring.middleware.muuntaja]
            [louhi.reitit.html :as html]
            [louhi.http.query :as http-query]
            [louhi.middleware.request-logger :as request-logger]
            [louhi.middleware.clacks-overhead :as clacks-overhead]))


(set! *warn-on-reflection* true)


;; To make inter-op with other components easier we use *only* lower-case
;; header names. This is not only the most sensible default any way, but
;; actually mandatory requirement with HTTP/2:


(def lower-case-response-headers-middleware
  {:name :lower-case-headers-middleware
   :wrap (fn [handler]
           (fn [req]
             (when-let [resp (handler req)]
               (update resp :headers update-keys str/lower-case))))})



;; The reitit.ring.middleware.parameters/parameters-middleware enables the query parameters
;; but brokes form parameters. This middleware handles just the query part:


(def query-params-middleware
  {:name :query-params-middleware
   :wrap (fn [handler]
           (fn [req]
             (let [query-params (-> req :query-string (http-query/parse-query-string))]
               (-> (assoc req :query-params query-params)
                   (handler)))))})


;;
;; Malli coercions:
;;


(defn default-coercions []
  (mc/create (-> mc/default-options
                 (assoc-in [:transformers :body :default]
                           (mt/transformer mt/string-transformer
                                           mt/default-value-transformer)))))

;;
;; Handle exceptions and errors:
;;


(def wrap-handle-exceptions
  {:name :handle-exceptions-middleware
   :wrap (fn [handler]
           (fn [req]
             (try
               (handler req)
               (catch clojure.lang.ExceptionInfo e
                 (let [ex   (ex-data e)
                       type (-> ex :type)]
                   (case type
                     :ring.util.http-response/response (-> ex :response)
                     :reitit.coercion/request-coercion (resp/bad-request (str "Your fault: " (me/humanize ex)))
                     (throw e)))))))})


(def wrap-handle-errors
  {:name :handle-errors-middleware
   :wrap (fn [handler]
           (fn [req]
             (try
               (handler req)
               (catch Exception e
                 (log/error e "unhandled exception")
                 (resp/internal-server-error! "My bad")))))})


;;
;; Cache headers middleware:
;;


(def default-cache-headers {"cache-control" "private, no-store"
                            "vary"          "cookie, hx-request"})


(defn- apply-default-cache-headers [headers]
  (merge default-cache-headers headers))


(def wrap-cache-headers
  {:name :apply-default-cache-headers-middleware
   :wrap (fn [handler]
           (fn [req]
             (when-let [resp (handler req)]
               (update resp :headers apply-default-cache-headers))))})


;;
;; Default muuntaja instance for coercions:
;;


(defn default-muuntaja []
  (-> m/default-options
      (assoc :default-format "text/html")
      (update :formats assoc
              "text/html" html/html-format
              "application/x-www-form-urlencoded" muuntaja.format.form/format)
      (m/create)))


;;
;; Common middleware:
;;


(defn default-middleware
  ([]                      (default-middleware nil nil))
  ([additional-middleware] (default-middleware nil additional-middleware))
  ([prefix-middleware additional-middleware]
   (concat prefix-middleware
           [request-logger/wrap-request-logger
            clacks-overhead/wrap-clacks-overhead
            wrap-handle-errors
            wrap-handle-exceptions
            wrap-cache-headers
            lower-case-response-headers-middleware
            query-params-middleware
            reitit.ring.middleware.muuntaja/format-middleware
            reitit.ring.coercion/coerce-request-middleware
            reitit.ring.coercion/coerce-response-middleware]
           additional-middleware)))


;;
;; Handler using Reitit:
;;


(defn handler
  ([routes] (handler routes nil))
  ([routes {:keys [middleware coercions muuntaja additional-middleware]}]
   (-> (ring/router routes {:data {:muuntaja   (or muuntaja (default-muuntaja))
                                   :coercion   (or coercions (default-coercions))
                                   :middleware (or middleware (default-middleware additional-middleware))}})
       (ring/ring-handler (constantly (resp/not-found "I can't even"))))))



(comment

  (let [ring-handler (handler ["/" {:get {:parameters {:query [:map [:name {:default :foo} [:enum :foo :bar]]]}
                                          :handler    (fn [req]
                                                        {:status 200
                                                         :body   [:h1 "hullo, " (-> req :parameters :query :name (pr-str))]})}}])]
    (-> (ring-handler {:request-method :get
                       :query-string   "name=foo"
                       :uri            "/"})
        :body
        (slurp)))

  (let [ring-handler (handler ["/" {:get {:parameters {:query [:map [:name :string]]}
                                          :handler    (fn [req]
                                                        {:status 200
                                                         :body   [:h1 "hullo, " (-> req :parameters :query :name)]})}}])]
    (-> (ring-handler {:request-method :get
                       :query-string   "name=world"
                       :uri            "/"})
        :body
        (slurp)))
  ;;=> "<h1>hullo, world</h1>"

  ;;
  ;; x-www-form-urlencoded:
  ;;

  (let [ring-handler (handler ["" {:htmx true}
                               ["/" {:post {:parameters {:body [:map [:name :string]]}
                                            :handler    (fn [req]
                                                          {:status 200
                                                           :body   [:h1 "hullo, " (-> req :parameters :body :name)]})}}]])]
    (-> (ring-handler {:request-method :post
                       :uri            "/"
                       :headers        {"content-type" "application/x-www-form-urlencoded"
                                        "hx-request"   "true"}
                       :body           (-> "name=world"
                                           (.getBytes)
                                           (java.io.ByteArrayInputStream.))})
        :body
        (slurp)))
  ;;=> "<h1>hullo, world</h1>"

  ;;
  ;; HTML and HTMX:
  ;;

  (require '[louhi.middleware.htmx :as htmx])

  (let [middleware   [[htmx/wrap-htmx {:to-full-page (fn [body] [:html [:body body]])}]]
        routes       ["" {:htmx true}
                      ["/hullo" {:get {:handler (constantly {:status 200
                                                             :body   [:h1 "hullo"]})}}]]
        ring-handler (handler routes {:additional-middleware middleware})]
    (println "partial page:" (-> (ring-handler {:request-method :get
                                                :uri            "/hullo"
                                                :headers        {"hx-request" "true"}})
                                 :body
                                 slurp))
    (println "   full page:" (-> (ring-handler {:request-method :get
                                                :uri            "/hullo"})
                                 :body
                                 slurp)))
  ; prints:
  ; partial page: <h1>hullo</h1>
  ;    full page: <html><body><h1>hullo</h1></body></html>
  )
