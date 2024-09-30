(ns louhi.util.html
  (:require [dev.onionpancakes.chassis.core :as h]))


(defn script
  ([resources-repo script-name] (script resources-repo script-name {:deref  true
                                                                    :secure true}))
  ([resources-repo script-name {:keys [deref secure]}]
   [:script {:src       script-name
             :defer     deref
             :integrity (when secure
                          (-> resources-repo
                              (get script-name)
                              :louhi/resource-info
                              :resource-subresource-integrity))}]))


(defn style
  ([resources-repo style-name] (style resources-repo style-name {:secure true}))
  ([resources-repo style-name {:keys [secure]}]
   [:link {:href      style-name
           :type      "text/css"
           :rel       "stylesheet"
           :integrity (when secure
                        (-> resources-repo
                            (get style-name)
                            :louhi/resource-info
                            :resource-subresource-integrity))}]))


(defn html-page [& content]
  (let [[attrs content] (if (-> content first (map?))
                          [(first content) (rest content)]
                          [{:lang "en"} content])]
    [h/doctype-html5
     [:html attrs
      content]]))
