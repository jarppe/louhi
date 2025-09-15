(ns louhi.http.status
  (:refer-clojure :exclude [partial]))


(def ok                    200)
(def created               201)
(def accepted              202)
(def non-authorative       203)
(def no-content            204)
(def reset                 205)
(def partial               206)

(def multiple-choices      300)
(def moved-permanently     301)
(def moved-temporarily     302)
(def see-other             303)
(def not-modified          304)
(def use-proxy             305)

(def bad-request           400)
(def unauthorized          401)
(def forbidden             403)
(def not-found             404)
(def not-acceptable        406)
(def conflict              409)
(def gone                  410)

(def internal-server-error 500)
(def not-implemented       501)
(def bad-gateway           502)
(def unavailable           503)
(def gateway-timeout       504)
