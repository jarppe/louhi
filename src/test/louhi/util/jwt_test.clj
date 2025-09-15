(ns louhi.util.jwt-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [louhi.util.jwt :as jwt]))


(deftest jwt-test
  (let [claims {:foo/bar 42}
        secret "tiger"]
    (testing "roundtrip"
      (is (match? (merge claims
                         {:iat int?
                          :exp int?
                          :ttl 2})
                  (-> (jwt/make-jwt claims secret 2)
                      (jwt/open-jwt secret)))))
    (testing "wrong secret"
      (is (thrown-match? {:type     :ring.util.http-response/response
                          :response {:status 400}}
                         (-> (jwt/make-jwt claims secret 2)
                             (jwt/open-jwt "donkey")))))
    (testing "bad token"
      (is (thrown-match? {:type     :ring.util.http-response/response
                          :response {:status 400}}
                         (jwt/open-jwt "foo" secret))))))


(deftest ^:slow slow-jwt-test
  (let [claims {:foo/bar 42}
        secret "tiger"]
    (testing "ttl is adjusted"
      (is (match? (merge claims
                         {:iat int?
                          :exp int?
                          :ttl 1})
                  (let [jwt (jwt/make-jwt claims secret 2)]
                    (Thread/sleep 1100)
                    (jwt/open-jwt jwt secret)))))
    (testing "expired jwt"
      (is (nil? (let [jwt (jwt/make-jwt claims secret 2)]
                  (Thread/sleep 2100)
                  (jwt/open-jwt jwt secret)))))))