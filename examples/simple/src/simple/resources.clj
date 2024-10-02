(ns simple.resources
  (:require [integrant.core :as ig]
            [louhi.util.resources :as resources]))


(defmethod ig/init-key :simple/resources-repo [_ {:keys [dev?]}]
  (merge (resources/make-resources-repo "public"
                                        "/s/"
                                        {:cache-control :immutable}
                                        dev?)
         (resources/louhi-resources-repo "/s/louhi")))
