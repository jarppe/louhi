(ns louhi.http.cache)


(def cache-control  "cache-control")


;;
;; Common cache-control values:
;;
;;   See: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control
;;


(def cache-control-private-no-store  "private, no-store")
(def cache-control-private-no-cache  "private, no-cache")
(def cache-control-public-no-cache   "public, no-cache")

(def cache-control-private-1min      "private, max-age=60,     s-maxage=60,     stale-while-revalidate=10")
(def cache-control-private-10min     "private, max-age=600,    s-maxage=600,    stale-while-revalidate=540")
(def cache-control-private-1h        "private, max-age=3600,   s-maxage=3600,   stale-while-revalidate=3540")
(def cache-control-private-1d        "private, max-age=86400,  s-maxage=86400,  stale-while-revalidate=82800")
(def cache-control-private-7d        "private, max-age=604800, s-maxage=604800, stale-while-revalidate=601200")
(def cache-control-private-immutable "private, max-age=604800, s-maxage=604800, immutable")

(def cache-control-public-1min       "public,  max-age=60,     s-maxage=60,     stale-while-revalidate=10")
(def cache-control-public-10min      "public,  max-age=600,    s-maxage=600,    stale-while-revalidate=540")
(def cache-control-public-1h         "public,  max-age=3600,   s-maxage=3600,   stale-while-revalidate=3540")
(def cache-control-public-1d         "public,  max-age=86400,  s-maxage=86400,  stale-while-revalidate=82800")
(def cache-control-public-7d         "public,  max-age=604800, s-maxage=604800, stale-while-revalidate=601200")
(def cache-control-public-immutable  "public,  max-age=604800, s-maxage=604800, immutable")
