(ns simple.app.login
  (:require [ring.util.http-response :as resp]
            [simple.app.user-store :as user-store]))


;;
;; Login form:
;;


(defn login-form
  ([] (login-form nil))
  ([{:keys [email password error]}]
   [:main.login
    [:form {:hx-post   "/session/login"
            :hx-target "main"
            :hx-swap   "outerHTML"}
     [:h2 "Login:"]
     [:fieldset
      [:legend {:for "login-email"} "E-mail:"]
      [:input {:id           "login-email"
               :name         "email"
               :type         "text"
               :value        (or email "")
               :required     true
              ; :autocomplete "email"
               :aria-invalid (when (some? error) "true")}]]
     [:fieldset
      [:legend {:for "login-password"} "Password:"]
      [:input {:id           "login-password"
               :name         "password"
               :type         "password"
               :value        (or password "")
               :required     true
              ; :autocomplete "current-password"
               :aria-invalid (when (some? error) "true")}]]
     (when error
       [:div.notification.error
        [:i "error"]
        [:span error]])
     [:div.controls
      [:button {:type "submit"}
       [:i "login"]
       [:span "Login"]]]]]))


(defn session-missing-hander [_env]
  (fn [_req]
    (-> (resp/unauthorized "Login first!")
        (update :headers assoc "content-type" "text/plain; charset=utf-8"))))


;;
;; Routes for session management:
;;


(defn login-routes [env]
  (let [user-store (-> env :user-store)]
    ["/session"
     ["/login" {:post {:name       :login/login
                       :public     true
                       :parameters {:form {:email    :string
                                           :password :string}}
                       :handler    (fn [req]
                                     (if-let [user-info (user-store/get-user-info user-store (-> req :parameters :form))]
                                       (-> (resp/ok)
                                           (assoc :session/session user-info)
                                           (update :headers assoc "hx-refresh" "true"))
                                       (-> (login-form {:error    "Invalid username or password"
                                                        :email    (-> req :parameters :form :email)
                                                        :password ""})
                                           (resp/ok))))}}]
     ["/logout" {:get {:name    :login/logout
                       :public  true
                       :handler (fn [_req]
                                  (-> (resp/found "/")
                                      (assoc :session/session nil)
                                      (update :headers assoc "hx-refresh" "true")))}}]
     ["/whodis" {:get {:name    :login/whodis
                       :public  true
                       :htmx    false
                       :handler (fn [req]
                                  (if-let [session (-> req :session/session)]
                                    (resp/ok session)
                                    (resp/unauthorized)))}}]]))
