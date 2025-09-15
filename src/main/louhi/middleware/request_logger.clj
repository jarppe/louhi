(ns louhi.middleware.request-logger
  (:require [clojure.string :as str]
            [malli.error :as me]
            [louhi.http.query :as query])
  (:import (java.time Duration)))


(set! *warn-on-reflection* true)


(def ^:private
  clojure-name-specials {"QMARK" "?"
                         "BANG"  "!"
                         "PLUS"  "+"
                         "GT"    ">"
                         "LT"    "<"
                         "EQ"    "="
                         "STAR"  "*"
                         "SLASH" "/"
                         "COLON" ":"})


(defn- pretty-print-clj-class ^String [class-name]
  (-> class-name
      (str/replace "$" "/")
      (str/replace #"_([A-Z]{2,5})_" (fn [[_ special]]
                                       (clojure-name-specials special special)))
      (str/replace "_" "-")
      (str/replace #"\/fn?--\d+.*" "[fn]")))


(defn- append-ex-stacktrace-element [^Appendable out ^java.lang.StackTraceElement ste]
  (let [file   (-> ste (.getFileName))
        line   (-> ste (.getLineNumber))
        class  (-> ste (.getClassName))
        method (-> ste (.getMethodName))]
    (.append out (format "%35s" (str (if (= file "NO_SOURCE_FILE")
                                       "?"
                                       file)
                                     ":"
                                     line)))
    (.append out " ")
    (.append out (if (#{"invokeStatic" "invoke"} method)
                   (pretty-print-clj-class class)
                   (str class "." method)))
    (.append out "\n"))
  out)


(defn- append-ex-info [^Appendable out ^Throwable ex nested?]
  (let [st (-> ex (.getStackTrace))]
    (if nested?
      (when (pos? (alength st))
        (append-ex-stacktrace-element out (aget st 0)))
      (doseq [ste st]
        (append-ex-stacktrace-element out ste))))
  out)


(defn- append-ex [^Appendable out ^Throwable ex]
  (when ex
    (let [cause (.getCause ex)]
      (append-ex out cause)
      (append-ex-info out ex (some? cause)))))


(defn wrap-request-logger 
  ([handler] (wrap-request-logger handler nil))
  ([handler {:keys [output request-headers response-headers headers query explain stacktrace htmx]
             :or   {output println}}]
   (fn [req]
     (let [start   (System/nanoTime)]
       (when-let [resp (try
                         (handler req)
                         (catch Exception e
                           e))]
         (let [end     (System/nanoTime)
               e       (when (instance? Exception resp) ^Exception resp)
               buffer  (java.io.StringWriter.)
               message (java.io.PrintWriter. buffer)
               log     (fn [& args]
                         (doseq [arg args]
                           (.append message (str arg))))]
           (log (-> req :request-method (name) (str/upper-case))
                " "
                (-> req :uri (str)))
           (when (and htmx (contains? (:headers req) "hx-request"))
             (log " [htmx")
             (when-let [id (-> req :headers (get "hx-trigger"))]
               (log ":id=\"" id "\""))
             (when-let [name (-> req :headers (get "hx-trigger-name"))]
               (log ":name=\"" name "\""))
             (when-let [prompt (-> req :headers (get "hx-prompt"))]
               (log ":prompt=\"" prompt "\""))
             (log "]"))
           (log " => ")
           (if e
             (log (->  e (.getClass) (.getName))
                  ": \""
                  (->  e (.getMessage) (or ""))
                  "\"")
             (log (-> resp :status (str))))
           (log " (" (-> (- end start) (Duration/ofNanos) (.toMillis)) "ms)")
           (when-let [query (and query
                                 (not (str/blank? (:query-string req)))
                                 (query/parse-query-string (:query-string req)))]
             (log "\n  query:")
             (doseq [[k v] query]
               (log "\n    " k ": " (pr-str v))))
           (when (or headers request-headers)
             (log "\n  request:")
             (doseq [[^String k ^String v] (-> req :headers)]
               (log "\n    " k ": " v)))
           (when (or headers response-headers)
             (log "\n  response:")
             (doseq [[^String k ^String v] (-> resp :headers)]
               (log "\n    " k ": " v)))
           (when (and explain e (instance? clojure.lang.ExceptionInfo e))
             (if (-> e (ex-data) :type (= :reitit.coercion/request-coercion))
               (log "\n  request validation: " (-> e (ex-data) (me/humanize)))
               (log "\n  ex-data: " (pr-str (ex-data e)))))
           (when (and e stacktrace)
             (.append message "\n  stacktrace:\n")
             (append-ex message e))
           (.flush message)
           (output (.toString buffer))
           (when e
             (throw e))
           resp))))))


(comment
  (let [handler (wrap-request-logger (constantly {:status  200
                                                  :headers {"content-type" "text/plain"}})
                                     {:headers    true
                                      :stacktrace true
                                      :htmx       true})]
    (handler {:request-method :get
              :uri            "/hullo"
              :headers        {"accept"     "text/plain"
                               "hx-request" "true"
                               "hx-prompt"  "sure"}}))
  ;; prints
  ; GET /hullo htmx:prompt=[sure] => 200 (0ms)
  ;   request:
  ;     accept: text/plain
  ;     hx-request: true
  ;     hx-prompt: sure
  ;   response:
  ;     content-type: text/plain  
  )