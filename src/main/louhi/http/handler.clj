(ns louhi.http.handler
  (:require [clojure.string :as str]))


(defn matches [req method uri]
  (let [request-method (:request-method req)
        request-uri    (:uri req)]
    (and (cond
           (keyword? method) (= method request-method)
           (ifn? method)     (method request-method)
           :else (throw (ex-info (str "don't know how to handle method: " (pr-str method)) {})))
         (cond
           (and (string? uri) (str/ends-with? uri "*"))
           (str/starts-with? request-uri (subs uri 0 (dec (count uri))))

           (string? uri)
           (= request-uri uri)

           (instance? java.util.regex.Pattern uri)
           (when-let [m (re-matcher uri request-uri)]
             (.find m)
             (->> (.namedGroups m)
                  (.keySet)
                  (reduce (fn [acc n]
                            (assoc acc (keyword n) (.group m n)))
                          {})))

           (ifn? uri)
           (uri request-uri)

           :else (throw (ex-info (str "don't know how to handle uri: " (pr-str uri)) {}))))))


(defn handle [method uri handler]
  (fn [req]
    (when-let [match (matches req method uri)]
      (handler (if (map? match) 
                 (assoc req :path-params match) 
                 req)))))


(defn make-handler [handlers]
  (->> handlers
       (map (fn [[method uri handler]]
              (handle method uri handler)))
       (apply some-fn)))


(comment
  (let [handler (make-handler [[:get "/"                  (fn [req] 1)]
                               [:get "/foo"               (fn [req] 2)]
                               [:get #"\/foo\/(?<id>\d+)" (fn [req] (-> req :path-params :id))]])]
    (handler {:request-method :get :uri "/foo/123"}))
  
  (let [handler (handle :get #"\/foo\/(?<id>\d+)"
                        (fn [req]
                          {:status 200
                           :body   (str "Hullo, your id is " (-> req :path-params :id))}))]
    (handler {:request-method :get
              :uri            "/foo/123"}))
  ;;=> {:status 200
  ;;    :body   "Hullo, your id is 123"}
  )
