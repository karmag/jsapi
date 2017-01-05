(ns karmag.jsapi.http
  (:import java.util.concurrent.TimeUnit
           org.apache.http.config.SocketConfig
           org.apache.http.impl.bootstrap.ServerBootstrap
           org.apache.http.protocol.HttpRequestHandler))

(defrecord Handler [handler]
  HttpRequestHandler
  (handle [this request response context]
    (handler request response context)))

(defn start
  "Handler is a function that takes [request, response,
  context] arguments."
  [port handler-fn]
  (let [socket-config (.. SocketConfig
                          custom
                          (setSoTimeout 15000)
                          (setTcpNoDelay true)
                          build)
        server (.. ServerBootstrap
                   bootstrap
                   (setListenerPort port)
                   (setSocketConfig socket-config)
                   (registerHandler "*" (Handler. handler-fn))
                   create)]
    (.start server)
    (.awaitTermination server Long/MAX_VALUE TimeUnit/DAYS)))
