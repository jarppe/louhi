(ns louhi.util.cookie-session
  (:require [ring.util.http-response :as resp]
            [louhi.util.jwt :as jwt]))


(def CookieConfig [:map
                   [:jwt-secret    string?]
                   [:cookie-name   string?]
                   [:secure?       boolean?]
                   [:max-age-sec   int?]
                   [:renew-ttl-sec int?]])


; There's no well defined way to remove a cookie. The commonly used and generally
; working way to remove cookie is to set the cookie to value with `expires` set 
; to Thu, 01 Jan 1970 00 :00:00 GMT. This is sometimes called "cookie depth charge".

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


(def wrap-cookie-session
  {:name :louhi/cookie-session
   :wrap (fn [handler config]
           (let [cookie-name   (-> config :cookie-name)
                 jwt-secret    (-> config :jwt-secret)
                 renew-ttl-sec (-> config :renew-ttl-sec)
                 make-cookie   (make-cookie-factory config)]
             (fn [req]
               (let [session (when-let [cookie-value (-> req :cookies (get cookie-name) :value)]
                               (or (jwt/open-jwt cookie-value jwt-secret)
                                   (resp/unauthorized! {:message "Authorization required"})))
                     resp    (-> (if session
                                   (assoc req
                                          :session/session session
                                          :session/source :cookie)
                                   req)
                                 (handler))]
                 (cond
                   ; Response has set session or cleared session:
                   (contains? resp :session/session)
                   (update resp :cookies assoc cookie-name (if-let [session (:session/session resp)]
                                                             (make-cookie session)
                                                             cookie-depth-charge))

                   ; We have valid session but the token is about to expire, create new
                   ; fresh cookie to response:
                   (and resp session (-> session :ttl (< renew-ttl-sec)))
                   (update resp :cookies assoc cookie-name (make-cookie session))

                   ; None of the above:
                   :else
                   resp)))))})


;;
;; Compiles into all "non-public" routes, that means routes that do not have
;; the `:public true`. When called, it handles requests that do not have the
;; session active by responding with login form. If the session is active the
;; call is padded to next handler.
;;


(defn- resp? [x]
  (and (-> x (map?))
       (-> x :status (int?))))


(defn- wrap-require-session-handler
  ([handler] (wrap-require-session-handler handler nil))
  ([handler {:keys [session-required-resp]}]
   (let [session-required-resp (cond
                                 (nil? session-required-resp) (constantly (resp/unauthorized))
                                 (resp? session-required-resp) (constantly session-required-resp)
                                 (fn? session-required-resp) session-required-resp
                                 :else (throw (ex-info "unsupported session-required-resp" {:session-required-resp session-required-resp})))]
     (fn [req]
       (if (-> req :session/session (some?))
         (handler req)
         (session-required-resp req))))))


(def wrap-require-session
  {:name    :louhi/require-session
   :compile (fn [{:keys [public]} _]
              (when (not public)
                wrap-require-session-handler))})
