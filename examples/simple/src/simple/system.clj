(ns simple.system
  (:require [integrant.core :as ig]
            [ring.util.http-response :as resp]
            [louhi.system :as system]
            [louhi.handler.resources :as resources]
            [louhi.middleware.htmx :as htmx]
            [louhi.dev.watch-service :as watch]
            [simple.routes :as routes]
            [simple.content :as content]))


(def public-resources "./examples/simple/public")


(defn system-map []
  {::watch/watch-handler {:watch-locations [{:root public-resources}]}
   :louhi/handler       {:routes (routes/routes {})
                         :opts   {:default-handlers      [(resources/resources-handler (resources/directory-resources-repository public-resources))
                                                          (ig/ref ::watch/watch-handler)
                                                          (constantly (resp/not-found "I can't even"))]
                                  :additional-middleware [[htmx/wrap-htmx {:to-full-page content/full-html-page}]]}}
   :louhi/server        {:handler (ig/ref :louhi/handler)
                         :config  {:host (or (System/getenv "HOST") "127.0.0.1")
                                   :port (or (System/getenv "PORT") "8080")}}})


(comment
  (system/start-system (system-map))
  (system/stop-system)
  )