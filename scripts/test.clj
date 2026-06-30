(ns test
  (:require [babashka.process :refer [process destroy-tree]]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

(defn find-free-port []
  (let [socket (java.net.ServerSocket. 0)
        port (.getLocalPort socket)]
    (.close socket)
    port))

(defn server-up? [port]
  (try
    (= 200 (:status (http/get (str "http://localhost:" port "/"))))
    (catch Exception _
      false)))

(defn wait-for-server [port timeout-seconds]
  (println "Waiting for Wrangler dev server to start on port" port "...")
  (loop [elapsed 0.0]
    (cond
      (server-up? port) (do (println "Server is healthy!") true)
      (>= elapsed timeout-seconds) (throw (Exception. "Server did not start in time"))
      :else (do
              (Thread/sleep 500)
              (recur (+ elapsed 0.5))))))

(defn test-endpoint [url expected-map]
  (println "Testing:" url)
  (let [resp (http/get url)
        status (:status resp)
        body (json/parse-string (:body resp) true)]
    (println "  Status:" status)
    (println "  Body:" body)
    (assert (= 200 status) (str "Expected status 200, got " status))
    (doseq [[k v] expected-map]
      (assert (= v (get body k))
              (str "Expected key " k " to be " v ", got " (get body k))))))

(defn test-variant [base-url prefix variant-name]
  (println "\n--- Testing" variant-name "variant (" prefix ") ---")

  (test-endpoint (str base-url prefix "/local_var_index?lv=77")
                 {:function "local_var_index" :lv "77" :result 77 :variant variant-name})

  (test-endpoint (str base-url prefix "/range?start=3&end=10")
                 {:function "range" :start "3" :end "10" :result 3 :variant variant-name})

  (test-endpoint (str base-url prefix "/range?start=10&end=3")
                 {:function "range" :start "10" :end "3" :result 0 :variant variant-name})

  (test-endpoint (str base-url prefix "/hash?n=42")
                 {:function "hash" :n "42" :result 704659998 :variant variant-name})

  (test-endpoint (str base-url prefix "/level1")
                 {:function "level1" :result 100 :variant variant-name})

  (println "Testing" variant-name "state_demo persistence...")
  (let [r1 (json/parse-string (:body (http/get (str base-url prefix "/state_demo?val=5"))) true)
        r2 (json/parse-string (:body (http/get (str base-url prefix "/state_demo?val=5"))) true)]
    (println "  First query result:" r1)
    (println "  Second query result:" r2)
    (assert (= 6 (:result r1)) (str "Expected 6, got " (:result r1)))
    (assert (= variant-name (:variant r1)) (str "Expected variant " variant-name ", got " (:variant r1)))))

(defn run-tests [port]
  (let [base-url (str "http://localhost:" port)]
    (test-endpoint (str base-url "/")
                   {:gleamunison_sc "Self-contained GleamUnison WASM — zero JS imports"
                    :gleamunison_cf "Cloudflare-bound GleamUnison WASM — 12 JS FFI imports"})

    (test-variant base-url "/sc" "sc")
    (test-variant base-url "/cf" "cf")

    ;; Legacy unprefixed endpoints should still work (route to sc)
    (println "\n--- Testing legacy unprefixed endpoints ---")
    (test-endpoint (str base-url "/local_var_index?lv=77")
                   {:function "local_var_index" :lv "77" :result 77 :variant "sc"})
    (test-endpoint (str base-url "/hash?n=42")
                   {:function "hash" :n "42" :result 704659998 :variant "sc"})
    (test-endpoint (str base-url "/level1")
                   {:function "level1" :result 100 :variant "sc"})))

(defn run-tests! []
  (let [port (find-free-port)
        w-proc (process (str "npx wrangler dev --port " port " --ip 127.0.0.1") {:out :inherit :err :inherit})]
    (try
      (wait-for-server port 15)
      (run-tests port)
      (println "\nSUCCESS: All tests completed successfully on port" port "!")
      (catch Exception e
        (println "ERROR: Tests failed!")
        (.printStackTrace e)
        (System/exit 1))
      (finally
        (println "Stopping Wrangler dev server...")
        (destroy-tree w-proc)))))

(when (= *file* (System/getProperty "babashka.file"))
  (run-tests!))
