(ns eponai.web.ui.d3.pie-chart
  (:require
    [cljsjs.d3]
    [eponai.client.ui :refer-macros [opts]]
    [eponai.web.ui.d3 :as d3]
    [goog.string :as gstring]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]
    [taoensso.timbre :refer-macros [debug]]
    [eponai.web.ui.utils :as utils]))

(defn- end-angle [value limit]
  (cond (zero? limit)
        (* 2 js/Math.PI)
        (zero? value)
        0
        :else
        (let [progress (/ value limit)]
          (* 2 js/Math.PI progress))))

(defui PieChart
  Object
  (create [this]
    (let [{:keys [id]} (om/props this)
          svg (d3/build-svg (om/react-ref this (str "pie-" id)))

          {inner-height :height
           inner-width :width} (d3/svg-dimensions svg)

          graph (.. svg
                    (append "g")
                    (attr "class" "pie")
                    (attr "transform" (str "translate(" (/ inner-width 2) "," (/ inner-height 2) ")")))
          arc (.. js/d3
                  arc
                  (innerRadius (- (/ (min inner-width inner-height) 2) 5))
                  (outerRadius (/ (min inner-width inner-height) 2))
                  (startAngle 0))
          txt (.. graph
                  (append "text")
                  (attr "class" "txt")
                  (attr "text-anchor" "middle")
                  (attr "font-size" 14))]
      (.. graph
          (append "path")
          (attr "class" "background")
          (datum #js {:endAngle (* 2 js/Math.PI)})
          (attr "d" arc))

      (d3/update-on-resize this id)
      (om/update-state! this assoc :svg svg :graph graph :arc arc :txt txt)))

  (update [this]
    (let [{:keys [svg arc graph start-angle txt]} (om/get-state this)
          {:keys [value limit title]} (om/props this)

          {inner-width  :width
           inner-height :height} (d3/svg-dimensions svg)

          progress (.. graph
                       (selectAll ".foreground")
                       (data #js [#js {:value value :endAngle (end-angle value limit)}]))

          title-txt (.. txt
                        (selectAll ".title-txt")
                        (data #js [title]))
          val-txt (.. txt
                      (selectAll ".val-txt")
                      (data #js [value]))]

      (.. arc
          (innerRadius (- (/ (min inner-width inner-height) 2) 5))
          (outerRadius (/ (min inner-width inner-height) 2)))

      (.. progress
          enter
          (append "path")
          (attr "class" "foreground")
          (attr "d" #(arc %)))

      (.. progress
          ;transition
          ;(duration 500)
          (attr "d" #(arc %))
          ;(attrTween "d"
          ;           (fn [d]
          ;             (debug "Updating pie got value: " d)
          ;             (let [interpolate (.. js/d3
          ;                                   (interpolate start-angle (end-angle (.-value d) limit)))]
          ;               (fn [t]
          ;                 (set! (.-endAngle d) (interpolate t))
          ;                 (arc d)))))
          )

      (.. title-txt
          enter
          (append "tspan")
          (attr "text-anchor" "middle")
          (attr "class" "title-txt")
          (attr "dy" "-1.5em")
          (attr "x" 0)
          (text title))

      (.. val-txt
          enter
          (append "tspan")
          (attr "text-anchor" "middle")
          (attr "class" "val-txt")
          (attr "dy" "1.5em")
          (attr "x" 0)
          (text (gstring/format "%.0f" value)))

      (.. val-txt
          transition
          (duration 500)
          (text (gstring/format "%.0f" value))
          ;(tween "text" (fn [d]
          ;                (this-as jthis
          ;                  (let [i (.. js/d3
          ;                              (interpolate (.-textContent jthis) d))]
          ;                    (fn [t]
          ;                      (set! (.-textContent jthis) (gstring/format "%.2f" (i t))))))))
          )))

  (componentDidMount [this]
    (.create this))

  (componentDidUpdate [this _ _]
    (.update this))

  (componentWillReceiveProps [this new-props]
    (let [{:keys [value limit]} new-props
          new-start-angle (end-angle value limit)]

      (if (not= new-props (om/props this))
        (om/update-state! this assoc :start-angle new-start-angle))))

  (componentWillUnmount [this]
    (d3/unmount-chart this))

  (initLocalState [_]
    {:start-angle 0})

  (render [this]
    (let [{:keys [id]} (om/props this)]
      (html
        [:div.graph
         (opts {:ref (str "pie-" id)
                :id id})]))))

(def ->PieChart (om/factory PieChart))
