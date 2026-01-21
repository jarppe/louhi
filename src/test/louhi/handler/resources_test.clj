(ns louhi.handler.resources-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [louhi.handler.resources :as resources]))


(deftest directory-resources-repository-test
  (let [repo (resources/directory-resources-repository "./src/test/louhi/handler/resources" "/public")]
    (is (match? {:body    #(instance? java.io.File %)
                 :headers {"cache-control"    "public, no-cache"
                           "content-encoding" "gzip"
                           "content-type"     "text/plain; charset=utf-8"
                           "etag"             "\"262913f79674f6ad\""
                           "last-modified"    "Wed, 21 Jan 2026 09:34:51 GMT"}}
                (repo "/public/test.txt")))
    (is (match? {:body    #(instance? java.io.File %)
                 :headers {"cache-control"    "public, no-cache"
                           "content-encoding" "identity"
                           "content-type"     "text/plain; charset=utf-8"
                           "etag"             "\"185f8db32271fe25\""
                           "last-modified"    "Wed, 21 Jan 2026 09:46:51 GMT"}}
                (repo "/public/test-2.txt")))
    (is (nil? (repo "/public/foo.txt")))))


(deftest classpath-resources-repository
  (let [repo (resources/classpath-resources-repository "louhi/handler/resources" "/public")]
    (is (match? {:body    #(instance? java.net.URL %)
                 :headers {"cache-control"    "public, no-cache"
                           "content-encoding" "gzip"
                           "content-type"     "text/plain; charset=utf-8"
                           "etag"             "\"262913f79674f6ad\""}}
                (repo "/public/test.txt")))
    (is (match? {:body    #(instance? java.net.URL %)
                 :headers {"cache-control"    "public, no-cache"
                           "content-encoding" "identity"
                           "content-type"     "text/plain; charset=utf-8"
                           "etag"             "\"185f8db32271fe25\""}}
                (repo "/public/test-2.txt")))
    (is (nil? (repo "/public/foo.txt")))))