(ns louhi.reitit.handler
  (:require [clojure.string :as str]
            [muuntaja.core :as m]
            [malli.transform :as mt]
            [reitit.ring :as ring]
            [reitit.coercion.malli :as mc]
            [reitit.ring.coercion]
            [reitit.ring.middleware.muuntaja]
            [muuntaja.format.core]
            [muuntaja.format.form]
            [louhi.reitit.html :as html]
            [louhi.http.query :as query]))


(set! *warn-on-reflection* true)


;; To make inter-op with other components easier we use *only* lower-case
;; header names. This is not only the most sensible default any way, but
;; actually mandatory requirement with HTTP/2:

(def ^:private lower-case-response-headers-middleware
  {:name :lower-case-headers-middleware
   :wrap (fn [handler]
           (fn [req]
             (when-let [resp (handler req)]
               (update resp :headers update-keys str/lower-case))))})


;; The reitit.ring.middleware.parameters/parameters-middleware enables the query parameters
;; but brokes form parameters. This middleware handles just the query part:


(def ^:private query-params-middleware
  {:name :query-params-middleware
   :wrap (fn [handler]
           (fn [req]
             (-> (assoc req :query-params (query/parse-query-string (:query-string req)))
                 (handler))))})


;;
;; Common middleware:
;;



(def default-middleware [lower-case-response-headers-middleware
                         query-params-middleware
                         reitit.ring.middleware.muuntaja/format-middleware
                         reitit.ring.coercion/coerce-request-middleware
                         reitit.ring.coercion/coerce-response-middleware])



;;
;; Malli coercions:
;;

(def coercions (mc/create (-> mc/default-options
                              (assoc-in [:transformers :body :default] (mt/transformer mt/string-transformer
                                                                                       mt/default-value-transformer)))))


;;
;; Handler using Reitit:
;;


(defn reitit-handler [routes {:keys [to-full-page]}]
  (-> (ring/router routes {:data {:muuntaja   (-> m/default-options
                                                  (assoc :default-format "text/html")
                                                  (update :formats assoc
                                                          "text/html" html/html-format
                                                          "application/x-www-form-urlencoded" muuntaja.format.form/format)
                                                  (m/create))
                                  :coercion   coercions
                                  :middleware (into default-middleware [[html/wrap-htmx {:to-full-page to-full-page}]])}})
      (ring/ring-handler)))



(comment

  (let [ring-handler (reitit-handler ["/" {:get {:parameters {:query [:map [:name {:default :foo} [:enum :foo :bar]]]}
                                                 :handler    (fn [req]
                                                               {:status 200
                                                                :body   [:h1 "hullo, " (-> req :parameters :query :name (pr-str))]})}}]
                                     {:to-full-page (fn [body] [:html [:body body]])})]
    (-> (ring-handler {:request-method :get
                       :query-string   "name=foo"
                       :uri            "/"})
        :body
        slurp))
  
  (let [ring-handler (reitit-handler ["/" {:get {:parameters {:query [:map [:name :string]]}
                                                 :handler    (fn [req]
                                                               {:status 200
                                                                :body   [:h1 "hullo, " (-> req :parameters :query :name)]})}}]
                                     {:to-full-page (fn [body] [:html [:body body]])})]
    (-> (ring-handler {:request-method :get
                       :query-string   "name=world"
                       :uri            "/"})
        :body
        slurp))
  ;;=> "<h1>hullo, world</h1>"

  ;;
  ;; x-www-form-urlencoded:
  ;;

  (let [ring-handler (reitit-handler ["" {:htmx true}
                                      ["/" {:post {:parameters {:body [:map [:name :string]]}
                                                   :handler    (fn [req]
                                                                 {:status 200
                                                                  :body   [:h1 "hullo, " (-> req :parameters :body :name)]})}}]]
                                     {:to-full-page (fn [body] [:html [:body body]])})]
    (-> (ring-handler {:request-method :post
                       :uri            "/"
                       :headers        {"content-type" "application/x-www-form-urlencoded"
                                        "hx-request"   "true"}
                       :body           (-> "name=world"
                                           (.getBytes)
                                           (java.io.ByteArrayInputStream.))})
        :body
        slurp))
  ;;=> "<h1>hullo, world</h1>"

  ;;
  ;; HTML and HTMX:
  ;;

  (let [ring-handler (reitit-handler ["" {:htmx true}
                                      ["/hullo" {:get {:handler (constantly {:status 200
                                                                             :body   [:h1 "hullo"]})}}]
                                      ["/world" {:get {:handler (constantly {:status 200
                                                                             :body   [:h1 "world"]})}}]]
                                     {:to-full-page (fn [body] [:html [:body body]])})]
    (println "/hullo:" (-> (ring-handler {:request-method :get
                                          :uri            "/hullo"
                                          :headers        {"hx-request" "true"}})
                           :body
                           slurp))
    (println "/world:" (-> (ring-handler {:request-method :get
                                          :uri            "/world"})
                           :body
                           slurp)))
  ; prints:
  ; /hullo: <h1>hullo</h1>
  ; /world: <html><body><h1>world</h1></body></html>
  )
