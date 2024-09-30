(ns louhi.util.jwt
  (:require [buddy.sign.jwt :as jwt]
            [ring.util.http-response :as resp]))


(def ^:private buddy-opts {:alg :hs512})


(defn make-jwt [claims secret max-age-sec]
  (let [now (java.time.Instant/now)
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
                            (-> (java.time.Instant/now)
                                (.toEpochMilli)
                                (/ 1000)
                                (long)))))
    (catch clojure.lang.ExceptionInfo e
      (let [data  (ex-data e)
            cause (when (-> data :type (= :validation))
                    (-> data :cause))]
        (case cause
          :exp nil
          :signature (resp/bad-request!)
          ; All others are 500:
          (resp/internal-server-error!))))))



(comment
  (-> (make-jwt {:foo/bar 42} "tiger" 2)
      (open-jwt "tiger"))
  ;; => {:foo/bar 42
  ;;     :iat     1714456011
  ;;     :exp     1714456071
  ;;     :ttl     2}

  (let [jwt (make-jwt {:foo/bar 42} "tiger" 2)]
    (Thread/sleep 1000)
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
  ;; => Execution error (ExceptionInfo) at ring.util.http-response/throw! (http_response.clj:11).
  ;;    HTTP 400

  (open-jwt "xxx" "tiger")
  ;; => Execution error (ExceptionInfo) at ring.util.http-response/throw! (http_response.clj:11).
  ;;    HTTP 400

  ;
  )
