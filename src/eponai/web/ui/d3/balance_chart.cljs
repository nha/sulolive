(ns eponai.web.ui.d3.balance-chart
  (:require
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [cljs-time.core :as t]
    [cljs-time.coerce :as c]))

(defn create-style-defs [svg]
  (let [defs (.. svg
                 (append "defs"))
        gradient (.. defs
                     (append "linearGradient")
                     (attr "id" "balance-chart-gradient")
                     (attr "x1" "0%")
                     (attr "y1" "0%")
                     (attr "y2" "100%")
                     (attr "x2" "0%"))
        inset (.. defs
                  (append "filter")
                  (attr "id" "inset-shadow"))

        drop-shadow (.. defs
                        (append "filter")
                        (attr "id" "drop-shadow"))]

    (.. gradient
        (append "stop")
        (attr "class" "stop-top")
        (attr "offset" "0%"))
    (.. gradient
        (append "stop")
        (attr "class" "stop-bottom")
        (attr "offset" "100%"))

    (.. inset
        (append "feGaussianBlur")
        (attr "in" "SourceAlpha")
        (attr "stdDeviation" "0,3")
        (attr "result" "inset-blur"))
    (.. inset
        (append "feOffset")
        (attr "in" "inset-blur")
        (attr "dx" 0)
        (attr "dy" 3)
        (attr "result" "inset-offset-blur"))

    (.. inset
        (append "feComposite")
        (attr "operator" "out")
        (attr "in" "SourceGraphic")
        (attr "in2" "inset-offset-blur")
        (attr "result" "inset-inverse"))
    (.. inset
        (append "feFlood")
        (attr "flood-color" "#57BFBF")
        (attr "flood-opacity" 1)
        (attr "result" "inset-color"))
    (.. inset
        (append "feComposite")
        (attr "operator" "in")
        (attr "in" "inset-color")
        (attr "in2" "inset-inverse")
        (attr "result" "inset-shadow"))
    (.. inset
        (append "feComposite")
        (attr "operator" "over")
        (attr "in" "inset-shadow")
        (attr "in2" "SourceGraphic"))

    (.. drop-shadow
        (append "feGaussianBlur")
        (attr "in" "SourceAlpha")
        (attr "stdDeviation" "0,1")
        (attr "result" "line-blur"))
    (.. drop-shadow
        (append "feOffset")
        (attr "in" "line-blur")
        (attr "dx" 0)
        (attr "dy" 2)
        (attr "result" "line-offset-blur"))
    (.. drop-shadow
        (append "feFlood")
        (attr "flood-color" "#C77F2C")
        (attr "flood-opacity" 1)
        (attr "result" "line-color"))
    (.. drop-shadow
        (append "feComposite")
        (attr "operator" "in")
        (attr "in" "line-color")
        (attr "in2" "line-offset-blur")
        (attr "result" "drop-shadow"))
    (.. drop-shadow
        (append "feBlend")
        (attr "in" "SourceGraphic")
        (attr "in2" "drop-shadow")
        (attr "mode" "normal"))))

(defui BalanceChart
  Object
  (make-axis [_ width height]
    (let [x-scale (.. js/d3 scaleTime)

          y-scale (.. js/d3 scaleLinear)]

      {:x-axis  (.. js/d3 (axisBottom x-scale)
                    ;(tickFormat (fn [t]
                    ;              (let [time-format (d3/time-formatter "%b %d")]
                    ;                (time-format (js/Date. t)))))
                    )
       :y-axis  (.. js/d3 (axisLeft y-scale)
                    ;(tickFormat (.. js/d3
                    ;                (format ",.0f")))
                    )
       :x-scale x-scale
       :y-scale y-scale}))

  (create [this]
    (let [{:keys [id]} (om/props this)
          svg (d3/build-svg (om/react-ref this (str "balance-" id)))

          {:keys [margin]} (om/get-state this)
          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})

          {:keys [x-axis y-axis x-scale y-scale]} (.make-axis this inner-width inner-height)

          area (.. js/d3 area
                   (x  #(x-scale (:date %)))
                   (y0 inner-height)
                   (y1 #(y-scale (:balance %))))
          line (.. js/d3 line
                   (x #(x-scale (:date %)))
                   (y #(y-scale (:spent %))))
          graph (.. svg
                    (append "g")
                    (attr "class" "chart")
                    (attr "transform" (str "translate(" (:left margin) "," (:top margin) ")")))]

      (create-style-defs svg)
      (.. graph
          (append "g")
          (attr "class" "x axis grid")
          (attr "transform" (str "translate(0," inner-height ")")))
      (.. graph
          (append "g")
          (attr "class" "y axis grid")
          (attr "transform" (str "translate(-1,0)")))

      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :x-scale x-scale :y-scale y-scale :area area :line line :x-axis x-axis :y-axis y-axis :graph graph)))

  (update [this]
    (let [{:keys [svg x-scale y-scale margin graph area line]} (om/get-state this)
          {:keys [values]} (om/props this)
          js-values (into-array values)

          {inner-width :width
           inner-height :height} (d3/svg-dimensions svg {:margin margin})
          graph-area (.. graph
                         (selectAll ".area")
                         (data #js [js-values]))
          spent-line (.. graph
                         (selectAll ".line")
                         (data #js [js-values]))]
      (.. x-scale
          (range #js [0 inner-width])
          (domain (.. js/d3
                      (extent js-values (fn [d] (:date d))))))
      (.. y-scale
          (range #js [inner-height 0])
          (domain #js [0 (apply max (map :balance values))]))

      (.. graph-area
          enter
          (append "path")
          (attr "class" "area")
          (attr "d" area))

      (.. graph-area
          transition
          (duration 250)
          (attr "d" area))

      (.. graph-area
          exit
          remove)

      (.. spent-line
          enter
          (append "path")
          (attr "class" "line")
          (attr "d" line))

      (.. spent-line
          transition
          (duration 250)
          (attr "d" line))

      (.. spent-line
          exit
          remove)
      (.update-axis this inner-width inner-height)))

  (update-axis [this width height]
    (let [{:keys [x-axis y-axis graph]} (om/get-state this)
          {:keys [values]} (om/props this)]
      (.. y-axis
          (ticks (max (/ height 100) 2)))
      (.. x-axis
          (ticks (min (count values) 31)))
      (.. graph
          (selectAll ".x.axis")
          (attr "transform" (str "translate(0, " height ")"))
          transition
          (duration 250)
          (call x-axis))

      (.. graph
          (selectAll ".y.axis")
          transition
          (duration 250)
          (call y-axis))))

  (componentDidMount [this]
    (.create this))

  (componentDidUpdate [this _ _]
    (.update this))

  (componentWillReceiveProps [this new-props]
    (if (not= new-props (om/props this))
      (.update this)))

  (initLocalState [_]
    {:margin {:top 0 :left 100 :bottom 30 :right 0}})
  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div.graph
         (opts {:ref (str "balance-" id)
                :id id})]))))

(def ->BalanceChart (om/factory BalanceChart))