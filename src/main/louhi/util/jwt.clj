(ns louhi.util.jwt
  "Louhi JWT support.
   Requires dependency to Buddy sign:
     buddy/buddy-sign {:mvn/version \"3.5.351\"}"
  (:require [buddy.sign.jwt :as jwt]
            [louhi.http.resp :as resp])
  (:import (java.time Instant)))


(set! *warn-on-reflection* true)


(def ^:private buddy-opts {:alg :hs512})


(defn make-jwt [claims secret max-age-sec]
  (let [now (Instant/now)
        exp (.plusSeconds now (int max-age-sec))]
    (jwt/sign (assoc claims
                     :iat now
                     :exp exp)
              secret
              buddy-opts)))


(defn open-jwt [jwt secret]
  (try
    (let [claims (jwt/unsign jwt secret buddy-opts)]
      (assoc claims :ttl (- (-> claims :exp)
                            (-> (Instant/now)
                                (.toEpochMilli)
                                (quot 1000)))))
    (catch clojure.lang.ExceptionInfo e
      (let [data  (ex-data e)
            cause (when (-> data :type (= :validation))
                    (-> data :cause))]
        (case cause
          :exp nil
          :signature (throw (resp/error! {:ex-message "request contained JWT token with bad signature"
                                          :status     400}))
          ; All others are 500:
          (resp/error! {:ex-message "unexpected JWT error"
                        :status     500
                        :cause      e}))))))


(comment
  (-> (make-jwt {:foo/bar 42} "tiger" 2)
      (open-jwt "tiger"))
  ;; => {:foo/bar 42
  ;;     :iat     1714456011
  ;;     :exp     1714456071
  ;;     :ttl     2}

  (let [jwt (make-jwt {:foo/bar 42} "tiger" 2)]
    (Thread/sleep 1100)
    (open-jwt jwt "tiger"))
  ;; => {:foo/bar 42
  ;;     :iat     1714456105
  ;;     :exp     1714456107
  ;;     :ttl     1}

  (let [jwt (make-jwt {:foo/bar 42} "tiger" 2)]
    (Thread/sleep 3000)
    (open-jwt jwt "tiger"))
  ;; => nil

  (let [jwt (make-jwt {:foo/bar 42} "tiger" 2)]
    (open-jwt jwt "donkey"))
  ; Execution error (ExceptionInfo) at louhi.util.resp/error! (resp.clj:10).
  ; request contained JWT token with bad signature 
  )
