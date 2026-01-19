(ns louhi.middleware.clacks-overhead
  "See https://xclacksoverhead.org/home/about")


(def wrap-clacks-overhead
  {:name :wrap-clacks-overhead
   :wrap (fn [handler]
           (fn [req]
             (when-let [resp (handler req)]
               (update resp :headers assoc "x-clacks-overhead" "GNU Terry Pratchett"))))})
