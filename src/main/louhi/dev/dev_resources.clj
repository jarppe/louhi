(ns louhi.dev.dev-resources
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [louhi.util.resources :as resources])
  (:import (java.io File)))


(set! *warn-on-reflection* true)


(defn ->file ^File [resource-root uri]
  (let [file (io/file resource-root uri)]
    (when (and (.isFile file)
               (.canRead file))
      file)))


(defn make-get-resource [^File resource-root ^String uri-prefix opts]
  (with-meta
    (fn [uri]
      (when (str/starts-with? uri uri-prefix)
        (let [uri           (subs uri (count uri-prefix))
              resource-file (or (->file resource-root uri)
                                (->file resource-root (str uri ".gz")))]
          (when resource-file
            (resources/resource-resp resource-file (:cache-control opts))))))
    {:resource-root resource-root
     :uri-prefix    uri-prefix
     :opts          opts}))


(declare new-dev-resources-repo)


(deftype DevResourcesRepo [overlay-repo get-resource]
  clojure.lang.ILookup
  (valAt [_this uri] (or (get overlay-repo uri)
                         (get-resource uri)))
  (valAt [_this uri default] (or (get overlay-repo uri)
                                 (get-resource uri)
                                 default))

  clojure.lang.IFn
  (invoke [_this uri] (or (get overlay-repo uri)
                          (get-resource uri)))
  (invoke [_this uri default] (or (get overlay-repo uri)
                                  (get-resource uri)
                                  default))

  clojure.lang.IPersistentCollection
  (cons [_this other]
    (new-dev-resources-repo (merge overlay-repo other) get-resource))

  clojure.lang.Associative
  (containsKey [_this uri] (or (some? (get overlay-repo uri))
                               (some? (get-resource uri))))

  clojure.lang.IMeta
  (meta [_this] (meta get-resource)))


(defn new-dev-resources-repo [overlay-repo get-resource]
  (->DevResourcesRepo overlay-repo get-resource))


(defn dev-resources-repo
  ([resource-root uri-prefix] (dev-resources-repo resource-root uri-prefix nil))
  ([resource-root uri-prefix opts]
   (let [resource-path (io/file resource-root)
         uri-prefix    (if (str/ends-with? uri-prefix "**")
                         (subs uri-prefix 0 (- (count uri-prefix) 2))
                         uri-prefix)
         uri-prefix    (if (str/ends-with? uri-prefix "/")
                         uri-prefix
                         (str uri-prefix "/"))
         uri-prefix    (if (str/starts-with? uri-prefix "/")
                         uri-prefix
                         (str "/" uri-prefix))]
     (when (not (.isDirectory resource-path)) (throw (ex-info "resource-root must be a directory" {:resource-root resource-root})))
     (when (not (.canRead resource-path)) (throw (ex-info "resource-root must be a readable" {:resource-root resource-root})))
     (let [get-resource (make-get-resource resource-path
                                           uri-prefix
                                           opts)]
       (new-dev-resources-repo {} get-resource)))))


(comment
  (def repo (dev-resources-repo "public" "s" {:cache-control "no-store"}))

  (get repo "/s/styles.css")
  ;;=> {:status 200
  ;;    :headers {"content-type" "text/css; charset=utf-8",
  ;;              "content-length" 4463,
  ;;    ...

  (get repo "/foo")
  ;;=> nil

  (def repo2 (merge repo {"/foo" {:status 201}}))

  (get repo2 "/s/styles.css")
  ;;=> {:status 200
  ;;    :headers {"content-type" "text/css; charset=utf-8",
  ;;              "content-length" 4463,
  ;;    ...

  (get repo2 "/foo")
  ;;=> {:status 201}

  (meta repo2))