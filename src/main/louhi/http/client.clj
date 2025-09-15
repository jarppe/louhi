(ns louhi.http.client
  (:require [clojure.string :as str] 
            [louhi.util.async :as async])
  (:import (java.time Duration)
           (java.net URI
                     URLEncoder)
           (java.net.http HttpClient
                          HttpClient$Version
                          HttpClient$Redirect
                          HttpHeaders
                          HttpRequest
                          HttpRequest$Builder
                          HttpRequest$BodyPublisher
                          HttpRequest$BodyPublishers
                          HttpResponse
                          HttpResponse$BodyHandlers)
           (java.util.concurrent CompletableFuture)))


(set! *warn-on-reflection* true)


(defn new-client [{:keys [version connect-timeout follow-redirects executor]
                   :or   {version          :http-1-1
                          connect-timeout  (Duration/ofSeconds 10)
                          follow-redirects :never
                          executor         async/executor}}]
  (-> (HttpClient/newBuilder)
      (.version (case version
                  :http-1-1 HttpClient$Version/HTTP_1_1
                  :http-2   HttpClient$Version/HTTP_2))
      (.connectTimeout (if (instance? Duration connect-timeout)
                         connect-timeout
                         (Duration/ofMillis connect-timeout)))
      (.followRedirects (case follow-redirects
                          :never  HttpClient$Redirect/NEVER
                          :normal HttpClient$Redirect/NORMAL
                          :always HttpClient$Redirect/ALWAYS))
      (.executor executor)
      (.build)))


(defonce default-client
  (delay
    (new-client nil)))


(defn- input-stream? [body]
  (instance? java.io.InputStream body))


(defn- body-publisher ^HttpRequest$BodyPublisher [body]
  (cond
    (nil? body)          (HttpRequest$BodyPublishers/noBody)
    (bytes? body)        (HttpRequest$BodyPublishers/ofByteArray body)
    (input-stream? body) (HttpRequest$BodyPublishers/ofInputStream body)
    :else (throw (ex-info (str "don't know how to handle body of type " (type body)) {:body body}))))


(defn- set-headers ^HttpRequest$Builder [^HttpRequest$Builder builder headers]
  (doseq [[k v] headers]
    (.setHeader builder (name k) (str v)))
  builder)


(deftype HeadersAdapter [^HttpHeaders headers lookup]
  clojure.lang.ILookup
  (valAt [_ k] (lookup k))
  (valAt [_ k default-value] (or (lookup k) default-value))

  clojure.lang.IFn
  (invoke [_ k] (lookup k))
  (invoke [_ k default-value] (or (lookup k) default-value))

  clojure.lang.Associative
  (containsKey [_ k] (some? (lookup k)))
  (entryAt [_ k] (when-let [v (lookup k)] (clojure.lang.MapEntry. k v)))
  (equiv [this that] (.equals this that))

  clojure.lang.Seqable
  (seq [_]
    (->> headers
         (.map)
         (.entrySet)
         (.iterator)
         (iterator-seq)
         (map (fn [^java.util.Map$Entry entry]
                (let [k (key entry)
                      v (val entry)]
                  [k (java.util.List/.getFirst v)]))))))


(defn- headers-adapter [^HttpHeaders headers]
  (->HeadersAdapter headers
                    (fn [k]
                      (-> headers (.firstValue k) (.orElse nil)))))


(defmethod print-method HeadersAdapter [h w]
  (print-method (into {} (seq h)) w))


(defn request ^CompletableFuture [{:keys [client method ^String url headers query body]}]
  (let [client (or client @default-client)
        uri    (URI/create (if (seq query)
                             (-> (reduce-kv (fn [^StringBuilder url k v]
                                              (doto url
                                                (.append (-> k (name) (URLEncoder/encode)))
                                                (.append "=")
                                                (.append (-> v (str) (URLEncoder/encode)))
                                                (.append "&")))
                                            (-> (StringBuilder. url)
                                                (.append "?"))
                                            query)
                                 (str))
                             url))]
    (-> (.sendAsync ^HttpClient client
                    (-> (HttpRequest/newBuilder)
                        (.method (-> method (name) (str/upper-case)) 
                                 (body-publisher body))
                        (.uri uri)
                        (set-headers headers)
                        (.build))
                    (HttpResponse$BodyHandlers/ofInputStream))
        (async/then (fn [^HttpResponse resp]
                      {:status  (-> resp (.statusCode))
                       :headers (-> resp (.headers) (headers-adapter))
                       :body    (-> resp (.body))
                       :version (-> resp (.version) (.name) (keyword))})))))


(comment
  (-> (request {:method :GET
                :url    "https://metosin.fi/"})
      (deref)
      (update :body slurp))
  ;;=> {:status  308
  ;;    :headers {"cache-control"             "public, max-age=0, must-revalidate"
  ;;              "content-type"              "text/plain"
  ;;              "date"                      "Sun, 31 Aug 2025 08:10:13 GMT"
  ;;              "location"                  "/en"
  ;;              "server"                    "Vercel"
  ;;              "strict-transport-security" "max-age=63072000"
  ;;              "transfer-encoding"         "chunked"
  ;;              "x-vercel-id"               "arn1::g2tgt-1756627813162-91c0774a67c4"}
  ;;    :body    "Redirecting...\n"
  )


(defn GET
  ([url] (GET url nil))
  ([url options]
   (request (merge {:method :get
                    :url    url}
                   options))))


(defn POST
  ([url]      (POST url nil nil))
  ([url body] (POST url body nil))
  ([url body options]
   (request (merge {:method :post
                    :url    url
                    :body   body}
                   options))))


(defn PUT
  ([url]      (PUT url nil nil))
  ([url body] (PUT url body nil))
  ([url body options]
   (request (merge {:method :put
                    :url    url
                    :body   body}
                   options))))
