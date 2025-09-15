(ns louhi.middleware.cookie-session
  "Louhi cookie session middleware.
   Requires Buddy sign and Ring core:
     buddy/buddy-sign {:mvn/version \"3.5.351\"}
     ring/ring-core   {:mvn/version \"1.14.2\"}
   Apply cookies middleware `ring.middleware.cookies/wrap-cookies` before 
   this middleware"
  (:require [louhi.util.jwt :as jwt]
            [louhi.http.status :as status]
            [louhi.http.resp :as resp])
  (:import (java.time Duration)))


;; There's no well defined way to remove a cookie. The commonly used and generally
;; working way to remove cookie is to set the cookie to value with `expires` set 
;; to Thu, 01 Jan 1970 00 :00:00 GMT. This is sometimes called "cookie depth charge".

(def ^:private cookie-depth-charge {:value     ""
                                    :path      "/"
                                    :http-only true
                                    :same-site :strict
                                    :expires   "Thu, 01 Jan 1970 00:00:00 GMT"})


(defn- make-cookie-factory [{:keys [jwt-secret max-age-sec secure?]}]
  (fn [session]
    {:value     (jwt/make-jwt (-> session
                                  (dissoc :iat)
                                  (dissoc :exp))
                              jwt-secret
                              max-age-sec)
     :path      "/"
     :http-only true
     :same-site :strict
     :max-age   max-age-sec
     :secure    secure?}))


(def default-config {:cookie-name   "session"
                     :secure?       true
                     :max-age-sec   (-> (Duration/ofHours 1)
                                        (.toSeconds))
                     :renew-ttl-sec (-> (Duration/ofMinutes 10)
                                        (.toSeconds))})


(defn wrap-cookie-session [handler session-config]
  (let [config        (merge default-config session-config)
        cookie-name   (-> config :cookie-name)
        jwt-secret    (-> config :jwt-secret)
        renew-ttl-sec (-> config :renew-ttl-sec)
        make-cookie   (make-cookie-factory config)]
    (fn [req]
      (let [session (when-let [cookie-value (-> req :cookies (get cookie-name) :value)]
                      (or (jwt/open-jwt cookie-value jwt-secret)
                          (resp/error! {:status status/unauthorized})))
            resp    (handler (if session
                               (assoc req
                                      :louhi.session/session session
                                      :louhi.session/source :cookie)
                               req))]
        (cond
          ;; Response has set session or cleared session:
          (contains? resp :louhi.session/session)
          (update resp :cookies assoc cookie-name (if-let [session (:louhi.session/session resp)]
                                                    (make-cookie session)
                                                    cookie-depth-charge))

          ;; We have valid session but the token is about to expire, create new
          ;; fresh cookie to response:
          (and resp session (-> session :ttl (< renew-ttl-sec)))
          (update resp :cookies assoc cookie-name (make-cookie session))

          ;; None of the above:
          :else
          resp)))))
