(ns louhi.util.security-headers
  (:require [clojure.string :as str]))


; see: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy
;      https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html#content-security-policy-csp
;      https://cheatsheetseries.owasp.org/cheatsheets/Content_Security_Policy_Cheat_Sheet.html
;      https://owasp.org/www-project-secure-headers/


(def security-headers
  (let [self          "'self'"
        none          "'none'"
        unsafe-inline "'unsafe-inline'"
        unsafe-eval   "'unsafe-eval'"]
    {"x-content-type-options"       "nosniff"
     "x-frame-options"              "DENY"
     "referrer-policy"              "strict-origin-when-cross-origin"
     "cross-origin-opener-policy"   "same-origin"
     "cross-origin-resource-policy" "same-site"
     "cross-origin-embedder-policy" "require-corp"
     "content-security-policy"      (->> [["default-src"     [self]]
                                          ["frame-ancestors" [none]]
                                          ["style-src"       [self unsafe-inline "fonts.googleapis.com"]]
                                          ["font-src"        ["fonts.gstatic.com"]]
                                          ["script-src"      [self unsafe-eval]]]
                                         (map (fn [[k v]] (str k " " (str/join " " v))))
                                         (str/join "; "))}))


(def wrap-security-headers
  {:name :louhi/security-headers
   :wrap (fn [handler]
           (fn [req]
             (when-let [resp (handler req)]
               (if (-> resp :headers (get "content-type") (or "") (str/starts-with? "text/html"))
                 (update resp :headers merge security-headers)
                 resp))))})