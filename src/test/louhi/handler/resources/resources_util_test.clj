(ns louhi.handler.resources.resources-util-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [louhi.handler.resources.resources-util :as resources-util]))


(deftest resource-content-encoding-and-content-type-test
  (testing "File resources"
    (is (match? {"content-encoding" "identity"
                 "content-type"     "text/plain; charset=utf-8"}
                (-> (io/file "./src/test/louhi/handler/resources/test.txt")
                    (resources-util/resource-content-encoding-and-content-type))))
    (is (match? {"content-encoding" "identity"
                 "content-type"     "text/plain; charset=utf-8"}
                (-> (io/file "./src/test/louhi/handler/resources/test-2.txt")
                    (resources-util/resource-content-encoding-and-content-type)))))
  (testing "Resource resources"
    (is (match? {"content-encoding" "identity"
                 "content-type"     "text/plain; charset=utf-8"}
                (-> (io/resource "louhi/handler/resources/test.txt")
                    (resources-util/resource-content-encoding-and-content-type))))
    (is (match? {"content-encoding" "gzip"
                 "content-type"     "text/plain; charset=utf-8"}
                (-> (io/resource "louhi/handler/resources/test.txt.gz")
                    (resources-util/resource-content-encoding-and-content-type))))))