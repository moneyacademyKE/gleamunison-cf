(ns test
  (:require [babashka.process :refer [process destroy-tree]]
            [babashka.http-client :as http]
            [cheshire.core :as json]))

(defn server-up? []
  (try
    (= 200 (:status (http/get "http://localhost:8793/")))
    (catch Exception _
      false)))

(defn wait-for-server [timeout-seconds]
  (println "Waiting for Wrangler dev server to start...")
  (loop [elapsed 0.0]
    (cond
      (server-up?) (do (println "Server is healthy!") true)
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

(defn run-tests []
  (test-endpoint "http://localhost:8793/"
                 {:gleamunison_sc "Self-contained GleamUnison WASM — zero JS imports"})
  
  (test-endpoint "http://localhost:8793/local_var_index?lv=77"
                 {:function "local_var_index" :lv "77" :result 77})
  
  (test-endpoint "http://localhost:8793/range?start=3&end=10"
                 {:function "range" :start "3" :end "10" :result 3})
  
  (test-endpoint "http://localhost:8793/range?start=10&end=3"
                 {:function "range" :start "10" :end "3" :result 0})
  
  (test-endpoint "http://localhost:8793/hash?n=42"
                 {:function "hash" :n "42" :result 704659998})
  
  (test-endpoint "http://localhost:8793/level1"
                 {:function "level1" :result 100})
  
  ;; Let's inspect state_demo behavior and verify persistence/changes
  (println "Testing state_demo persistence...")
  (let [r1 (json/parse-string (:body (http/get "http://localhost:8793/state_demo?val=5")) true)
        r2 (json/parse-string (:body (http/get "http://localhost:8793/state_demo?val=5")) true)]
    (println "  First query result:" r1)
    (println "  Second query result:" r2)
    (assert (= 6 (:result r1)) (str "Expected 6, got " (:result r1)))
    ;; With global instantiation, does state change or accumulate? Let's check.
    ;; Note: The original stub just returns val + 1, or it could write to the KV store.
    ;; Let's log if there is any difference.
    ))

(defn run-tests! []
  (let [w-proc (process "npx wrangler dev --port 8793 --ip 127.0.0.1" {:out :inherit :err :inherit})]
    (try
      (wait-for-server 15)
      (run-tests)
      (println "SUCCESS: All tests completed successfully!")
      (catch Exception e
        (println "ERROR: Tests failed!")
        (.printStackTrace e)
        (System/exit 1))
      (finally
        (println "Stopping Wrangler dev server...")
        (destroy-tree w-proc)))))

(when (= *file* (System/getProperty "babashka.file"))
  (run-tests!))
