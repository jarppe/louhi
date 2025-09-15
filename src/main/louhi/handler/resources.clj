(ns louhi.handler.resources
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [louhi.http.cache :as cache]
            [louhi.handler.resources.resources-util :as util]))


(set! *warn-on-reflection* true)


;;
;; Directory Repository:
;;
;; Creates a resource repository for given directory. This repository makes a lookup 
;; to provided directory when a resource is requested. This repository implementation
;; is a good fit if the directory contents can vary at runtime.
;;


(defn directory-resources-repository
  ([public-directory]            (directory-resources-repository public-directory "/"        cache/cache-control-public-no-cache))
  ([public-directory uri-prefix] (directory-resources-repository public-directory uri-prefix cache/cache-control-public-no-cache))
  ([public-directory uri-prefix cache-control]
   (let [uri-prefix     (if (str/ends-with? uri-prefix "/")
                          uri-prefix
                          (str uri-prefix "/"))
         uri-prefix-len (count uri-prefix)]
     (fn [uri]
       (when (str/starts-with? uri uri-prefix)
         (let [file-name (subs uri uri-prefix-len)
               resource  (or (let [file (io/file public-directory (str file-name ".gz"))]
                               (when (.isFile file)
                                 file))
                             (let [file (io/file public-directory file-name)]
                               (when (.isFile file)
                                 file)))]
           (when resource
             (util/resource-response resource cache-control))))))))


;;
;; Classpath resources repository:
;;


(defn classpath-resources-repository
  ([path-prefix]            (classpath-resources-repository path-prefix "/"        cache/cache-control-public-no-cache))
  ([path-prefix uri-prefix] (classpath-resources-repository path-prefix uri-prefix cache/cache-control-public-no-cache))
  ([path-prefix uri-prefix cache-control]
   (let [path-prefix    (if (str/starts-with? path-prefix "/")
                          (subs path-prefix 1)
                          path-prefix)
         path-prefix    (if (str/ends-with? path-prefix "/")
                          path-prefix
                          (str path-prefix "/"))
         uri-prefix     (if (str/ends-with? uri-prefix "/")
                          uri-prefix
                          (str uri-prefix "/"))
         uri-prefix-len (count uri-prefix)]
     (fn [uri]
       (when (str/starts-with? uri uri-prefix)
         (when-let [resource (io/resource (str path-prefix (subs uri uri-prefix-len)))]
           (util/resource-response resource cache-control)))))))


;;
;; Utility to create a resources repository from a map of string uri keys to resources:
;;


(defn resource-map->resources-repository
  ([uri->resource] (resource-map->resources-repository uri->resource cache/cache-control-public-no-cache))
  ([uri->resource cache-control]
   (reduce (fn [acc [uri resource]]
             (assoc acc uri (util/resource-response resource cache-control)))
           {}
           uri->resource)))


;;
;; Static Resources Repository:
;;
;; Scans the provided direcory ar creation time and creates an in-memory map for
;; information for available resources. When an resources is requested this repository 
;; implementation can returns the resource very fast. Use this implementation when the 
;; resources don't change at runtime.
;;


(defn static-resources-repository
  ([public-directory]            (static-resources-repository public-directory "/"        cache/cache-control-public-no-cache))
  ([public-directory uri-prefix] (static-resources-repository public-directory uri-prefix cache/cache-control-public-no-cache))
  ([public-directory uri-prefix cache-control]
   (let [public-directory (io/file public-directory)
         public-path      (.toPath public-directory)
         uri-prefix       (if (str/ends-with? uri-prefix "/")
                            uri-prefix
                            (str uri-prefix "/"))]
     (resource-map->resources-repository (->> public-directory
                                              (file-seq)
                                              (filter java.io.File/.isFile)
                                              (map (fn [^java.io.File resource]
                                                     (let [resource-uri (str uri-prefix (.relativize public-path (.toPath resource)))
                                                           resource-uri (if (str/ends-with? resource-uri ".gz")
                                                                          (subs resource-uri 0 (- (count resource-uri) 3))
                                                                          resource-uri)]
                                                       [resource-uri resource]))))
                                         cache-control))))


;;
;; Resources handler:
;;


(defn resources-handler [resources-repository]
  (fn [req]
    (when-let [resp (and (-> req :request-method #{:get :head})
                         (-> req :uri (resources-repository)))]
      (if (or (when-let [if-none-match (-> req :headers (get "if-none-match"))]
                (= if-none-match (-> resp :headers (get "etag"))))
              (when-let [if-modified-since (-> req :headers (get "if-modified-since"))]
                (>= (util/parse-rfc-1123-date-time if-modified-since)
                    (util/parse-rfc-1123-date-time (-> resp :headers (get "last-modified"))))))
        (-> resp
            (assoc :status 304)
            (dissoc :body))
        (if (-> req :request-method (= :get))
          resp
          (-> resp (dissoc :body)))))))
