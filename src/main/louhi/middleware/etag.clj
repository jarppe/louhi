(ns louhi.middleware.etag
  (:require [louhi.http.status :as status]))


(defn- body-etag [body]
  (str \" (-> body (hash) (abs) (Long/toHexString)) \"))


(defn wrap-etag [handler]
  (fn [req]
    (when-let [resp (handler req)]
      (let [resp-etag (or (-> resp :headers (get "etag"))
                          (-> resp :body (body-etag)))
            req-etag  (and (<= 200 (:status resp) 299)
                           (-> req :headers (get "if-none-match")))]
        (if (= req-etag resp-etag)
          (-> resp
              (assoc :status status/not-modified)
              (update :headers assoc "etag" resp-etag)
              (dissoc :body))
          (-> resp
              (update :headers assoc "etag" resp-etag)))))))
