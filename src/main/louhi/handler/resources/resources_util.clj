(ns louhi.handler.resources.resources-util
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.nio.charset StandardCharsets)
           (java.nio.file.attribute FileTime)
           (java.security MessageDigest)
           (java.util Base64)
           (java.nio.file Files
                          LinkOption)
           (java.time ZonedDateTime
                      ZoneOffset)
           (java.time.format DateTimeFormatter)))


(set! *warn-on-reflection* true)


;;
;; ============================================================================
;; Resource hash:
;; ============================================================================
;;


(defn get-content-hash ^bytes [resource]
  (with-open [in (io/input-stream resource)]
    (let [buffer (byte-array 8192)
          digest (MessageDigest/getInstance "SHA-256")]
      (loop []
        (let [c (.read in buffer)]
          (if (pos? c)
            (do (.update digest buffer 0 c)
                (recur))
            (.digest digest)))))))


(def ^java.util.HexFormat hex-format (java.util.HexFormat/of))


(defn resource-etag ^String [resource]
  (let [content-hash (get-content-hash resource)]
    (str \" (.formatHex hex-format content-hash 0 8) \")))


(defn resource-query-param-hash ^String [resource]
  (let [content-hash (get-content-hash resource)]
    (str "_h=" (.formatHex hex-format content-hash 0 8))))


(defn resource-subresource-integrity ^String [resource]
  (let [content-hash (get-content-hash resource)]
    (str "sha256-" (-> (Base64/getEncoder)
                       (.encode content-hash)
                       (String. StandardCharsets/UTF_8)))))


(comment
  (let [hash (get-content-hash (io/file "deps.edn"))]
    {:hash-length           (alength hash)
     :hash-hex              (.formatHex hex-format hash)
     :etag                  (resource-etag "deps.edn")
     :query-param           (resource-query-param-hash "deps.edn")
     :subresource-integrity (resource-subresource-integrity "deps.edn")}))


;;
;; ============================================================================
;; Resource content encoding and content type:
;; ============================================================================
;;


(def ext->content-encoding
  {"gz" "gzip"
   "br" "br"})


;; TODO: get more complete list of content types from somewhere
(def ext->content-type
  {"txt"   "text/plain; charset=utf-8"
   "html"  "text/html; charset=utf-8"
   "css"   "text/css; charset=utf-8"
   "js"    "application/javascript; charset=utf-8"
   "json"  "application/json; charset=UTF-8"
   "map"   "application/json; charset=utf-8"
   "ico"   "image/x-icon"
   "svg"   "image/svg+xml; charset=utf-8"
   "png"   "image/png"
   "ttf"   "font/ttf"
   "woff2" "font/woff2"})


(defn resource-content-encoding-and-content-type [resource]
  (let [resource-name (condp instance? resource
                        java.io.File (java.io.File/.getName resource)
                        java.net.URL (java.net.URL/.getFile resource))
        last-dot      (str/last-index-of resource-name \.)
        encoding-ext  (when last-dot
                        (-> (subs resource-name (inc last-dot))
                            #{"gz" "br"}))
        content-ext   (if encoding-ext
                        (when-let [second-last-dot (str/last-index-of resource-name \. (- (count resource-name)
                                                                                          (count encoding-ext)
                                                                                          2))]
                          (subs resource-name
                                (inc second-last-dot)
                                last-dot))
                        (when-let [last-dot (str/last-index-of resource-name \.)]
                          (subs resource-name (inc last-dot))))]
    {"content-encoding" (-> encoding-ext ext->content-encoding (or "identity"))
     "content-type"     (-> content-ext  ext->content-type     (or "application/octet-stream"))}))


(comment
  (resource-content-encoding-and-content-type (io/file "foo.js.gz"))
  ;;=> {"content-encoding" "gzip", "content-type" "application/javascript; charset=utf-8"}

  (resource-content-encoding-and-content-type (io/file "foo.js"))
  ;;=> {"content-encoding" "identity", "content-type" "application/javascript; charset=utf-8"}

  (resource-content-encoding-and-content-type (io/file "foo.gz"))
  ;;=> {"content-encoding" "gzip", "content-type" "application/octet-stream"}

  (resource-content-encoding-and-content-type (io/file "foo"))
  ;;=> {"content-encoding" "identity", "content-type" "application/octet-stream"}
  )


;;
;; ============================================================================
;; Resource info:
;; ============================================================================
;;


(defn resource-last-modified [resource]
  (when (instance? java.io.File resource)
    (-> (Files/getAttribute (java.io.File/.toPath resource)
                            "unix:ctime"
                            (into-array LinkOption []))
        (FileTime/.toInstant)
        (ZonedDateTime/ofInstant ZoneOffset/UTC)
        (.format DateTimeFormatter/RFC_1123_DATE_TIME))))


(defn resource-response
  ([resource] (resource-response resource nil))
  ([resource cache-control]
   {:status  200
    :headers (merge (resource-content-encoding-and-content-type resource)
                    {"etag"          (resource-etag resource)
                     "cache-control" (or cache-control "public, no-cache")}
                    (when-let [last-modified (resource-last-modified resource)]
                      {"last-modified" last-modified}))
    :body    resource}))


(comment
  (resource-response (io/file "./deps.edn"))
  ;;=> {:status  200
  ;;    :headers {"content-encoding" "identity"
  ;;              "content-type"     "application/octet-stream"
  ;;              "etag"             "\"1a606c14e2346a31\""
  ;;              "cache-control"    "public, no-cache"
  ;;              "last-modified"    "Fri, 29 Aug 2025 07:02:31 GMT"}
  ;;    :body    #object[java.io.File 0x7adf12a5 "./deps.edn"]}
  )


(defn parse-rfc-1123-date-time [^String date-time]
  (when date-time
    (try
      (-> (.parse DateTimeFormatter/RFC_1123_DATE_TIME date-time)
          (java.time.Instant/from)
          (.toEpochMilli))
      (catch java.time.format.DateTimeParseException _
        nil))))