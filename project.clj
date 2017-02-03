(defproject budget "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
                 [amazonica "0.3.85"]
                 [org.clojars.petterik/om "1.0.0-alpha48-SNAPSHOT-3"]
                 ;;[org.omcljs/om "1.0.0-alpha46"]
                 [clj-http "3.3.0"]
                 ;[clj-time "0.11.0"]
                 [compojure "1.5.1"]
                 [aprint "0.1.3"]
                 [buddy/buddy-auth "1.3.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.datomic/datomic-pro "0.9.5544"
                  :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.10.56"
                  :exclusions [joda-time org.clojure/test.check]]
                 [com.draines/postal "2.0.1"]
                 [com.stripe/stripe-java "3.2.0"]
                 [com.taoensso/timbre "4.7.4"]
                 [environ "1.0.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/data.generators "0.1.2"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/core.memoize "0.5.8"]         ; needed to work around lein+core.async dependency issue.
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.namespace "0.2.11"]
                 ;; Depending on transit-clj for faster cljs builds
                 [com.cognitect/transit-clj "0.8.288"
                  :exlusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.fasterxml.jackson.core/jackson-core "2.5.3"]
                 ; ring helpers
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-devel "1.5.0"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [ring-transit "0.1.6"]
                 [prone "1.1.4"]
                 [medley "0.8.3"]
                 [org.martinklepsch/s3-beam "0.6.0-alpha1"]

                 ;; CLJS
                 [cljsjs/react "15.3.1-0"]
                 [cljsjs/react-dom "15.3.1-0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [org.clojure/clojurescript "1.9.293"
                  ;;  :classifier "aot"
                  :exclusion [org.clojure/data.json]
                  ]
                 [com.google.guava/guava "19.0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljs-http "0.1.39"]
                 [org.clojure/tools.reader "1.0.0-alpha2"]
                 [garden "1.3.2"]
                 [datascript "0.15.4"]
                 [cljsjs/stripe "2.0-0"]
                 [cljsjs/nvd3 "1.8.2-1"]
                 [bidi "2.0.10"]
                 [kibu/pushy "0.3.6"]
                 [binaryage/devtools "0.8.1"]
                 [org.clojure/tools.nrepl "0.2.11"
                  :exclusions [org.clojure/clojure]]

                 ;; React-native
                 [natal-shell "0.3.0"]

                 ;; Testing
                 [lein-doo "0.1.6"]
                 [devcards "0.2.1-6"]
                 [org.clojure/test.check "0.9.0"]]
  :exclusions [[org.clojure/test.check]]

  :jvm-opts ^:replace ["-Xms512m" "-Xmx2048m" "-server"
                       "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"
                      ]
  :resource-paths ["resources" "node_modules" "bower_components"]
  :plugins [
           ;; [lein-npm "0.6.1"]
            [lein-shell "0.5.0"]
            [lein-doo "0.1.6"]
            [lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.7"]
            [lein-ring "0.9.7"]
            [lein-test-out "0.3.1"]
            [lein-environ "1.0.1"]]
  
  :min-lein-version "2.0.0"
  :clean-targets ^{:protect false} ["resources/public/dev/js/out"
                                    "resources/public/devcards/js/out"
                                    "resources/public/release/js/out"
                                    "resources/public/doo-test/js/out"
                                    "resources/public/test/js/out"
                                    "target/"
                                  ;;  "index.ios.js"
                                  ;;  "index.android.js"
                                   ]

  :aliases {"all-deps"               ^{:doc "Fetches both clj, cljs and node dependencies."}
                                     ["do"
                                      ["deps"]
                                      ["pod-deps"]
                                      ["npm-deps"]
                                      ["css"]]
            "npm-deps"               ["do"
                                       ["shell" "npm" "install"]
                                       ["shell" "bower" "install"]]
            "pod-deps"               ["shell" "pod" "install" "--project-directory=./ios"]
            "css"                    ["shell" "./scripts/compile-css.sh"]
            "css-watch"              ["shell" "./scripts/compile-css.sh" "watch"]
            "prod-build-ios-local"   ^{:doc "Recompile mobile code with production profile.
                                           The build runs against a local/laptop server."}
                                     ["do"
                                      ["with-profile" "mob-prod" "cljsbuild" "once" "ios-local"]]
            "prod-build-ios-release" ^{:doc "Recompile mobile code with production profile.
                                           The build runs against production servers."}
                                     ["do"
                                      ["with-profile" "mob-prod" "cljsbuild" "once" "ios-release"]]
            "prod-build-web"         ^{:doc "Recompile web code with release build."}
                                     ["do"
                                      ["with-profile" "web-prod" "cljsbuild" "once" "release"]]
            "prod-build-server"      ^{:doc "Recompile server code with release build."}
                                     ["do" "uberjar"]
            "dev-build-ios"          ^{:doc "Compile mobile code in development mode."}
                                     ["do"
                                      ["with-profile" "mobile" "cljsbuild" "once" "ios"]]
            "dev-build-web"          ^{:doc "Compile web code in development mode."}
                                     ["do"
                                      ["with-profile" "web" "cljsbuild" "once" "dev"]]
            "dev-build-web-auto"     ^{:doc "Compile web code in development mode continously."}
                                     ["do"
                                      ["with-profile" "web" "cljsbuild" "auto" "dev"]]
            "run-tests-web"          ^{:doc "Compile and run web tests"}
                                     ["do"
                                      ["with-profile" "web" "cljsbuild" "once" "doo-test"]
                                      ["with-profile" "web" "doo" "phantom" "doo-test" "once"]]
            "figwheel-ios"           ^{:doc "Start figwheel for ios"}
                                     ["do"
                                      ;; Exporting an environment variable for figwheel port
                                      ;; as it's only configurable globally (per project.clj file).
                                      ;; This port should differ from the one running figwheel-web,
                                      ;; because they need to be running lein with different profiles
                                      ;; and different dependencies.
                                      ["shell" "bash" "-c"
                                       "export FIGWHEEL_PORT=3450; lein with-profile mobile figwheel ios"]
                                      ["with-profile" "mobile" "figwheel" "ios"]]
            "figwheel-web"           ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "web" "figwheel" "dev"]
                                      ]
            "figwheel-test"          ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "web" "figwheel" "test"]]
            "figwheel-web+test"      ^{:doc "Start figwheel for web"}
                                     ["do"
                                      ["with-profile" "web" "figwheel" "dev" "test"]]
            }

  ;; TODO: TEST ALL ALIASES

  ;;;;;;;;;;;;;
  ;; AWS+Docker deploy:
  ;;;;;;;;;;;;;
  :uberjar-name "budget-0.1.0-SNAPSHOT-standalone.jar"

  :figwheel {:css-dirs    ["resources/public/assets/css/"]
             :server-port ~(read-string (or (System/getenv "FIGWHEEL_PORT") "3449"))
             :reload-clj-files {:clj true :cljc true}
 }

  :profiles {:uberjar  {:jvm-opts   ^:replace ["-Dclojure.compiler.direct-linking=true"
                                               "-Xmx1g" "-server"]
                        :aot        :all
                        :resource-paths ^:replace ["resources"]
                        :prep-tasks ["compile" "prod-build-web" "css"]}

             :mobile   {:dependencies 
                                      [[org.clojars.petterik/om "1.0.0-alpha48-SNAPSHOT"
                                        :exclusions [cljsjs/react cljsjs/react-dom]]
                                    ;; [[org.omcljs/om "1.0.0-alpha46"
                                    ;;   :exclusions [cljsjs/react cljsjs/react-dom]]
                                       [figwheel-sidecar "0.5.7"]
                                       [com.cemerick/piggieback "0.2.1"]]
                        :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                        :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                        :cljsbuild    {:builds [{:id           "ios"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                                                 :figwheel     true
                                                 :compiler     {:output-to     "target/ios/not-used.js"
                                                                :main          "env.ios.main"
                                                                :output-dir    "target/ios"
                                                                :optimizations :none}}
                                                {:id           "android"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/dev"]
                                                 :figwheel     true
                                                 :compiler     {:output-to     "target/android/not-used.js"
                                                                :main          "env.android.main"
                                                                :output-dir    "target/android"
                                                                :optimizations :none}}]}}

             :mob-prod {:dependencies 
                                      [[org.clojars.petterik/om "1.0.0-alpha48-SNAPSHOT"
                                        :exclusions [cljsjs/react cljsjs/react-dom]]]
                                    ;; [[org.omcljs/om "1.0.0-alpha46"
                                    ;;   :exclusions [cljsjs/react cljsjs/react-dom]]]
                        :cljsbuild    {:builds [{:id           "ios-release"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                                 :compiler     {:output-to     "index.ios.js"
                                                                :main          "env.ios.main"
                                                                :output-dir    "target/ios"
                                                                :optimizations :simple}}
                                                {:id           "ios-local"
                                                 ;; A production build, run against a local/laptop
                                                 ;; jourmoney server.
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                                 :compiler     {:output-to     "index.ios.js"
                                                                :main          "env.ios.local-main"
                                                                :output-dir    "target/ios-local"
                                                                :optimizations :simple}}
                                                {:id           "android"
                                                 :source-paths ["src" "src-hacks/react-native" "env/client/prod"]
                                                 :compiler     {:output-to     "index.android.js"
                                                                :main          "env.android.main"
                                                                :output-dir    "target/android"
                                                                :optimizations :simple}}]}}

             :web-prod {:cljsbuild {:builds [{:id           "release"
                                              :source-paths ["src/" "src-hacks/web/" "env/client/prod"]
                                              :compiler     {:main          "env.web.main"
                                                             :asset-path    "/release/js/out"
                                                             :output-to     "resources/public/release/js/out/budget.js"
                                                             :output-dir    "resources/public/release/js/out/"
                                                             :optimizations :advanced
                                                             :externs ["src-hacks/js/externs/stripe-checkout.js"
                                                                       "src-hacks/js/externs/red5pro.js"]
                                                             :language-in :ecmascript5
                                                             ;;   :parallel-build true
                                                             ;;   :pseudo-names true
                                                             ;;   :pretty-print true
                                                             ;;   :verbose true
                                                             }}]}}
             :web      {:dependencies [[figwheel-sidecar "0.5.7"]]
                        :cljsbuild {:builds [{:id           "dev"
                                              :figwheel     {:on-jsload "eponai.web.figwheel/reload!"}
                                              :source-paths ["src/" "src-hacks/web/" "env/client/dev"]
                                              :compiler     {:main          "env.web.main"
                                                             :asset-path    "/dev/js/out"
                                                             :output-to     "resources/public/dev/js/out/budget.js"
                                                             :output-dir    "resources/public/dev/js/out/"
                                                             :optimizations :none
                                                             :parallel-build true
                                                             :source-map    true}}
                                             {:id           "devcards"
                                              :source-paths ["src/" "src-hacks/web/" "test/"]
                                              :figwheel     {:devcards    true  ;; <- note this
                                                             }
                                              :compiler     {:main                 "eponai.devcards.devcards_main"
                                                             :asset-path           "/devcards/js/out"
                                                             :output-to            "resources/public/devcards/js/out/budget.js"
                                                             :output-dir           "resources/public/devcards/js/out"
                                                             :source-map-timestamp true}}
                                             {:id           "test"
                                              :source-paths ["src/" "src-hacks/web/" "test/"]
                                              :figwheel     {:on-jsload     "eponai.client.figwheel.test-main/reload-figwheel!"}
                                              :compiler     {:output-to     "resources/public/test/js/out/budget.js"
                                                             :output-dir    "resources/public/test/js/out"
                                                             :asset-path    "/test/js/out"
                                                             :main          "eponai.client.figwheel.test-main"
                                                             :optimizations :none
                                                             :source-map    true
                                                             }}
                                             {:id           "doo-test"
                                              :source-paths ["src/" "src-hacks/web/" "test/"]
                                              :compiler     {:output-to     "resources/public/doo-test/js/out/budget.js"
                                                             :output-dir    "resources/public/doo-test/js/out"
                                                             :main          "eponai.client.tests"
                                                             :optimizations :none
                                                             :source-map    true
                                                             }}]}}}

  ;;;;;;;;;;;;;
  ;; clj:
  ;;;;;;;;;;;;;
  :target-path "target/%s"
  :source-paths ["src"]
  :test-paths ["test" "env/server/dev"]
  :ring {:handler eponai.server.core/app
         :init    eponai.server.core/init}
  :main eponai.server.core
  :repl-options {:init-ns eponai.repl
                 :init (eponai.repl/init)}
  :repositories {"my.datomic.com" {:url      "https://my.datomic.com/repo"
                                   :username ~(System/getenv "DATOMIC_EMAIL")
                                   :password ~(System/getenv "DATOMIC_KEY")}}

  ;;;;;;;;;;;;;
  ;; cljs:
  ;;;;;;;;;;;;;
  :doo {:paths {:phantom "./node_modules/phantomjs/bin/phantomjs"}}
  :test-commands {"frontend-unit-tests"
                    ["node_modules/karma/bin/karma" "start" "karma.conf.js" "--single-run"]}
)
