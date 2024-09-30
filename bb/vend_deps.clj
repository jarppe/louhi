(ns vend-deps
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [babashka.http-client :as http]))


(def deps (-> (fn []
                (-> (with-open [in (-> "vend-deps.edn"
                                       (io/input-stream)
                                       (io/reader)
                                       (java.io.PushbackReader.))]
                      (edn/read in))
                    :deps))
              (memoize)))


(comment
  (deps)
  ;; => {bigskysoftware/htmx {:github/tag "v1.9.12", 
  ;;                          :dist/url "https://unpkg.com/htmx.org@${VERSION}"},
  ;;     alpinejs/alpine {:github/tag "v3.13.10", 
  ;;                      :dist/url "https://cdn.jsdelivr.net/npm/alpinejs@${VERSION}/dist/cdn.min.js"}}
  )


(def parse-body (reify java.util.function.Function
                  (apply [_ resp]
                    (-> resp
                        :body
                        (json/parse-string true)))))


(def releases (-> (fn []
                    (let [lib-names (-> (deps)
                                        (keys))]
                      (->> lib-names
                           (map (fn [lib-name]
                                  (-> (http/get (str "https://api.github.com/repos/" lib-name "/tags")
                                                {:headers {"accept"          "application/json"
                                                           "accept-encoding" "gzip"}
                                                 :async   true})
                                      (.thenApply parse-body))))
                           (doall)
                           (map deref)
                           (zipmap lib-names))))
                  (memoize)))


(comment
  (releases)
  ;; => {bigskysoftware/htmx
  ;;     ({:name "v2.0.0-beta3",
  ;;       :zipball_url "https://api.github.com/repos/bigskysoftware/htmx/zipball/refs/tags/v2.0.0-beta3",
  ;;       :tarball_url "https://api.github.com/repos/bigskysoftware/htmx/tarball/refs/tags/v2.0.0-beta3",
  ;;       :commit
  ;;       {:sha "74744ac337357ed896dbfb35f1c45ecc8ecbec41",
  ;;        :url "https://api.github.com/repos/bigskysoftware/htmx/commits/74744ac337357ed896dbfb35f1c45ecc8ecbec41"},
  ;;       :node_id "MDM6UmVmMjU1Mzc4ODE2OnJlZnMvdGFncy92Mi4wLjAtYmV0YTM="}
  ;;      {:name "v2.0.0-beta2",
  ;;       :zipball_url "https://api.github.com/repos/bigskysoftware/htmx/zipball/refs/tags/v2.0.0-beta2",
  ;;       :tarball_url "https://api.github.com/repos/bigskysoftware/htmx/tarball/refs/tags/v2.0.0-beta2",
  ;;       :commit
  ;;       {:sha "bf692737011b40316397d55c3ee756fde6c502cf",
  ;;        :url "https://api.github.com/repos/bigskysoftware/htmx/commits/bf692737011b40316397d55c3ee756fde6c502cf"},
  ;;       :node_id "MDM6UmVmMjU1Mzc4ODE2OnJlZnMvdGFncy92Mi4wLjAtYmV0YTI="}
  ;; ...
  ;;     alpinejs/alpine
  ;;     ({:name "v3.13.10",
  ;;       :zipball_url "https://api.github.com/repos/alpinejs/alpine/zipball/refs/tags/v3.13.10",
  ;;       :tarball_url "https://api.github.com/repos/alpinejs/alpine/tarball/refs/tags/v3.13.10",
  ;;       :commit
  ;;       {:sha "851dc0ed2e15f34a7de6546ad71b581610adec58",
  ;;        :url "https://api.github.com/repos/alpinejs/alpine/commits/851dc0ed2e15f34a7de6546ad71b581610adec58"},
  ;;       :node_id "MDM6UmVmMjI0NjYzNjk2OnJlZnMvdGFncy92My4xMy4xMA=="}
  ;;      {:name "v3.13.9",
  ;;       :zipball_url "https://api.github.com/repos/alpinejs/alpine/zipball/refs/tags/v3.13.9",
  ;;       :tarball_url "https://api.github.com/repos/alpinejs/alpine/tarball/refs/tags/v3.13.9",
  ;;       :commit
  ;;       {:sha "6ac7cf209664aab72e82b1a24feb91f691400fd0",
  ;;        :url "https://api.github.com/repos/alpinejs/alpine/commits/6ac7cf209664aab72e82b1a24feb91f691400fd0"},
  ;;       :node_id "MDM6UmVmMjI0NjYzNjk2OnJlZnMvdGFncy92My4xMy45"}
  ;; ...

  ;
  )


(defn download-release [library-name tag]
  (let [release (->> (releases)
                     library-name
                     (some (fn [rel]
                             (when (-> rel :name (= tag))
                               rel))))
        lib     (-> (deps) library-name)]
    (with-open [in  (-> (http/get (-> (:dist/url lib)
                                      (str/replace (re-pattern "\\$\\{([^\\}]+)\\}")
                                                   (fn [[_ env]]
                                                     (case env
                                                       "VERSION" (-> release :name (subs 1))))))
                                  {:headers {"accept"          "application/json"
                                             "accept-encoding" "gzip"}
                                   :as      :stream})
                        :body)
                out (-> (:file/name lib)
                        (io/file)
                        (io/output-stream)
                        (cond->
                         (:file/gz? lib) (java.util.zip.GZIPOutputStream.)))]
      (io/copy in out)
      (.flush out))))


(defn download-all []
  (doseq [[library-name {tag :github/tag}] (deps)]
    (download-release library-name tag)))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn outdated []
  (let [releases (releases)
        outdated (keep (fn [[lib {:keys [github/tag]}]]
                         (let [latest-version (->> releases
                                                   lib
                                                   (map :name)
                                                   (remove (fn [version]
                                                             (or (str/index-of version "beta")
                                                                 (str/index-of version "alpha"))))
                                                   (first))]
                           (when-not (= tag latest-version)
                             [lib tag latest-version])))
                       (deps))]
    (if (seq outdated)
      (do (println "Outdated libs:")
          (doseq [[lib current latest] outdated]
            (println (format "   %-20s  %10s => %s"
                             lib
                             current
                             latest))))
      (println "All up to date 😀"))))
