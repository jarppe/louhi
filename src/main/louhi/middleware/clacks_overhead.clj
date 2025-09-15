(ns louhi.middleware.clacks-overhead
  "See https://xclacksoverhead.org/home/about")


(defn wrap-clacks-overhead [handler]
  (fn [req]
    (when-let [resp (handler req)]
      (update resp :headers assoc "x-clacks-overhead" "GNU Terry Pratchett"))))
