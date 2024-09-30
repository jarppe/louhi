(ns simple.resources
  (:require [integrant.core :as ig]
            [louhi.util.resources :as resources]))


(defmethod ig/init-key :simple/resources-repo [_ {:keys [dev?]}]
  (resources/make-resources-repo "public"
                                 "/s/"
                                 {:cache-control :immutable}
                                 dev?))
