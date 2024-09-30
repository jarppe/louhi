(ns louhi.util.resources
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [ring.util.http-response :as resp])
  (:import (java.nio.charset StandardCharsets)
           (java.security MessageDigest)
           (java.util Base64)
           (java.util.zip GZIPInputStream)))


(set! *warn-on-reflection* true)


;;
;; We use resource hash for three purposes:
;;   1) As "etag" in http caching, see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
;;   2) As query parameter for cache  busting
;;   3) For Subresource Integrity, see https://developer.mozilla.org/en-US/docs/Web/Security/Subresource_Integrity
;;


(defn- get-resource-hash ^bytes [^java.io.InputStream in]
  (let [buffer (byte-array 8192)
        digest (MessageDigest/getInstance "SHA-256")]
    (loop []
      (let [c (.read in buffer)]
        (if (pos? c)
          (do (.update digest buffer 0 c)
              (recur))
          (.digest digest))))))


(def ^:private ^java.util.HexFormat hex-format (java.util.HexFormat/of))


(defn- resource-hash->etag ^String [^bytes resource-hash]
  (str \" (.formatHex hex-format resource-hash 0 12) \"))


(defn- resource-hash->query-param ^String [^bytes resource-hash]
  (str "h=" (.formatHex hex-format resource-hash 0 12)))


(defn- resource-hash->subresource-integrity ^String [^bytes resource-hash]
  (str "sha256-" (-> (Base64/getEncoder)
                     (.encode resource-hash)
                     (String. StandardCharsets/UTF_8))))


(comment
  (def h (with-open [in (-> (io/file "public/README.md.gz")
                            (io/input-stream))]
           (get-resource-hash in)))

  (alength h)
  ;;=> 32

  (.formatHex hex-format h)
  ;;=> "b0a03cd1d058c514e5815f40ac180cd09266cddfc860ba76d631d943f02903bc"

  (resource-hash->subresource-integrity h)
  ;;=> "sha256-sKA80dBYxRTlgV9ArBgM0JJmzd/IYLp21jHZQ/ApA7w="

  (resource-hash->etag h)
  ;;=> "\"b0a03cd1d058c514e5815f40\""


  (resource-hash->query-param h)
  ;;=> "h=b0a03cd1d058c514e5815f40"
  )


(defmulti resource-info class)


(defmethod resource-info java.net.URL [^java.net.URL resource]
  (let [resource-name (-> resource (.getPath))
        gz?           (-> resource-name (str/ends-with? ".gz"))]
    {:resource-type     :url
     :resource-name     resource-name
     :resource-encoding (when gz? "gzip")
     :resource-length   (with-open [in (io/input-stream resource)]
                          (let [buffer (byte-array 8096)]
                            (loop [length 0]
                              (let [c (.read in buffer)]
                                (if (pos? c)
                                  (recur (+ length c))
                                  length)))))
     :resource-body     (reify clojure.lang.IDeref
                          (deref [_]
                            (io/input-stream resource)))}))


(defmethod resource-info java.io.File [^java.io.File resource]
  (let [resource-name (-> resource (.getName))
        gz?           (-> resource-name (str/ends-with? ".gz"))]
    {:resource-type     :file
     :resource-name     resource-name
     :resource-encoding (when gz? "gzip")
     :resource-length   (.length resource)
     :resource-body     (reify clojure.lang.IDeref
                          (deref [_]
                            resource))}))


;; TODO: pull more complete list from somewhere
(def ^:private content-types {"html"  "text/html; charset=utf-8"
                              "css"   "text/css; charset=utf-8"
                              "js"    "application/javascript; charset=utf-8"
                              "map"   "application/json; charset=utf-8"
                              "ico"   "image/x-icon"
                              "svg"   "image/svg+xml; charset=utf-8"
                              "png"   "image/png"
                              "ttf"   "font/ttf"
                              "woff2" "font/woff2"})


(defn- resource-content-type [resource-name]
  (let [gz?           (-> resource-name (str/ends-with? ".gz"))
        resource-name (if gz?
                        (subs resource-name 0 (- (count resource-name) 3))
                        resource-name)
        last-dot      (str/last-index-of resource-name ".")
        ext           (when last-dot
                        (subs resource-name (inc last-dot)))]
    (content-types ext "application/octet-stream")))


(defn- get-cache-control [cache-control]
  (case cache-control
    (nil :disabled false) "private, no-store"
    :revalidate           "public, no-cache"
    :immutable            "public, max-age=604800, s-maxage=604800, immutable"
    :10min                "public, max-age=60,  s-maxage=60,  stale-while-revalidate=10"
    :1h                   "public, max-age=600, s-maxage=600, stale-while-revalidate=60"
    cache-control))


(defn resource-resp [resource cache-control]
  (let [info          (resource-info resource)
        resource-hash (with-open [^java.io.InputStream in (-> info
                                                              :resource-body
                                                              (deref)
                                                              (io/input-stream))]
                        (let [gz? (-> info :resource-encoding (= "gzip"))
                              in  (if gz? (GZIPInputStream. in 8192) in)]
                          (get-resource-hash in)))
        info          (assoc info
                             :resource-hash resource-hash
                             :resource-etag (resource-hash->etag resource-hash)
                             :resource-query-param (resource-hash->query-param resource-hash)
                             :resource-subresource-integrity (resource-hash->subresource-integrity resource-hash))]
    (-> {:status  200
         :headers (cond-> {"content-type"   (-> info :resource-name (resource-content-type))
                           "content-length" (-> info :resource-length (str))
                           "etag"           (-> info :resource-etag)
                           "cache-control"  (get-cache-control cache-control)}
                    (:resource-encoding info) (assoc "content-encoding" (:resource-encoding info)))
         :body    (:resource-body info)}
        (assoc :louhi/resource-info info))))


(def ^:private ^java.nio.file.Path to-path
  (let [string-array (into-array String [])]
    (fn [^String s]
      (java.nio.file.Path/of s string-array))))


(defn resources-repo
  "Make an map where the uri is the key and a map of resource is val. The
   resource contains a ring response for valid request of the resource."
  ([resource-root uri-prefix] (resources-repo resource-root uri-prefix nil))
  ([resource-root uri-prefix opts]
   (let [resource-path (to-path resource-root)
         uri-prefix    (if (str/ends-with? uri-prefix "**")
                         (subs uri-prefix 0 (- (count uri-prefix) 2))
                         uri-prefix)
         uri-prefix    (if (str/ends-with? uri-prefix "/")
                         uri-prefix
                         (str uri-prefix "/"))
         uri-prefix    (if (str/starts-with? uri-prefix "/")
                         uri-prefix
                         (str "/" uri-prefix))]
     (with-meta
       (->> resource-path
            (.toFile)
            (file-seq)
            (reduce (fn [acc ^java.io.File resource-file]
                      (if (and (-> resource-file (.isFile))
                               (-> resource-file (.canRead))
                               (-> resource-file (.getName) (str/starts-with? ".") (not)))
                        (let [uri (str uri-prefix (->> (.toPath resource-file)
                                                       (.relativize resource-path)
                                                       (.toString)))
                              uri (if (str/ends-with? uri ".gz")
                                    (subs uri 0 (- (count uri) 3))
                                    uri)]
                          (assoc acc uri (resource-resp resource-file (:cache-control opts))))
                        acc))
                    {}))
       {:resource-root resource-root
        :uri-prefix    uri-prefix
        :opts          opts}))))


(defn get-louhi-resources [uri-prefix]
  (let [uri-prefix (if (str/ends-with? uri-prefix "**")
                     (subs uri-prefix 0 (- (count uri-prefix) 2))
                     uri-prefix)
        uri-prefix (if (str/ends-with? uri-prefix "/")
                     uri-prefix
                     (str uri-prefix "/"))
        uri-prefix (if (str/starts-with? uri-prefix "/")
                     uri-prefix
                     (str "/" uri-prefix))]
    (->> {"htmx.js"   "louhi/vend/htmx.js.gz"
          "alpine.js" "louhi/vend/alpine.js.gz"}
         (reduce (fn [acc [uri resource]]
                   (assoc acc
                          (str uri-prefix uri)
                          (resource-resp (io/resource resource)
                                         :immutable)))
                 {}))))


(comment
  (resources-repo "public" "s" {:cache-control "foo"})
  ;;=> {"/s/README.md" {:status 200,
  ;;                    :headers {"content-type" "application/octet-stream",
  ;;                              "content-length" 36,
  ;;                              "etag" "\"15c66a923b756658e84189ff\"",
  ;;                              "cache-control" "foo",
  ;;                              "content-encoding" "gzip"},
  ;;                    :body #<resources$eval32221$fn$reify__32223@3774e74f: #object[java.io.File 0x671516bb "public/README.md.gz"]>,
  ;;                    :louhi/resource-info {:resource-hash [21, -58, ...
  ;;                                          :resource-body #<resources$eval32221$fn$reify__32223@3774e74f: #object[java.io.File 0x671516bb "public/README.md.gz"]>,
  ;;                                          :resource-encoding "gzip",
  ;;                                          :resource-name "README.md.gz",
  ;;                                          :resource-etag "\"15c66a923b756658e84189ff\"",
  ;;                                          :resource-query-param "h=15c66a923b756658e84189ff",
  ;;                                          :resource-type :file,
  ;;                                          :resource-length 36,
  ;;                                          :resource-subresource-integrity "sha256-FcZqkjt1ZljoQYn/uZDWQTB9cm93/DiR5de3wgIoecM="}},
  ;;   ...
  )

(comment
  (def louhi (get-louhi-resources "/s/"))
  (keys louhi)
  ;;=> ("/s/htmx.js" "/s/alpine.js")
  (get louhi "/s/htmx.js")
  ;;=> {:status 200,
  ;;    :headers {"content-type" "application/javascript; charset=utf-8",
  ;;              "content-length" "15893",
  ;;              "etag" "\"e1746d9759ec0d43c5c28445\"",
  ;;              "cache-control" {:cache-control :immutable},
  ;;              "content-encoding" "gzip"},
  ;;    :body #<resources$eval30605$fn$reify__30609@38c69f0d: #object[java.io.BufferedInputStream 0x66a1e653 "java.io.BufferedInputStream@66a1e653"]>,
  ;;    :louhi/resource-info {:resource-hash [-31, 116, ...
  ;;                          :resource-body #<resources$eval30605$fn$reify__30609@38c69f0d: ...
  ;;                          :resource-encoding "gzip",
  ;;                          :resource-name "/Users/jarppe/swd/jarppe/louhi/resources/louhi/vend/htmx.js.gz",
  ;;                          :resource-etag "\"e1746d9759ec0d43c5c28445\"",
  ;;                          :resource-query-param "h=e1746d9759ec0d43c5c28445",
  ;;                          :resource-type :url,
  ;;                          :resource-length 15893,
  ;;                          :resource-subresource-integrity "sha256-4XRtl1nsDUPFwoRFIzOjELtf1yheusSy3Jv0TXK1qIc="}}
  )

(defn not-modified-resp [resp]
  (-> resp
      (assoc :status 304)
      (dissoc :body)
      ;; HTTP spec says 304 should contain _all_ thge same headers as the 200 response, including
      ;; content-length, but at least Jetty and Helidon barfs if content-length is present when 
      ;; the body is not.
      (update :headers dissoc "content-length")))


(defn resource-handler
  ([resource] (resource-handler resource {:cache-control :immutable}))
  ([resource {:keys [cache-control]}]
   (let [resp         (resource-resp resource cache-control)
         etag         (-> resp :louhi/resource-info :resource-etag)
         not-modified (not-modified-resp resp)]
     (fn [req]
       (cond
         (-> req :request-method (= :head)) (dissoc resp :body)
         (-> req :request-method (not= :get)) (resp/method-not-allowed)
         (-> req :headers (get "if-none-match") (= etag)) not-modified
         :else (update resp :body deref))))))


(defn resources-handler [resources-repo]
  (let [not-found (resp/not-found)]
    (fn [req]
      (if-let [resp (-> req :uri resources-repo)]
        (cond
          (-> req :request-method (= :head))
          (-> resp (dissoc :body))

          (-> req :request-method (not= :get))
          (resp/method-not-allowed)

          (= (-> req :headers (get "if-none-match"))
             (-> resp :headers (get "etag")))
          (not-modified-resp  resp)

          :else
          (update resp :body deref))
        not-found))))


(def wrap-resources
  {:name :louhi/resources
   :wrap (fn [handler {:keys [resources-repo]}]
           (let [resources-handler (resources-handler resources-repo)]
             (fn [req]
               (or (resources-handler req)
                   (handler req)))))})


(defn make-resources-repo [resources-root resources-uri-prefix opts dev?]
  (let [resources-repo-factory (if dev?
                                 (requiring-resolve 'louhi.dev.dev-resources/dev-resources-repo)
                                 resources-repo)]
    (merge (resources-repo-factory resources-root
                                   resources-uri-prefix
                                   opts)
           (get-louhi-resources (str resources-uri-prefix
                                     (if (str/ends-with? resources-uri-prefix "/")
                                       ""
                                       "/")
                                     "louhi")))))
